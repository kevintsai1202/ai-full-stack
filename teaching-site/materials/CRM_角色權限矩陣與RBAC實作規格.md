# CRM 角色權限矩陣與 RBAC 實作規格

**本文件是 AI CRM 專案權限設計的參考答案與驗收規格。** 課堂流程是：先用提示詞⓪ 讓 AI 盤點目前的端點、產生空權限表，由你逐格決定 ✅／🔸／❌，填完再與本文件第二、三節對照；實作時把你填好的表連同本文件一起附給 AI Agent。日後要調整權限，也是先改表、再重跑提示詞，而不是直接改程式。

## 一、角色定義

| 角色代碼 | 中文名稱 | 定位 | 示範帳號 |
|---|---|---|---|
| `SALES` | 業務人員 | 只處理自己負責的客戶與商機 | `sales`、`sales2` |
| `MANAGER` | 業務主管 | 看得到整個團隊的資料與報表 | `manager` |
| `ADMIN` | 系統管理員 | 管知識庫、系統設定與全公司資料 | `admin` |

角色值存放於 `app_users.role`，登入後寫入 JWT 的 `role` claim；所有示範帳號密碼皆為 `password123`。

## 二、功能權限矩陣

| 功能面向 | 代表端點 | SALES 業務 | MANAGER 主管 | ADMIN 管理員 |
|---|---|---|---|---|
| 客戶查詢 | `GET /api/customers` | 🔸 限本人負責 | 🔸 限團隊成員 | ✅ 全部 |
| 客戶新增／編輯 | `POST /api/customers`、`PUT /api/customers/{id}` | 🔸 限本人負責 | 🔸 限團隊成員 | ✅ 全部 |
| 客戶刪除 | `DELETE /api/customers/{id}` | ❌ | ❌ | ✅ |
| 使用者查詢 | `GET /api/auth/me`、`GET /api/users` | ✅ 只有 me | ✅ users 限部屬 | ✅ |
| 商機與互動紀錄 | `/api/opportunities/**` | 🔸 限本人負責 | 🔸 限團隊成員 | ✅ 全部 |
| 團隊報表與儀表板 | `GET /api/manager/analytics` | ❌ | ✅ 團隊範圍 | ✅ 全公司 |
| 待辦任務（自己的） | `POST`、`GET /api/tasks`、`PATCH /api/tasks/{id}` | ✅ 僅自己的 | ✅ 僅自己的 | ✅ |
| 指派任務給他人 | `POST /api/manager/tasks` | ❌ | ✅ 限部屬 | ✅ |
| AI 助理對話 | `GET /api/ai/stream` | ✅ 但資料範圍受限 | ✅ 但資料範圍受限 | ✅ |
| RAG 知識庫管理 | `/api/rag/**`（上傳／刪除） | ❌ | ❌ | ✅ |
| API 文件與系統監控 | `/swagger-ui/**`、`/actuator/**` | ❌ | ❌ | ✅ |

**圖例**

| 符號 | 意義 | 實作位置 |
|---|---|---|
| ✅ | 可存取，且看得到全部資料 | 端點層放行，資料層不加條件 |
| 🔸 | 可存取，但自動套用資料範圍過濾 | 端點層放行，資料層補 `owner_id` 條件 |
| ❌ | 不可存取，直接回 403 Forbidden | 端點層攔截（`requestMatchers` / `@PreAuthorize`） |

**本章實作範圍**：矩陣中的團隊報表、待辦任務、AI 助理、RAG 知識庫等列，是第五～七章才會實作的端點，先預留不動。第四章只實作提示詞⓪ 盤點出來的列（客戶、商機、Swagger UI、Actuator）；後續章節每新增一組端點，就回到這張表補上一列、重跑權限提示詞。

## 三、資料可視範圍（🔸 的實際判定）

同樣呼叫 `GET /api/customers`，三種角色拿到的資料筆數不同。這一層**不能靠端點規則擋**，必須在查詢時自動補上過濾條件。

| 角色 | 判定依據 | 自動加上的查詢條件 |
|---|---|---|
| `SALES` | `customer.owner_id` | `owner_id = 當前登入者 id` |
| `MANAGER` | `app_users.manager_id` | `owner_id IN (自己 + 直屬部屬的 id)` |
| `ADMIN` | — | 不加任何條件 |

**單筆存取的例外規則**：`GET`／`PUT /api/customers/{id}` 若該筆資料不在可視範圍內，回 **404 而非 403**。回 403 等於告訴對方「這筆資料存在、只是你看不到」，反而洩漏了資訊。

## 四、三層防線，缺一不可

| 層級 | 負責的事 | 實作手段 | 對應矩陣符號 |
|---|---|---|---|
| 端點層 | 這個角色能不能走進這條路徑 | `SecurityConfig.requestMatchers`、`@PreAuthorize` | ❌ |
| 資料層 | 走進來之後看得到幾筆 | JPA `Specification` 動態補 `owner_id` 條件 | 🔸 |
| AI 工具層 | AI 代替使用者查資料時的邊界 | `@Tool` 方法一律走同一套 Specification | 🔸 |

前端隱藏按鈕**不算防線**，只是體驗優化；後端沒擋就等於沒擋。AI 工具層特別容易被忽略——AI 會很樂意幫使用者讀出他無權看到的資料。

## 五、資料表異動需求

| 資料表 | 欄位 | 說明 |
|---|---|---|
| `app_users` | `manager_id` | 自我參照外鍵，指向直屬主管；`sales`、`sales2` 皆指向 `manager` |
| `customers` | `owner_id` | 負責業務的使用者 id，需建索引；由第二章的 `ownerName` 字串轉換而來，migration 要依名字把既有資料掛到對應帳號：亞太智能製造、環球零售巨擘掛 `sales`，鼎峰金融科技與資料不足的新客戶掛 `sales2` |
| `opportunities` | `owner_id` | 負責業務的使用者 id，需建索引 |

以上異動一律透過 Flyway migration 新增，不使用 `ddl-auto` 自動建表。

**身分查詢端點**：`GET /api/auth/me` 回目前登入者的 `id`、`username`、`displayName`、`role`、`managerId`；`GET /api/users` 回所有使用者的同樣欄位，限 MANAGER（只回自己與部屬）與 ADMIN。新增／編輯客戶與商機可帶 `ownerId`，不帶則為目前登入者。

## 六、驗收案例

實作完成後，以三種身分各跑一次，狀態碼與筆數需符合下表。

| # | 身分 | 操作 | 期望結果 |
|---|---|---|---|
| 1 | 未登入 | `GET /api/customers` | 401，ProblemDetail 格式 |
| 2 | `sales` | `GET /api/customers` | 200，且只含 `owner_id` 等於自己的資料 |
| 3 | `sales` | `DELETE /api/customers/{id}` | 403，ProblemDetail 格式 |
| 4 | `sales` | `GET /api/customers/{sales2 的客戶 id}` | 404（不是 403） |
| 5 | `sales` | `GET /api/manager/analytics` | 403 |
| 6 | `manager` | `GET /api/customers` | 200，含 `sales` 與 `sales2` 兩人的資料 |
| 6a | `sales` | `GET /api/users` | 403；`manager` 呼叫則 200 且含兩位部屬 |
| 7 | `manager` | `GET /api/manager/analytics` | 200，範圍限團隊 |
| 8 | `manager` | `POST /api/rag/upload` | 403（知識庫僅 ADMIN） |
| 9 | `admin` | `DELETE /api/customers/{id}` | 204 |
| 10 | `admin` | `/swagger-ui/index.html` | 200，可正常瀏覽與測試 |
| 11 | `sales` | 透過 AI 助理詢問他人客戶 | AI 查不到資料，不得回答其他業務的客戶內容 |

## 七、怎麼交給 AI Agent

1. 先用提示詞⓪ 讓 AI 掃描 Controller 產生空權限表（AI 只盤點現況，不決定權限），自己逐格填入 ✅／🔸／❌ 與資料可視範圍規則。
2. 填完與本文件第二、三節對照，想法不同的格子先弄清楚原因再定案；驗收案例一律以本文件第六節為準。
3. 把你填好的表連同這份 `.md`（至少第三、四、五、六節）一起附給 AI Agent，並加上一句：「這張表是唯一規格，請勿自行增減功能或放寬條件。」
4. 要求 AI 完成後輸出一份對照表，說明**每一列權限是由哪個檔案的哪段程式實現的**——這是最有效的驗收方式，能立刻看出哪一列被漏掉或被偷偷放寬。
5. 權限調整時，改表格後重新執行同一段提示詞，讓程式與規格保持同步。
