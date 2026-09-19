# 01-openapi-swagger｜Open API 文件（Swagger）自動產生旁白稿

本稿依據 `01-openapi-swagger.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜從 demo 到能上線

建議檔名：`01-from-demo-to-production.wav`

歡迎來到第四章。先講一下這一章在整個課程裡的位置：前面三章，我們的客戶 API 已經做出來了，資料也真正存進 PostgreSQL，重開機也不會不見。功能上看起來像一回事了，對不對？但我要老實跟你說，這個東西現在還只是一個 demo，離「能上線」還差一段距離。差在哪裡？差在三件事：別人看不懂你的 API、出錯的時候訊息一團亂、還有——任何人都能呼叫它，包含刪除資料。這一章我們就把這三個洞補起來，本節先處理第一個：文件。

## 02｜為什麼需要 API 文件

建議檔名：`02-why-api-docs.wav`

為什麼文件這麼重要？你想像一個情境：你是後端，隔壁坐一個前端同事，他要串你的客戶查詢 API。他會一直問你：「欄位叫什麼？」「level 可以填哪些值？」「錯誤的時候回什麼？」API 只有五個端點的時候你還能用嘴巴回答，超過五個之後，這種問答就會吃掉你一半的開發時間。更慘的是測試人員和新進同事，他們只能翻你的程式碼去猜 request body 長什麼樣子。

## 03｜規格即文件

建議檔名：`03-spec-as-doc.wav`

業界的解法是 OpenAPI 規範，它的前身就是大家常聽到的 Swagger。它定義了一套描述 REST API 的標準格式，而 Spring 生態裡有一個很棒的套件叫 springdoc-openapi，它會自動掃描你的 Controller，把文件「長」出來，完全不用手寫。這就是我們說的「規格即文件」——文件跟程式碼永遠同步，不會有那種文件寫 A、程式跑 B 的窘境。你只要記住這個觀念就好，套件名稱跟依賴怎麼加，教學網站上都查得到。

## 04｜第一次打開 Swagger UI

建議檔名：`04-first-swagger-ui.wav`

我們現在來實際做一次。第一步，請 AI 幫你在 pom.xml 加入 springdoc-openapi 依賴，然後重新啟動應用程式。你會看到，什麼程式碼都還沒改，瀏覽器打開 swagger-ui.html，你的客戶管理 API 全部都列在上面了——GET、POST、PUT、DELETE，每個端點點開都能看到參數和回應格式，還可以直接按 Try it out 在網頁上呼叫。第一次看到這個畫面通常會有點感動，因為這是零成本得到的文件。

## 05｜加標註讓文件變完整

建議檔名：`05-annotations.wav`

不過你也會發現，這份自動產生的文件有點「乾」——它只能從方法簽章推導，端點名稱是英文方法名，沒有說明。所以第二步，我們加標註讓文件變完整。在 Controller 類別上加 @Tag，寫「客戶管理」和它的用途；在每個方法上加 @Operation，說明這個端點做什麼；再用 @ApiResponse 補上每種 HTTP 狀態碼代表什麼意思。這件事很適合交給 AI，教材裡的提示詞直接複製貼上就好，不用抄。跑完之後重新整理 Swagger UI，你會看到每個端點都有清楚的中文說明，前端同事再也不用來問你了。

## 06｜OpenApiConfig 與環境控制

建議檔名：`06-openapi-config.wav`

第三步，建立一個 OpenApiConfig，設定全域的 API 資訊：標題、版本、聯絡方式。這讓文件開頭有一個像樣的門面，也是驗收作業會看的項目之一。另外補充一個實務提醒：Swagger UI 通常只在開發環境開，正式環境要關掉——用 profile 就能控制，網站上也有對應的提示詞可以直接問 AI。

## 07｜文件也是前後端的合約

建議檔名：`07-api-contract.wav`

最後講一個很多人忽略的重點：springdoc 除了給人看的 UI，還會在 /v3/api-docs 提供機器可讀的 JSON 規格，前端工具可以拿它自動產生 API client。也就是說，這份文件不只是給人讀的，還是前後端之間的「合約」。等我們後面做 React 前端的時候，你會更有感覺。

## 08｜本節總結與下一節

建議檔名：`08-closing.wav`

總結一下：這一節我們用 springdoc-openapi 讓 API 文件自動生成，加上標註讓它完整可讀，從此規格即文件。但你有沒有注意到，現在在 Swagger 上把一筆資料填錯，回傳的錯誤訊息還是一堆看不懂的堆疊資訊？下一節我們就來處理它——全域例外處理，讓錯誤訊息也變得跟文件一樣專業。我們下一節見。

## 邊念邊操作提示卡

1. 先顯示 OpenAPI JSON URL，再開 Swagger UI；旁白念到「這是同一份契約」時，停下來讓學員比較 endpoint 數量。
2. 點開 customers 的 GET 和 POST，逐一指出 request、response、status 與 auth 標記，接著在 UI 執行一次請求。
3. 用終端機重現同一請求，讓畫面同時保留 status 和 body；若文件與實際不一致，保留差異再修正。
4. 收尾保存規格和截圖，說明下一單元會統一錯誤 response。

## 交付檔案命名

```text
01-from-demo-to-production.wav
02-why-api-docs.wav
03-spec-as-doc.wav
04-first-swagger-ui.wav
05-annotations.wav
06-openapi-config.wav
07-api-contract.wav
08-closing.wav
```
