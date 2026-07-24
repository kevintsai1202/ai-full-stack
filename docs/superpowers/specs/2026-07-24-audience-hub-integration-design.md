# 受眾名單中心整合設計（測驗／電子報／宣傳／廣告／線上課）

**日期**：2026-07-24
**目標**：以 survey-backend 為「名單中心」（受眾＋同意＋寄信的唯一真相），整合測驗系統、宣傳、未來廣告與線上課系統的受眾資料流；避免多套寄信系統與多份同意狀態並存。
**狀態**：Phase 0 已完成；Phase 1+ 依觸發訊號啟動（見「啟動時機」）。

---

## 1. 背景與現況

| 系統 | Zeabur 專案 | 現況 |
|---|---|---|
| survey-backend | `hahow-ai-full-stack`（6a3483c107afd8c0435e56c0） | 問卷收集、campaign 寄送、HMAC 確認/退訂閉環、email_log、source 來源標記、admin 後台 |
| exam 系統 | `examsystem-aste`（69c16b89e11ead6d2def2a6c） | 線上測驗；`student_profile` 259 筆；內建但**閒置**的 email_campaign/email_recipient/email_template 表（0 筆）與 consent 欄位（全 NULL） |
| 宣傳頁 | land-page 等 | UTM 參數已可寫入 survey_response.utm |
| 廣告 | 未來 | 無 |
| 線上課系統 | 未來 | 無 |

**已完成（Phase 0，2026-07-24）**：
- `survey_response.source` 欄位（V5 migration）：`survey_form` / `exam`
- `POST /api/admin/import`：外部名單匯入為待確認（consent=false）
- `POST /api/admin/campaign/invite`：二次確認邀請信（limit 分批＋email_log 冪等跳過）
- `GET /api/survey/confirm`：HMAC 確認訂閱閉環
- exam 名單 254 筆已匯入，每日 50 封邀請排程執行中（claude.ai routine `trig_01UcKL8HK18yMiKNWtVowC6Y`）

## 2. 核心原則

1. **整合受眾，不整合系統**：每個系統管自己的領域；「這個人是誰、同意了什麼、從哪來」只在名單中心有一份真相。
2. **一律走 API，不共用資料庫**：跨 Zeabur 專案的邊界保留；任一系統重寫不影響另一邊。
3. **同意狀態只有一份**：exam 系統閒置的 email/consent 表**正式棄用**，不得啟用第二套寄信。
4. **email 為跨系統關聯鍵**（小寫正規化）；未來身分登入沿用 Google OAuth 模式。

## 3. 目標架構

```
                      ┌──────────────────────────────┐
   資料來源（誰進來）   │   survey-backend = 名單中心    │   消費者（誰用名單）
                      │  · email / source / consent  │
  測驗系統 ────匯入────▶│  · tags（Phase 2）           │────▶ 電子報 campaign
  問卷表單 ────寫入────▶│  · 確認/退訂 HMAC 閉環         │────▶ 課程優惠通知
  宣傳頁 UTM ──寫入────▶│  · email_log 寄送軌跡         │────▶ 廣告受眾匯出（Phase 4）
  線上課(未來)─事件回寫─▶│  · utm 歸因                  │
                      └──────────────────────────────┘
```

## 4. 分階段規劃

### Phase 1：exam → 名單中心自動同步
- **啟動時機**：第二次需要手動匯入 exam 名單時。
- 做法（擇一）：
  - A（簡單，建議先做）：每日排程（cron/routine）呼叫既有 `/api/admin/import`，抓 exam DB `student_profile` 增量（以 `created_at` > 上次同步時間）。
  - B（即時）：exam-system-backend 在學生建立時 webhook 呼叫 import API。
- 匯入者一律 `source=exam`、`consent=false`，走既有二次確認流程。
- 估工：半天。

### Phase 2：tags 分眾與 UTM 歸因
- **啟動時機**：想寄「只給某批人」的信但篩不出來時；或開始跑多檔宣傳。
- `survey_response` 加 `tags jsonb`（如 `["exam-2026","course-basic","confirmed"]`）。
- import/confirm 時自動打 tag；campaign send 的 Filter 擴充 tag 條件。
- 宣傳/廣告連結統一帶 UTM（欄位已存在），admin 後台 UTM 歸因圖表已可用。
- 估工：1 天。

### Phase 3：線上課系統整合（開發第一天就納規格）
- 課程系統獨立開發（獨立 Zeabur 專案、獨立 DB），登入用 Google OAuth，email 為關聯鍵。
- **事件回寫**：購課、完課、單元進度等事件 → 呼叫名單中心 API 打 tag（如 `purchased-fullstack`、`completed-unit3`）。
- 電子報即可分眾寄送（例：買過基礎課未買進階、卡在某單元的人）。
- 名單中心只管行銷受眾；學習資料留在課程系統。

### Phase 4：廣告（不自建系統）
- 從名單中心匯出「已同意」受眾 → Meta/Google Custom Audience / Lookalike。
- 廣告落地頁＝問卷/宣傳頁，UTM 歸因自動進名單中心。

## 5. 啟動時機訊號（出現任一才動工，避免過度開發）

1. 第二次手動匯入 exam 名單 → Phase 1
2. 想寄分眾信但篩不出對象 → Phase 2
3. 退訂者從另一系統收到信 → 同意狀態失守，**最高優先**修正
4. 線上課系統立案 → Phase 3 規格內建整合

## 6. 非目標（明確不做）

- 不合併 survey-backend 與 exam 系統的程式碼或資料庫
- 不啟用 exam 系統內建的 email_campaign/email_recipient/email_template
- 不自建廣告投放/追蹤系統
- Phase 1 前不做任何自動同步（現階段手動批次已足夠）

## 7. 參考

- 前置設計：`docs/superpowers/specs/2026-06-19-survey-backend-design.md`（名單中心基礎）
- 前置設計：`docs/superpowers/specs/2026-06-19-survey-email-and-unsubscribe-design.md`（寄信管線與退訂閉環）
- exam DB 對外連線：Zeabur GraphQL `portForwardedHost` + `ports.forwardedPort`（43.167.234.24:30167）
