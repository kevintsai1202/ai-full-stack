# 課程提示詞改寫對照：補齊跨客戶列表端點與 id 可發現性

**日期**：2026-09-20
**背景**：後端做到第四章 Security 時發現 API 不足：沒有「檢視全部門／自己負責的客戶、生意機會、往來紀錄」的列表端點，測試時查不到相關 id。根因是第二章領域模型提示詞只要求「巢狀新增與查詢」，跨客戶列表從未被任何提示詞要求，直到第五章前端才以「後端缺的端點請一併補上」收拾，時序已在權限設計之後。
**原則**：只改提示詞文字與講義中直接受影響的表格，不動章節結構、不動技術路線。提示詞一律用 vibe coding 新人的口吻講「業務需求」，不列端點路徑、參數名、類別名；需要固定的技術規格放進講義，讓學員附上講義而不是自己打。

## 修改順序

1. `teaching-site/course-data.js`（source of truth，含尾逗號非嚴格 JSON，改完用 `node -e` 以 `vm.runInNewContext` 驗證可載入）
2. `node teaching-site/scripts/export-teaching-site-content.mjs`（重產 `course-package/_source/u*.md`，勿手改）
3. 同步 `course-package/ch0X/*.md` 內嵌的逐字提示詞（旁白稿不動，屬講師口述）
4. 更新 `teaching-site/materials/CRM_角色權限矩陣與RBAC實作規格.md` 第五、六節（項目 C 帶出的帳號與端點）
5. `node teaching-site/scripts/verify-course-package.mjs`、`verify-site.mjs`、`verify-render.mjs`

---

## A. ch02 u5 提示詞②「設計成完整的 CRM 客戶關係資料」

**影響檔案**：`course-data.js` L489；`course-package/ch02-spring-mvc-rest-domain/05-crm-domain-model.md` 示範與提示詞段；`_source/u2.md`（匯出）

**舊句**

> 每種都要能新增和查詢；查單一客戶時要一次把他的聯絡人、往來紀錄、生意機會都帶回來；生意機會要能單獨更新階段。

**新句**

> 每種都要能新增和查詢；查單一客戶時要一次把他的聯絡人、往來紀錄、生意機會都帶回來；生意機會要能單獨更新階段。另外，我不只想從某一個客戶點進去看，也要能「一次看全公司所有的生意機會」和「一次看全公司所有的往來紀錄」，可以依客戶或依階段、類型篩選，之後主管看報表、做看板都要用到。查出來的每一筆都要顯示它的編號和所屬客戶，我才知道接下來要拿哪一筆去測試或更新。

**理由**：同章 02-rest-api-design 已教「列出所有商機」，提示詞卻沒要求；第四章權限矩陣講義列了商機與互動這一列，第五章看板也需要全公司的機會列表。在這裡以需求口吻補齊，AI 會自行推出跨客戶端點，後面兩章都不必再臨時長端點。

**連動**：`note` 欄位改為「客戶 / 聯絡人 / 往來紀錄 / 生意機會，欄位與狀態值固定，機會與往來要能全公司一次看」。同節驗證提示詞（test-crm-api.ps1）加一句「也測一次看全公司生意機會、全公司往來紀錄都查得到」。

---

## B. ch03 u5 提示詞③「把資料真正存進資料庫，並支援多條件搜尋」

**影響檔案**：`course-data.js` L689；`course-package/ch03-persistence-and-search/05-dynamic-query.md` L52；`_source/u3.md`

**舊句**

> 另外客戶查詢要能「多個條件任意組合」，用 Spring Data JPA 的 Specification 實作：條件有 keyword（公司名或 email 模糊比對）、industry、owner（負責業務）、status、renewalFrom / renewalTo（預計續約日區間），每個條件都可以不填；要有分頁 page（預設 0）與 size（預設 10），回傳格式固定為 items、page、size、totalElements、totalPages。

**新句**

> 另外客戶查詢要能「多個條件任意組合」：條件有 keyword（公司名或 email 模糊比對）、industry、owner（負責業務）、status、renewalFrom / renewalTo（預計續約日區間），每個條件都可以不填；要有分頁 page（預設 0）與 size（預設 10），回傳格式固定為 items、page、size、totalElements、totalPages。全公司的生意機會清單和往來紀錄清單也要比照辦理：機會可以用客戶、階段、類型、負責業務、預計成交日區間任意組合來查，往來紀錄可以用客戶、類型、發生時間區間來查，分頁格式和客戶一樣。三種查詢請用同一種做法實作，之後我還會再加「只能看自己負責的」這類條件，希望到時候只要改一個地方。

**理由**：第四章「🔸 資料層自動套用資料範圍過濾」要有共用入口才做得到；現況只有客戶有動態查詢，機會與互動無法套用資料範圍過濾。保留原句已有的參數名（那是既有內容，且講義有表對照），新增部分只講「用什麼條件查」。

**連動**：`note` 改為「多條件任意組合＋固定的分頁格式，客戶／機會／往來三種清單比照辦理」。ch03 u6 驗證提示詞加一句「用階段篩機會、用日期區間篩往來紀錄各查一次，分頁欄位正確」。

---

## C. ch04 u3 提示詞①「加上登入與權限控管」

**影響檔案**：`course-data.js` L907；`course-package/ch04-security-jwt-openapi/03-spring-security-jwt.md` L51～79；`_source/u4.md`；講義 `CRM_角色權限矩陣與RBAC實作規格.md` 第一、五、六節與 `JWT_架構與認證流程設計.md` 示範帳號段

**舊句 1**

> - 用 Flyway 建 app_users 與三個示範帳號（sales／manager／admin），密碼用 BCrypt

**新句 1**

> - 用 Flyway 建 app_users 與四個示範帳號：兩位業務 sales 與 sales2、一位主管 manager（兩位業務都歸他管）、一位管理員 admin，密碼都是 password123、用 BCrypt；要記得住「誰是誰的主管」

**舊句 2**

> - 把客戶原本的 ownerName（字串）換成 owner_id 外鍵指向 app_users，用 Flyway 依名字把既有種子資料掛到對應帳號，查詢與回應仍要看得到負責業務的名字

**新句 2**

> - 客戶與生意機會原本只記了「負責業務的名字」，請改成真正對應到帳號，並把既有的示範資料掛好：亞太智能製造、環球零售巨擘歸 sales，鼎峰金融科技和那家資料不足的新客戶歸 sales2；查出來仍要看得到負責業務的名字。新增或編輯客戶、生意機會時可以指定負責業務，不指定就是目前登入的人
> - 登入後我要能查「我是誰、我的角色、我的主管是誰」；主管和管理員還要能列出所有使用者和他們的角色，這樣指派客戶或測試時才找得到人的編號

**舊句 3**

> 完成後我要能驗證：sales 只看得到自己的客戶、刪客戶被 403 擋下，換 admin 才刪得掉。

**新句 3**

> 完成後我要能驗證：sales 只看得到自己的客戶，查 sales2 的客戶回 404；manager 看得到兩位業務的客戶；sales 刪客戶被 403 擋下，換 admin 才刪得掉。

**理由**：只有一位業務時，「看不到別人的客戶」與講義驗收案例第 4 條（查別人的客戶回 404）根本無法示範；主管沒有使用者清單就查不到部屬編號，也無法在測試時把客戶指派給特定業務。技術規格（app_users.manager_id、owner_id、/api/auth/me、/api/users 的欄位）寫進講義第五節與矩陣，學員附講義即可，提示詞只講需求。

**連動**：講義第一節帳號表加 `sales2`；第五節 `app_users.manager_id` 說明改為「sales 與 sales2 皆指向 manager」，並加一列「`/api/auth/me`、`/api/users` 回傳 id、username、displayName、role、managerId」；第六節驗收案例第 4 條改為「sales 取 sales2 的客戶 id」；第二節矩陣加一列「使用者查詢 GET /api/users：SALES ❌、MANAGER ✅ 限部屬、ADMIN ✅」。JWT 講義示範帳號段同步為四個帳號。ch03 u2 種子提示詞（L688）不必改，負責業務名稱由本章依名字掛帳號。

---

## D. ch04 u4 提示詞⓪「盤點路徑、產生空權限表」

**影響檔案**：`course-data.js` L906；`course-package/ch04-security-jwt-openapi/04-crm-authorization.md` 示範段；`_source/u4.md`

**舊句**

> 只要輸出表格，先不要寫任何程式。

**新句**

> 盤點完請對照附上的《CRM_角色權限矩陣與RBAC實作規格.md》第二節，另外列一張「講義上有、但我們專案還沒做出來的功能」清單，讓我決定要先補還是先預留。只要輸出表格與清單，先不要寫任何程式。

**理由**：提示詞⓪要求「不要臆測尚未實作的功能」是對的，但要讓缺口在填表前就浮現，而不是到第五章前端才發現。若 A、B 已套用，這張清單應只剩第五～七章預留的列，正好可當檢查點。

---

## E. ch05 u4 提示詞③「做出 CRM 的核心頁面並接上真實資料」

**影響檔案**：`course-data.js` L1100；`course-package/ch05-react-crm-workbench/04-crm-workbench-design.md` L50；`_source/u5.md`

**舊句**

> 後端缺的端點請一併補上。

**新句**

> 生意機會看板請用後端已經有的「全公司生意機會清單」來做。除了總覽用的 GET /api/dashboard/summary，後端不要自己再新增其他功能；如果做到一半發現前端還缺什麼後端功能，先停下來列給我看，因為新功能要先回第四章的權限表補一列再做。

**理由**：維持「先改表再改程式」的課程原則。第四章權限矩陣講義已明文「後續章節每新增一組端點，就回到這張表補上一列、重跑權限提示詞」，第五章提示詞卻反其道而行。

**連動**：`note` 改為「登入 / 總覽 / 客戶清單 / 客戶詳情 / 生意機會看板（後端只新增總覽數字的端點）」。同節後續兩段（reports、rfm）本就明列新端點，不必改。

---

## 不改的部分與原因

- **ch02 u2 rest-api-design 講義**：已寫 GET /api/opportunities，與新提示詞一致。
- **ch03 u2 種子資料提示詞**：客戶歸屬在第四章才轉成 owner_id，種子維持以 ownerName 字串描述；第二章暖身欄位已含 ownerName。
- **各節旁白稿**：講師口述示範，不是交給 AI 的工作，且仍與新流程相容。
- **live-slides、ai-crm 專案**：本次不在範圍。

## 驗收清單

- [ ] `course-data.js` 五段提示詞改完，`vm.runInNewContext` 載入成功
- [ ] 匯出後 `_source/u2.md`、`u3.md`、`u4.md`、`u5.md` 含新句
- [ ] `course-package` ch02～ch05 內嵌提示詞與 `_source` 逐字一致（verify-course-package PASS）
- [ ] 兩份講義帳號改為四個、驗收案例第 4 條改為 sales2
- [ ] `grep -rn "三個示範帳號\|後端缺的端點請一併補上" course-package teaching-site` 無殘留
