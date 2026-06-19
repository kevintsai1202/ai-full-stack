# 子專案 B：問卷頁 + 後端儲存 設計

**日期**：2026-06-19
**目標**：在 springai.world landing page 新增問卷調查頁，把回應（含 email 與同意接收電子報）儲存到資料庫，並設計成後續子專案 C（電子報/寄信）可直接取用名單。

**所屬計畫**：land-page 行銷功能三部曲 A（廣告埋碼，已完成）→ **B（本文件）** → C（電子報/寄信，後續）。

## 範圍

**包含**：問卷前端頁、後端儲存 API、資料庫、受保護的名單查詢/匯出 API、部署到 Zeabur、驗證腳本。
**不包含（留給 C）**：所有寄信、電子報、廣告信、退訂流程。B 只先備好 `consent` 與 `unsubscribed` 欄位。

## 決策（已與開發者確認）

| 項目 | 決策 |
|---|---|
| 後端技術 | Spring Boot 3.5 / Java 21 + PostgreSQL（與課程／ai-crm 同棧） |
| 部署 | 同一個 Zeabur 專案 `hahow-ai-full-stack`（`6a3483c107afd8c0435e56c0`）新增 git 服務，Root Directory=`survey-backend`；PostgreSQL 用 Zeabur 模板部署 |
| 問卷頁 | land-page 新增獨立靜態頁 `survey.html`，POST 到後端 `/api/survey` |
| 資料檢視 | 受保護的管理 API（`X-Admin-Key` 標頭），支援 JSON 與 CSV 匯出；不另做後台頁面 |
| email 唯一性 | 不設 unique，允許重複填寫，去重留給 C |
| 寄信 | 全部留給 C；B 不寄任何信 |

## 架構

```
springai.world/survey            land-page 靜態服務（既有）
   │  fetch POST /api/survey  (JSON, 含 UTM 歸因)
   ▼
survey-backend                   Spring Boot 3.5 / Java 21（新 Zeabur git 服務）
   │  Spring Data JPA + Flyway
   ▼
Zeabur PostgreSQL                同專案模板部署
```

跨來源：survey.html 在 `springai.world`，後端在自己的 Zeabur 網域（generated，如 `survey-api.zeabur.app`）。後端 CORS 僅允許 `https://springai.world`（外加該 zeabur.app 網域與 `http://127.0.0.1:*` 供開發）。survey.html 以一個明顯可填的 `API_BASE` 常數指向後端網域。

## 資料模型

Flyway `V1__create_survey_response.sql` 建立資料表 `survey_response`：

| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGSERIAL PK | |
| email | TEXT NOT NULL | 後續電子報/通知用 |
| name | TEXT | 稱呼，選填 |
| role | TEXT | 學生／後端／前端／全端／其他 |
| experience | TEXT | Java/Spring 經驗區間 |
| interest | JSONB | 複選主題陣列（RAG／Tool Calling／MCP／Security／部署） |
| budget | TEXT | 可接受價位帶 |
| utm | JSONB | UTM 歸因（source/medium/campaign/term/content） |
| consent | BOOLEAN NOT NULL | 同意接收課程資訊與電子報（PDPA） |
| unsubscribed | BOOLEAN NOT NULL DEFAULT false | 退訂旗標，留給 C |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | 送出時間 |

JPA 設 `ddl-auto: validate`，schema 由 Flyway 管理。`interest`/`utm` 以 JSONB 對應（用字串存 JSON 或 Hibernate JSON 型別）。

## API 端點

- `POST /api/survey`（公開）
  - 請求 JSON：`{ email, name?, role?, experience?, interest?: string[], budget?, utm?: object, consent, website? }`
  - `website` 為**蜜罐**隱藏欄位：若有值視為機器人，回 204 但不寫入。
  - Bean Validation：`email` `@Email @NotBlank`；`consent` 必須為 `true`（否則 400）。
  - 成功寫入回 `201 Created`。
- `GET /api/admin/survey`（受保護）
  - 需 `X-Admin-Key` 標頭，比對環境變數 `ADMIN_API_KEY`；不符回 401。
  - 預設回 JSON 清單；`?format=csv` 回 CSV（UTF-8 BOM，方便 Excel 開）。
- `GET /api/health`：回 200 與簡單狀態。

## 安全與錯誤處理

- CORS：僅允許 `https://springai.world` + 後端自身 zeabur.app 網域 + `http://127.0.0.1:*`（dev）。
- 統一錯誤回應：用 Spring 的 `ProblemDetail`（驗證失敗、缺 consent、admin key 錯誤）。
- 機密（DB 連線、`ADMIN_API_KEY`）一律環境變數，不寫死。
- 基本濫用防護：蜜罐欄位 + 後端對 `email` 長度與 payload 大小設上限。
- 程式碼需中文函式級註解。

## 前端 survey.html

- 沿用 land-page 既有 CSS 變數與視覺（毛玻璃卡片、accent 色），與主站一致。
- 表單欄位對應資料模型；`consent` checkbox 必勾，附 PDPA 同意說明文字。
- 載入時從 `sessionStorage` 讀 `tracking.js` 存的 `utm`，送出時一併帶上。
- 前端驗證（email 格式、consent）；fetch POST 到 `API_BASE + '/api/survey'`。
- 三種 UI 狀態：送出中、成功（顯示感謝訊息）、失敗（顯示重試）。
- 成功時呼叫 `window.Tracking.event('survey_submit')`（接上子專案 A 的轉換追蹤）。
- 蜜罐欄位以 CSS 隱藏，一般使用者看不到。

## 部署

1. **PostgreSQL**：Zeabur 模板部署到專案 `hahow-ai-full-stack`，取得連線環境變數。
2. **survey-backend**：git 部署（同 repo、branch main），Root Directory=`survey-backend`；設定環境變數：DB 連線（引用 Zeabur PostgreSQL）、`ADMIN_API_KEY`、`APP_CORS_ALLOWED_ORIGINS`。產生 Zeabur 網域。
3. **survey.html**：放進 land-page，把 `API_BASE` 設為 survey-backend 網域；從 landing page 導覽或 CTA 連入。push 後 land-page 自動重建。

## 驗證

- `survey-backend/scripts/test-survey-api.ps1`（PowerShell 7+）：POST 一筆測試問卷 → 預期 201 → 用 `X-Admin-Key` GET `/api/admin/survey` → 確認該筆存在；再測缺 consent 回 400、蜜罐有值回 204 不寫入。
- 本機啟動說明：可連本機 Docker PostgreSQL 或 Zeabur PostgreSQL；`mvn -pl survey-backend spring-boot:run`。
- 前端：本機開 survey.html（指向本機後端），送出測試一筆，確認成功 UI 與 DB 寫入、UTM 有帶入。

## 驗收標準

1. survey.html 在 springai.world/survey 可填寫送出，成功顯示感謝訊息。
2. 後端 `POST /api/survey` 正確寫入 PostgreSQL；缺 consent 回 400；蜜罐欄位有值不寫入。
3. `GET /api/admin/survey` 帶正確 key 回名單、錯誤 key 回 401；CSV 匯出可用 Excel 正常開啟（含 BOM）。
4. UTM 歸因有隨問卷一起存入。
5. 送出成功觸發 `survey_submit` 追蹤事件。
6. 服務部署於 Zeabur 同專案，push main 自動重建。

## 非目標（YAGNI）

- 不做寄信／電子報／退訂（C）。
- 不做問卷編輯後台 UI（只做受保護 API + CSV 匯出）。
- 不做使用者帳號系統、不做問卷多版本管理。
