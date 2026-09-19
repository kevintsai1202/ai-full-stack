# 03-layered-architecture｜分層實作的好處旁白稿

本稿依據 `03-layered-architecture.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜三百行 Controller 的災難

建議檔名：`01-fat-controller.wav`

這一節我們要真的動手把 API 做出來了。但在動手之前，先講一個你未來一定會遇到的災難場景：有一種 Controller，裡面塞了三百行程式碼——收參數、判斷業務規則、組資料、算折扣、回傳 JSON，全部混在同一個方法裡。

這種程式碼寫的當下很爽，三個月後要改一個規則，你會發現動一行、壞三處，而且完全沒辦法寫測試。這就是為什麼我說，分層架構是 Spring MVC 的靈魂。

## 02｜Controller 是櫃檯，Service 是幹活的部門

建議檔名：`02-controller-service.wav`

分層的原則其實很簡單：Controller 負責轉接和基本驗證，Service 負責商業邏輯，兩邊切忌混在一起。

講白話一點，Controller 就是接待櫃檯——它只管接 HTTP 請求、把參數整理好、決定回傳格式和狀態碼，不做任何業務判斷。Service 才是真正幹活的部門，業務規則、資料操作都在這裡。

這樣分有什麼實際好處？最直接的一個：這一章我們的資料放在記憶體的 List 裡，下一章要換成 PostgreSQL 加 JPA，到時候你只需要改 Service，Controller 一行都不用動。換資料來源不動門面，這就是分層的威力。

## 03｜為什麼這一章刻意只有兩層

建議檔名：`03-two-layers.wav`

這裡我要特別說明一個教學設計：這一章的示範專案，刻意只有 Controller 和 Service 兩層，資料就放在 Service 裡的一個 Java List。

為什麼不一次到位直接上資料庫？因為我要你先把 Spring MVC 的流程「跑通」——請求進來、路由、呼叫 Service、回 JSON，這條路先走順了，下一章再往 Service 底下加一層 Repository 接資料庫，你就會很清楚每一層在做什麼。先懂架構，再往上疊東西，順序不要反過來。

## 04｜Lombok：跟樣板程式碼說再見

建議檔名：`04-lombok.wav`

動手之前還有一個小工具要介紹：Lombok。寫過 Java 的人都知道那個痛——一個類別四個欄位，你要手寫八個 getter、setter，再加 toString、equals，滿滿兩頁都是樣板程式碼。

Lombok 是一個編譯期的程式碼產生器，你只要在類別上標註解，編譯時它自動幫你生出這些方法。最常用的三個：@Data，一個註解等於 getter、setter、toString、equals、hashCode 全包；@NoArgsConstructor 產生無參數建構子，之後 JPA 會需要；@AllArgsConstructor 產生全欄位建構子，初始化測試資料很方便。還有一個 @Builder，欄位多的類別用起來特別舒服。

在 Spring Boot 專案裡，pom.xml 引入 Lombok 依賴之後，VS Code 的 Java 擴充套件會自動識別，不用再裝額外外掛。

## 05｜把提示詞貼給 AI Agent

建議檔名：`05-ai-prompt.wav`

好，我們現在來實際操作。打開 AI Agent，把教學網站上的建立專案提示詞貼進去——大意是：我有一個只有 spring-boot-starter-web 依賴的 Spring Boot 專案，請建立一個客戶 REST API，資料存在記憶體：GET 拿全部客戶、GET 單筆找不到回 404、POST 新增回 201，加上中文函式級別註解。

提示詞不用抄，網站上可以直接複製。你要注意的是它的結構：我明確講了依賴只有 web、明確講了三個端點和它們的狀態碼行為。把需求講清楚，AI 才做得準——這才是你在課堂上要學走的東西。

## 06｜產出後先看架構再執行

建議檔名：`06-review-first.wav`

你會看到 AI 產出一個 Customer 的 Model、一個 CustomerService、一個 CustomerController，正好就是我們剛講的分層。

產出來之後，別急著跑，先看一眼：Controller 裡面有沒有混業務邏輯？Service 是不是乾淨地管著那個 List？這個檢查動作，就是你作為架構把關者的角色——AI 負責寫，你負責確認它寫在對的層。

## 07｜驗證與延伸練習

建議檔名：`07-verify-extend.wav`

專案啟動之後，用第二段提示詞請 AI 幫你用 PowerShell 的 Invoke-RestMethod 把三個端點都打一遍，你會看到 GET 回傳客戶清單的 JSON、POST 回 201。如果 mvn spring-boot:run 噴錯也不用慌，把錯誤訊息原封不動貼給 AI 排查，這就是第一章講的協作流程。

行有餘力，再做兩個延伸練習：請 AI 把 List 換成 HashMap 加速查詢，或者加一個 DELETE 端點、成功回 204。這兩個練習會讓你體會到——改的都只有一層，另一層不動。

## 08｜本節總結與下一節

建議檔名：`08-closing.wav`

總結一句：Controller 管門面、Service 管邏輯，層分清楚，換資料來源才不用大動土木。

下一節我們來補一個目前這支 API 的大漏洞——它現在前端傳什麼就存什麼，我們要加上輸入驗證。我們下一節見。

## 邊念邊操作提示卡

1. 先打開檔案樹，依序指出 controller、service、repository、dto；念每個責任時停一下，讓學員跟著建立資料夾。
2. 打開一條 GET 或 POST 的呼叫鏈，從 Controller 進入 Service 再到 Repository；用游標標記資料在哪一層被轉換。
3. 顯示測試命令和輸出，念到「請閱讀 diff」時先不要切畫面，留時間讓學員找出 Controller 是否混入商業規則。
4. 若遇到循環依賴或測試失敗，保留錯誤，示範先定位責任邊界再請 AI 修一個局部問題。

## 交付檔案命名

```text
01-fat-controller.wav
02-controller-service.wav
03-two-layers.wav
04-lombok.wav
05-ai-prompt.wav
06-review-first.wav
07-verify-extend.wav
08-closing.wav
```
