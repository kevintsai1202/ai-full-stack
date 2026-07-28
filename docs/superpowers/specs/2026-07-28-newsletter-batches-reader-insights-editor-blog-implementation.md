# 電子報分批寄送、讀者洞察、編輯器與文章站實作

## 目標

同一篇文章可以分多個批次寄送，且每個 Email 永久保留寄送狀態；後續新增且已確認訂閱的讀者可以補寄，已寄送者不可再次選取。讀者管理同時提供收信與解鎖歷程篩選。文章撰寫支援快速 Markdown、Emoji 與 hashtag，公開 archive 改為可依 hashtag 篩選的部落格卡片。

## 資料模型

- `campaign`：文章與內容本體，新增 `cover_emoji`。
- `campaign_batch`：一次立即或排程寄送，保存要求、成功、失敗、略過及狀態。
- `campaign_recipient`：文章對 Email 的永久狀態；`(campaign_id, email_normalized)` 唯一，防止重複寄送。
- `email_log.batch_id`：連結不可變寄送稽核紀錄與實際批次。
- `content_tag`、`campaign_tag`：正規化 hashtag 與文章多對多關聯。

既有 `email_log` 會由 V12 migration 回填為 legacy batch 與逐收件人狀態；新舊資料可在同一個後台檢視。

## 寄送狀態與操作

- `SENT`、`SENDING`、`SCHEDULED`：不可再次勾選。
- `FAILED`、`CANCELLED`：若目前仍是已確認訂閱者，可重新勾選。
- 尚未有狀態且目前符合原文章分眾：顯示為新加入，可補寄。
- 排程批次可在到期前取消；成功取消後收件人可重新選取。
- `PREMIUM` 文章維持安全守門，禁止把受限全文寄入信箱。
- 寄送前重新檢查訂閱資格、永久狀態與行銷額度；資料庫唯一約束及原子保留共同防止併發重寄。

### 後台 API

- `GET /api/admin/campaigns/{campaignId}/recipients`
- `GET /api/admin/campaigns/{campaignId}/batches`
- `POST /api/admin/campaigns/{campaignId}/batches`
- `DELETE /api/admin/campaigns/{campaignId}/batches/{batchId}`

## 讀者洞察

人物搜尋回傳 `deliveryCount`、`lastDeliveryAt`、`unlockCount`、`lastUnlockAt`，並可依下列條件交叉篩選：

- 指定電子報、寄送狀態、寄送日期、寄送次數、從未寄送成功。
- 指定文章、hashtag、解鎖日期、解鎖次數、從未解鎖。

詳細資料視窗顯示人物／帳戶摘要、逐篇收信狀態、文章解鎖與點數帳本。

### 後台 API

- `GET /api/admin/audience/insights/options`
- `GET /api/admin/audience/{personId}/detail`
- `POST /api/admin/audience/search`（擴充 `delivery`、`unlock` 條件）

## Markdown 與文章中繼資料

- 格式列：同類格式使用展開群組；標題提供 H1／H2／H3，程式碼提供行內、
  Java、JavaScript、HTML、Python、JSON、SQL 與 Bash fenced code。
- 單一換行使用 `<br>` 保留，不再被 CommonMark 折成空白。
- 500 ms 防抖即時預覽與 `localStorage` 草稿。
- 封面 Emoji、內文 Emoji、六個預設 hashtag，以及最多八個自訂 hashtag。
- 預覽與測試信呈現目前的圖片／Emoji 封面、主旨及 hashtag；預覽框最小高度
  620px 並允許垂直拉伸。
- 測試寄送在瀏覽器與服務層都先驗證主旨、內文及信箱；空白必填欄位回 400，
  郵件供應商拒絕則回可辨識的 502，不再以無原因的 500 呈現。
- 寄送／發布產生副作用前先驗證 Emoji 與 hashtag。

## 公開文章站

- `/r/archive` 使用雙欄部落格卡片、封面 Emoji、免費區摘要、閱讀時間與 hashtag。
- `/r/archive?tag={slug}` 在伺服器端篩選文章。
- 單篇頁顯示可點選 hashtag，Markdown 的清單、引用、程式碼、表格與分隔線採一致閱讀樣式。
- Archive 摘要只從 `ContentSplitter.freeMarkdown()` 產生，受限區永遠不進入列表 HTML。
- 文章與列表仍使用 `private, no-store` 與 `Vary: Cookie`，不改變既有 paywall 安全模型。

### 讀者導覽視覺

- 公開與登入後選單均提供「首頁」入口，每個選單使用內建 CSS 向量 icon，不依賴第三方字型或外部 CDN。
- `reader-nav.js` 依目前 URL 加上 `aria-current="page"` 與高亮樣式；文章詳情歸類為「歷史內容」。
- 桌面使用毛玻璃頁首與膠囊選單，手機維持 sticky 雙列頁首；選單可水平滑動且不造成整頁水平溢出。
- 鍵盤焦點、減少動態效果偏好與未執行 JavaScript 時的基本連結能力均保留。

## 驗證

- `mvn test`：完整測試套件通過。
- 乾淨 PostgreSQL 套用 V1–V12 migration 通過。
- `verify-admin-reader.mjs`：既有後台端到端案例全部通過。
- 真實瀏覽器驗收：
  - Hashtag 點選後 URL 變為 `/r/archive?tag=rag`，文章由 2 篇縮為 1 篇。
  - 編輯器快捷列、Emoji、預設與自訂 hashtag 可操作。
  - H1／H2／H3 與各程式碼語言群組可展開並插入正確 Markdown；分隔線與付費牆
    插入的是真正換行，不含字面 `\n`。
  - 預覽可看到封面與 hashtag，圖片含 `max-width:100%` 限制，測試信可成功送交供應商。
  - 進階讀者交叉篩選只留下目標人物，詳細視窗正確顯示 `SENT` 與 `#RAG` 解鎖紀錄。
  - 收件人管理預設顯示新加入者；切到 `SENT` 後已寄者核取方塊為 disabled。
  - 讀者導覽在 1440px 與 390px 均無整頁水平溢出；首頁、歷史內容、遊戲規則與登入頁各自只有一個正確的 `aria-current`，實際點選後高亮會隨 URL 切換。
