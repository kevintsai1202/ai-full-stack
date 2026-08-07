# 章節 4 單元 1｜Open API 文件（Swagger）自動產生

## 單元定位

前三章我們已經把客戶 API 做出來、也讓資料真正落進 PostgreSQL 了，但這套 API 目前只有「寫的人」知道怎麼呼叫。本節解決的是協作問題：透過 springdoc-openapi 自動產生互動式 API 文件，讓前端與測試人員不需要看程式碼就能理解與呼叫 API。本節也為單元 3 鋪路——之後 Swagger UI 本身會被 Security 保護、並且要帶 JWT 才能測試 API。建議時長：20～25 分鐘。

## 教學素材

### 為什麼需要 API 文件

後端 API 一旦超過 5 個端點，沒有文件的開發協作就開始痛苦：前端不知道要傳什麼格式、測試人員要翻程式碼才知道有哪些欄位、新人要花大量時間猜 request body 結構。

OpenAPI 規範（前身是 Swagger）定義了一套描述 REST API 的標準格式，springdoc-openapi 能自動從 Spring MVC 的 Controller 掃描產生 OpenAPI 文件，並提供互動式 UI 讓人直接從瀏覽器呼叫 API：

- **自動掃描**：不需要手寫文件，從 Controller 標註推導。
- **Swagger UI**：瀏覽器直接測試每個端點，看到 request / response 格式。
- **機器可讀格式**：前端工具可從 `/v3/api-docs` 取得 JSON 格式規格，自動產生 API client。

這就是核心原則裡說的「規格即文件」——文件不是另外維護的 Word 檔，而是從程式碼自動長出來、永遠跟程式同步。

### OpenAPI 標註讓文件更完整

不加標註時 Swagger UI 只能從方法簽章推導基本資訊。用 `@Operation`、`@Parameter`、`@ApiResponse` 補充說明後，文件立即更完整，前端閱讀效率大幅提升。

```java
@RestController
@RequestMapping("/api/customers")
@Tag(name = "客戶管理", description = "客戶的新增、查詢、修改與刪除")
public class CustomerController {

    @Operation(
        summary = "查詢所有客戶",
        description = "回傳完整客戶清單，可加 keyword 參數進行模糊搜尋"
// ... 完整程式碼請參考課程 GitHub 專案 ...
    }
}
```

### 本節對應的實作任務

- 在 `pom.xml` 加入 springdoc-openapi 依賴並啟動應用，確認可存取 `/swagger-ui.html`。
- 在 CustomerController 加上 `@Operation` 與 `@ApiResponse` 標註。
- 建立 OpenApiConfig 設定全域 API 資訊（標題、版本、聯絡方式）。

## 示範與提示詞

試著用以下提示詞讓 AI 助手幫你完善 API 文件標註：

> 「請幫我在 CustomerController 的所有端點加上 @Operation 說明，並補充每個可能的 HTTP 狀態碼對應的 @ApiResponse 標註。」

> 「如何讓 Swagger UI 只在 dev profile 啟用，在 prod profile 自動關閉？請修改 application.yml 與 OpenApiConfig。」

另外，本章 prompts 中的「② 做一份線上操作說明頁，並統一錯誤訊息」也涵蓋本節的目標（統一錯誤的部分在單元 2 完成）：

```text
請幫我做一份「線上的 API 操作說明頁」，讓我能直接在上面看到有哪些功能、並直接測試它們。另外，當操作出錯時（例如資料填錯、找不到、沒權限），都要回給我格式一致、看得懂的錯誤訊息，而不是一堆看不懂的程式錯誤。這個說明頁一樣要登入後才能使用。請加中文註解。
```

驗證方式：瀏覽器開 `http://localhost:8080/swagger-ui/index.html`，確認每個客戶端點都有中文說明、能直接 Try it out。

## 口語稿

歡迎來到第四章。先講一下這一章在整個課程裡的位置：前面三章，我們的客戶 API 已經做出來了，資料也真正存進 PostgreSQL，重開機也不會不見。功能上看起來像一回事了，對不對？但我要老實跟你說，這個東西現在還只是一個 demo，離「能上線」還差一段距離。差在哪裡？差在三件事：別人看不懂你的 API、出錯的時候訊息一團亂、還有——任何人都能呼叫它，包含刪除資料。這一章我們就把這三個洞補起來，本節先處理第一個：文件。

為什麼文件這麼重要？你想像一個情境：你是後端，隔壁坐一個前端同事，他要串你的客戶查詢 API。他會一直問你：「欄位叫什麼？」「level 可以填哪些值？」「錯誤的時候回什麼？」API 只有五個端點的時候你還能用嘴巴回答，超過五個之後，這種問答就會吃掉你一半的開發時間。更慘的是測試人員和新進同事，他們只能翻你的程式碼去猜 request body 長什麼樣子。

所以業界的解法是 OpenAPI 規範，它的前身就是大家常聽到的 Swagger。它定義了一套描述 REST API 的標準格式，而 Spring 生態裡有一個很棒的套件叫 springdoc-openapi，它會自動掃描你的 Controller，把文件「長」出來，完全不用手寫。這就是我們說的「規格即文件」——文件跟程式碼永遠同步，不會有那種文件寫 A、程式跑 B 的窘境。

我們現在來實際做一次。第一步，請 AI 幫你在 pom.xml 加入 springdoc-openapi 依賴，然後重新啟動應用程式。你會看到，什麼程式碼都還沒改，瀏覽器打開 swagger-ui.html，你的客戶管理 API 全部都列在上面了——GET、POST、PUT、DELETE，每個端點點開都能看到參數和回應格式，還可以直接按 Try it out 在網頁上呼叫。第一次看到這個畫面通常會有點感動，因為這是零成本得到的文件。

不過你也會發現，這份自動產生的文件有點「乾」——它只能從方法簽章推導，端點名稱是英文方法名，沒有說明。所以第二步，我們加標註讓文件變完整。在 Controller 類別上加 @Tag，寫「客戶管理」和它的用途；在每個方法上加 @Operation，用 summary 和 description 說明這個端點做什麼；再用 @ApiResponse 補上每種 HTTP 狀態碼代表什麼意思。這件事很適合交給 AI，你可以直接用我提供的提示詞：「請幫我在 CustomerController 的所有端點加上 @Operation 說明，並補充每個可能的 HTTP 狀態碼對應的 @ApiResponse 標註。」跑完之後重新整理 Swagger UI，你會看到每個端點都有清楚的中文說明，前端同事再也不用來問你了。

第三步，建立一個 OpenApiConfig，設定全域的 API 資訊：標題、版本、聯絡方式。這讓文件開頭有一個像樣的門面，也是驗收作業會看的項目之一。另外補充一個實務提醒：Swagger UI 通常只在開發環境開，正式環境要關掉，這也有對應的提示詞可以問 AI 怎麼用 profile 控制。

最後講一個很多人忽略的重點：springdoc 除了給人看的 UI，還會在 /v3/api-docs 提供機器可讀的 JSON 規格，前端工具可以拿它自動產生 API client。也就是說這份文件不只是給人讀的，還是前後端之間的「合約」。

總結一下：這一節我們用 springdoc-openapi 讓 API 文件自動生成，加上標註讓它完整可讀，從此規格即文件。但你有沒有注意到，現在在 Swagger 上把一筆資料填錯，回傳的錯誤訊息還是一堆看不懂的堆疊資訊？下一節我們就來處理它——全域例外處理，讓錯誤訊息也變得跟文件一樣專業。
