# teaching-site 提示詞重構設計

**日期**：2026-06-19
**目標**：重寫 `teaching-site` 8 個單元的 AI 協作提示詞，使其符合「真實 AI 輔助開發」的寫法——只給目標概念與技術邊界，不列實作細節——並能引導學員產出像 `D:\GitHub\ai-crm` 那樣的全端 CRM 專案。

## 背景與問題

`teaching-site/course-data.js` 是純靜態教學網站的資料權威來源，由 `app.js` 渲染。目前提示詞有四個問題：

1. **太複雜**：提示詞列出 1.2.3.4 編號步驟、貼整段程式碼、指定類別/方法/欄位名稱，不符合真實 AI 輔助開發只寫目標概念的習慣。
2. **範圍過細**：應該「廣而不精」——點名技術但不列實作細節，細節讓 AI 產生、人工核對。
3. **多提示詞無序且未分類**：同一章多張提示詞沒標先後順序，也沒區分建置/驗證/排錯。
4. **說明混在提示詞裡**：原理解釋與提示詞指令混雜，說明應放網頁（concepts 區塊）。

### 探查發現的結構性問題

- **資料雙軌且漂移**：畫面渲染自 `course.day1.units` + `course.day2.units`（`app.js:240` `getAllUnits`），但 `scripts/verify-site.mjs:52` 驗證的是另一個頂層 `course.units` 陣列。兩份各有一套 u1–u8。
- **驗證規則衝突**：`verify-site.mjs:55` 要求 `prompt.length >= 800`，與「簡化提示詞」目標直接衝突。
- **重複的大師範本卡**：每個 unit 的 `prompt`/`promptMac`/`prompts[0].text` 內容幾乎相同，造成提示詞區塊重複顯示。
- **內容漂移**：提示詞寫 port 8080/5432/5173；u6 提示詞含寫死的 API Key 佔位 `"xxxxxxx"`。

## 決策（已與開發者確認）

| 項目 | 決策 |
|---|---|
| 提示詞粒度 | **目標 + 點名技術 + 少量關鍵約束 + 驗收**。移除編號步驟、程式碼、類別/方法名清單。 |
| 說明搬家 | 移入該 unit 既有的 `concepts[]` 講解區塊（原理先行）。 |
| 資料雙軌 | 以 `day1/day2.units` 為唯一權威來源重寫；修 `verify-site.mjs` 改讀此來源並放寬字數限制；頂層 `course.units` 標記移除。 |
| port / 資料庫名 | **保留教學預設值**（8080 / 5432 / 5173 / `learn_spring`）。不對齊 ai-crm 的非衝突 port。 |
| API Key | 改為環境變數寫法，不寫死。 |

## 設計

### A. 提示詞卡公式

每張提示詞卡遵循：

```
目標（一句話，業務語言）
+ 點名技術／框架
+ 少量關鍵約束（固定 port、套件版本、sessionId 隔離等「一變就壞」的要素）
+ 驗收（一句話：完成後我要能做到 X）
```

**範例（u4 Security）**

> 請用 **Spring Security + JWT** 為現有 CRM API 加上登入與角色授權：登入以外的 API 都需帶 Token；角色分 ADMIN 與一般使用者，只有 ADMIN 能刪客戶；Swagger UI 也要登入後才看得到。完成後我要能：用一般帳號登入查得到客戶、刪客戶會被擋；用 admin 才刪得掉。

### B. `prompts[]` 卡片結構與排序

每個 unit 的 `prompts[]` 改為乾淨且有序的卡片陣列，新增 `kind` 欄位：

```js
{
  "title": "① 建立 JWT 登入與角色授權",   // 序號內嵌，先後順序一目了然
  "kind": "build",                          // 'build' | 'verify' | 'fix'
  "note": "一句用途說明（選用）",
  "text": "純指令，無說明散文"
}
```

- 建置卡（`build`）依依賴順序在前。
- 驗證卡（`verify`）緊接其後，標題前綴 `✅ 驗證 —`，渲染時給淡色徽章。
- 排錯卡（`fix`，選用）：保留「貼上錯誤訊息請 AI 修正」這類，但精簡。

### C. 說明搬家

把目前混在提示詞 `text` 裡的原理/步驟解釋移除；確認對應 unit 的 `concepts[]` 已涵蓋該說明（多數已有），缺的補進去。提示詞卡只留指令，符合章節節奏：原理講解（concepts）→ 程式範例 → AI 提示詞 → 驗證。

### D. 清掉重複的大師範本卡，調整渲染

- 移除 `prompts[0]` 與 `prompt` 重複的副本。
- `prompt` / `promptMac` 精簡為「章節總目標一段」（含『請接續 Unit N』連貫語），只服務搜尋與 Windows/Mac 平台切換，不再渲染為卡片。
- 微調 `app.js` 提示詞渲染（約 `app.js:771-810`）：當 `unit.prompts` 存在時**只依序渲染 `prompts[]`**，不再自動注入 master 卡；對 `verify` 卡渲染徽章。
- `styles.css` 新增 `.prompt-verify-badge`（淡色徽章）樣式。

### E. `verify-site.mjs` 修正

- 改讀 `course.day1.units` + `course.day2.units`（畫面真正來源）。
- 移除 `prompt.length >= 800` 規則。
- 新增檢查：每個 unit 至少 1 張 `kind: 'build'` 卡與 1 張 `kind: 'verify'` 卡；`prompts[].text` 不得包含「```」三反引號（確保提示詞不貼程式碼）。
- 保留圖片覆蓋率檢查（`illustrations.length >= 3`）。
- 頂層 `course.units` 死資料移除。

## 受影響檔案

- `teaching-site/course-data.js`——重寫 8 unit 的 `prompts[]`、精簡 `prompt`/`promptMac`、移除頂層 `units`。
- `teaching-site/app.js`——提示詞渲染邏輯簡化 + verify 徽章。
- `teaching-site/styles.css`——新增 verify 徽章樣式。
- `teaching-site/scripts/verify-site.mjs`——驗證來源與規則調整。

## 驗收標準

1. `node scripts/verify-site.mjs` 通過（新規則）。
2. `node scripts/verify-render.mjs` 通過（RWD、進度、複製按鈕無回歸）。
3. 每個 unit 提示詞卡：建置卡在前、驗證卡標 ✅ 在後、無程式碼貼附、無說明散文。
4. 8 個單元提示詞串起來，能引導 AI 產出 ai-crm 的功能與架構（後端 Spring Boot + JPA + Flyway + Security + Spring AI + RAG + MCP；前端 React + Vite）。
5. 提示詞不含寫死的 API Key。

## 非目標（YAGNI）

- 不改課程大綱、goals、concepts 的教學主軸（只在 C 項補必要說明）。
- 不對齊 ai-crm 的非衝突 port。
- 不重構 app.js 與提示詞無關的部分。
