# REST API 命名規範與最佳實踐

本講義為「AI 賦能全端開發」課程的 API 設計教學素材。

## 核心設計原則

### 1. 使用名詞而非動詞描述資源
* **正確**: `POST /api/customers` (新增客戶)
* **錯誤**: `POST /api/createCustomer`

### 2. 使用適當的 HTTP 方法 (Verbs)
* `GET`：讀取資源（安全且等冪）
* `POST`：建立新資源
* `PUT`：更新（替換）資源或狀態
* `DELETE`：刪除資源

### 3. 使用複數名詞
* 統一使用複數形式來代表資源集合。
  * `GET /api/customers` 取得所有客戶
  * `GET /api/customers/{id}` 取得單一客戶

### 4. 資源層級關係表達
當資源之間有從屬關係時，使用路徑表達：
* `POST /api/customers/{id}/interactions` (為指定客戶新增一筆互動紀錄)
* `GET /api/customers/{id}/opportunities` (查詢指定客戶的所有商機)

## DTO 邊界隔離
* **Entity**: 對應資料庫 Schema，負責持久化。
* **DTO (Data Transfer Object)**: 負責 API 輸入 (Request) 與輸出 (Response) 的資料載體。
* **原則**: 絕不直接在 Controller 曝露 Entity，避免資料庫結構外洩，並防止 JSON 循環引用 (StackOverflow) 錯誤。
