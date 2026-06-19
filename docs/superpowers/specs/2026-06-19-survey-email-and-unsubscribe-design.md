# 子專案 C（第一段）：退訂閉環 + 寄信基礎建設 Design

**日期**：2026-06-19
**目標**：在既有 `survey-backend`（Spring Boot 3.5 / Java 21 + PostgreSQL）上，補齊半成品退訂功能成防偽閉環，並建立可實際寄信的基礎建設；以「問卷送出後自動寄歡迎信」作為驗證整條管線的第一條寄信路徑。

**所屬計畫**：land-page 行銷功能三部曲 A（廣告埋碼，已完成）→ B（問卷 + 後端儲存，已完成）→ **C（電子報/寄信）**。本文件是 C 的**第一段**，只做「退訂 + 寄信基礎建設」，**不含批量電子報撰寫/發送 UI**（留給 C 的後續段落）。

設計來源／前置：`docs/superpowers/specs/2026-06-19-survey-backend-design.md`（B）。

---

## 範圍

**包含**：
1. 退訂閉環：補上 `unsubscribeByEmail` 資料層方法，並把現有明碼退訂端點升級為 HMAC 簽章防偽。
2. 寄信基礎建設：可插拔的 `MailSender` 介面 + ZSend 實作 + 無金鑰時的 no-op 實作；寄送記錄表 `email_log`；名單去重查詢（為未來批量發送鋪路）。
3. 第一條寄信路徑：問卷送出（201）後自動寄一封歡迎/確認信（交易型、單收件人、內含退訂連結）。

**不包含（留給 C 後續）**：
- 批量電子報/廣告信的撰寫與發送 UI。
- 寄送排程（`/emails/schedule`）、批量端點（`/emails/batch`）。
- ZSend webhook（delivery/bounce/open/click）接收與投遞狀態回寫。
- 退訂偏好分類（只做全域退訂這一個布林）。

---

## 決策（已與開發者確認）

| 主題 | 決策 |
|---|---|
| 範圍 | 退訂閉環 + 寄信基礎建設（不做批量發送 UI） |
| 寄信管道 | Zeabur Email（ZSend）REST API |
| 寄件人網域 | `springai.world`（ZSend 已驗證：DKIM/MX/SPF verified；DMARC pending 不影響寄送） |
| 寄件人地址 | `AI 全端課程 <noreply@springai.world>` |
| 第一條寄信路徑 | 問卷送出後自動寄歡迎/確認信 |
| 問卷→寄信耦合（取捨 A） | **A1 同步 try/catch**：`save` 成功後同請求內寄信，失敗只記 log 不拋例外、不影響 201 |
| 退訂防偽（取捨 B） | **B2 HMAC 簽章 token**：`?email=...&t=HMAC_SHA256(email, 秘鑰)`，無狀態、不需新欄位 |
| API key | 已存在 `.env` 的 `SEND_MAIL_API=zs_...`（綁定 `springai.world`，已 gitignore） |

---

## 架構

```
問卷送出 (survey.html → POST /api/survey)
        │ 201 後（蜜罐 204 不寄）
        ▼
SurveyController ──► WelcomeMailService ──► MailSender (介面)
                                               ├─ ZSendMailSender   → POST api.zeabur.com/.../zsend/emails
                                               └─ NoopMailSender    → 僅記 log（SEND_MAIL_API 空白時啟用）
                                            └─► email_log 寫一筆寄送記錄

退訂連結 (信件內) ──► GET /api/survey/unsubscribe?email=&t=
                          │ 驗 HMAC token
                          ▼
                     SurveyResponseRepository.unsubscribeByEmail()  (UPDATE unsubscribed=true)
```

ZSend 送信為 REST：`POST https://api.zeabur.com/api/v1/zsend/emails`，標頭 `Authorization: Bearer ${SEND_MAIL_API}`，body `{from, to:[...], subject, html, text?}`，成功回 `{id, status:"pending"}`（`id` 存入 `email_log.provider_message_id`）。單封收件人（to+cc+bcc）上限 50。

---

## 元件設計

### 1. 退訂閉環

**Repository 方法**（`SurveyResponseRepository`）：
```java
/** 將指定 email（大小寫不敏感）標記為已退訂；回傳受影響筆數 */
@Modifying
@Query("update SurveyResponse s set s.unsubscribed = true where lower(s.email) = lower(:email)")
int unsubscribeByEmail(@Param("email") String email);
```
- 需在呼叫端（service 或 controller 方法）有交易；以 `@Transactional` 標註退訂處理。

**退訂端點**（沿用半成品 `GET /api/survey/unsubscribe`，升級為 B2）：
- 參數 `email` 與 `t`（HMAC token）。
- 驗證：`t == HMAC_SHA256(email.trim().toLowerCase(), UNSUBSCRIBE_SECRET)`（Base64 URL-safe、無 padding）。
- token 通過 → `unsubscribeByEmail(email)`；token 錯/缺 → 不執行退訂。
- **不論結果一律回同一個固定 HTML 成功頁**（不洩漏 email 是否在名單、防 XSS）——此非揭露行為半成品已正確，保留。
- 冪等：已退訂者再點、名單查無此 email，都回相同成功頁、不報錯。

**HMAC 工具**：新增 `UnsubscribeTokenService`（或工具類），提供 `sign(email)` 與 `verify(email, token)`，秘鑰由 `UNSUBSCRIBE_SECRET` 注入。

### 2. 寄信基礎建設

**`MailSender` 介面**：
```java
/** 寄送單封信；回傳 provider 訊息 id（失敗回 null 或拋例外，由呼叫端決定處理） */
public interface MailSender {
    String send(String to, String subject, String html);
}
```

**`ZSendMailSender`**（`@ConditionalOnProperty` 或以金鑰非空白判斷啟用）：
- 用 Spring `RestClient`（Boot 3.2+ 內建，免新依賴）POST 到 ZSend。
- 從設定讀 `SEND_MAIL_API`（Bearer）、`MAIL_FROM`、`MAIL_REPLY_TO`（選填）。
- 解析回應取 `id` 回傳。

**`NoopMailSender`**（`SEND_MAIL_API` 空白時啟用）：
- 僅 `log.info` 記下「（未設金鑰）略過寄信 to=... subject=...」，回傳固定假 id（如 `"noop"`）。
- 用途：本機/測試不打外部 API（沿用知識庫「無 key 走 fallback」模式）。

**寄送記錄表 `email_log`**（Flyway `V3__create_email_log.sql`）：
| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGSERIAL PK | |
| recipient | TEXT NOT NULL | 收件人 email |
| subject | TEXT | 主旨 |
| type | TEXT | 信件類型（本段固定 `welcome`） |
| provider_message_id | TEXT | ZSend 回傳 id（no-op 時為 `noop`） |
| status | TEXT NOT NULL | `sent` / `failed` |
| error | TEXT | 失敗時的錯誤摘要 |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

對應 `EmailLog` Entity + `EmailLogRepository`。

**名單去重查詢**（`SurveyResponseRepository`，為未來批量發送鋪路）：
```java
/** 可寄送名單：同意且未退訂的去重 email（依 email 去重） */
@Query("select distinct lower(s.email) from SurveyResponse s where s.consent = true and s.unsubscribed = false")
List<String> findDistinctRecipients();
```

### 3. 問卷送出自動寄歡迎信

**`WelcomeMailService`**：
- `sendWelcome(SurveyResponse saved)`：組歡迎信 HTML（含 B2 退訂連結 `${APP_PUBLIC_BASE_URL}/api/survey/unsubscribe?email=<urlencoded>&t=<token>`）→ `mailSender.send(...)` → 寫 `email_log`（成功 `sent` 存 id；例外 `failed` 存 error）。
- 整個方法 try/catch，**任何例外都吞掉只記 log**，不向上拋。

**`SurveyController.submit()` 整合**：
- 蜜罐有值（回 204）的路徑**不寄信**。
- `repository.save(entity)` 成功後呼叫 `welcomeMailService.sendWelcome(entity)`（A1 同步），再回 201。
- 寄信不在問卷寫入的交易內、且失敗不影響 201（呼應知識庫「衍生動作不可拖垮主交易」）。
- 已知限制：email 無 unique，重複填寫會重複寄歡迎信——本段可接受，`email_log` 可事後查。

---

## 設定與環境變數

| 變數 | 用途 | 本機預設 |
|---|---|---|
| `SEND_MAIL_API` | ZSend Bearer 金鑰（已存在 `.env`，已 gitignore） | 空白 → 啟用 NoopMailSender |
| `MAIL_FROM` | 寄件人 | `AI 全端課程 <noreply@springai.world>` |
| `MAIL_REPLY_TO` | 回信地址（選填） | （空） |
| `UNSUBSCRIBE_SECRET` | HMAC 退訂 token 秘鑰 | `dev-unsub-secret`（線上必換強隨機） |
| `APP_PUBLIC_BASE_URL` | 組退訂連結用（= survey-backend 對外網址） | `http://127.0.0.1:8080` |

- 機密一律環境變數讀取，不寫死（沿用 B 慣例）。
- 線上（Zeabur）以 `zeabur-variables` 設定上述變數。

---

## 安全與合規

- 退訂 token 防偽（B2），避免任意退訂他人 / email 列舉。
- 退訂端點不洩漏名單（固定回應）、回應頁不回顯使用者輸入（防 XSS）。
- `SEND_MAIL_API` 已 gitignore；不得進版控、不得寫死。
- 歡迎信內含可用退訂連結，符合行銷信 PDPA / 可退訂要求。

---

## 測試

以 `@WebMvcTest` + `@MockBean`（Spring Boot 3.5，不需 4.x 的 `@MockitoBean`）：

1. **退訂端點**：
   - 合法 token → 呼叫 `unsubscribeByEmail` 一次 + 回 200 HTML。
   - token 錯誤/缺少 → **不**呼叫 `unsubscribeByEmail`，仍回 200 同頁。
2. **MailSender 選擇**：`SEND_MAIL_API` 空白 → 注入的是 `NoopMailSender`（或以條件 bean 測試驗證）。
3. **ZSendMailSender 請求格式**：用 `MockRestServiceServer` 驗 POST URL、Bearer 標頭、body 含 from/to/subject/html，**不真打 ZSend**。
4. **問卷送出觸發寄信**：
   - 合法問卷（201）→ `welcomeMailService`（或 `mailSender`）被呼叫一次。
   - 蜜罐（204）→ **不**呼叫寄信。
5. 既有 B 的測試（驗證/蜜罐/admin 金鑰/CSV）需維持綠燈。

整合層（手動，需金鑰）：對線上 survey-backend 送一筆問卷，確認收到歡迎信且信中退訂連結點擊後該 email `unsubscribed=true`。

---

## 非目標（YAGNI）

- 不做批量電子報撰寫/發送 UI、不接 webhook、不做寄送排程。
- 不做退訂主題分類（只有單一全域 `unsubscribed` 布林）。
- 不做歡迎信去重 / 寄送頻率限制（重複填寫重複寄，本段可接受）。
- 不改 B 既有的問卷欄位與統計。

---

## 既有狀態盤點（實作起點）

- `survey-backend` 工作區有**未提交**變更：`SurveyController.java` 已含半成品退訂端點（明碼版，呼叫尚不存在的 `unsubscribeByEmail`，目前編譯不過）。本設計以此為起點：補方法 + 升級為 HMAC。
- `springai.world` 於 ZSend **已驗證**，寄信管線已用測試信實測通過（`{id,status:pending}`），故原「前置 DNS 驗證」步驟**不需再做**。
- Spring Boot **3.5.0**、目前**無 mail 依賴**（改用內建 `RestClient`，不加 `spring-boot-starter-mail`）。
