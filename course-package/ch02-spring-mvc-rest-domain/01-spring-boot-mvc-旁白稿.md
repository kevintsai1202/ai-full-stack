# 01-spring-boot-mvc｜Spring Boot 與 MVC 架構旁白稿

本稿依據 `01-spring-boot-mvc.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜承接第一章：請求進去之後發生什麼事

建議檔名：`01-request-question.wav`

歡迎來到第二章。上一章我們把環境裝好了、專案骨架也建起來了，後端可以啟動，也知道我們整門課要做的是一套 AI CRM。但你有沒有想過一個問題：當前端發一個請求給後端，這個請求進到 Spring Boot 裡面，到底發生了什麼事？

很多人寫了好幾年 Spring Boot，其實答不出這題。答不出的後果就是：出錯的時候不知道去哪找問題——404 到底是路由沒對到，還是資料不存在？JSON 格式怪怪的，是誰負責序列化的？這一節，我們把這條路徑一次走清楚。

## 02｜Spring Boot 幫你省下什麼

建議檔名：`02-spring-boot-value.wav`

先講 Spring Boot 本身。為什麼全世界的 Java 課程幾乎都用它當起點？因為它把大量繁瑣的設定折疊起來了。

它有兩個關鍵機制。第一個叫 Starter Dependencies，你不用一個一個挑函式庫，而是以「情境」為單位引入依賴——要做 Web 就引 web 這個 starter，之後要做資料庫就引 data-jpa。第二個叫 Auto-Configuration，自動配置，它會根據你的 classpath 和設定推導出合理的預設值，自動幫你建好常見的 Bean。

這裡要特別提醒：重點不是去背 Spring Boot 幫你做了哪些設定，而是理解它讓你省下了什麼機械工作。省下來的時間放在哪？放在模組責任的劃分和 API 的設計上，這才是這門課要練的東西。

## 03｜前端控制器：DispatcherServlet 是總機

建議檔名：`03-dispatcher-servlet.wav`

那請求進來之後怎麼流動？Spring MVC 用的是一個叫「前端控制器」的設計模式，英文是 Front Controller。意思是：所有進來的 HTTP 請求，不管你打哪個網址，都會先經過同一個統一入口，這個入口叫 DispatcherServlet。

你可以把它想像成公司的總機——所有電話都先打到總機，總機再根據你要找誰，轉接到對應的分機。DispatcherServlet 做的就是這件事：接到請求之後，根據 URL 和 HTTP 方法，找到對應的 Controller 方法，把請求轉過去。

## 04｜走一遍完整流程

建議檔名：`04-request-flow.wav`

我們拿具體例子走一遍。前端發出 GET /api/customers，第一站是 DispatcherServlet；它看到這個路徑和 GET 方法，去比對哪個 Controller 標了對應的 @GetMapping，找到就呼叫那個方法。Controller 接著呼叫 Service 拿資料——注意喔，這一章我們的資料先放在 Java 的 List 裡，還不接資料庫，下一章才換成真的 PostgreSQL。

Service 把資料交回給 Controller，最後因為我們用的是 @RestController，Spring 會自動把回傳的 Java 物件序列化成 JSON 回給前端。這整條路對你幾乎是透明的。好處是：路由、例外處理、JSON 轉換這些「每支 API 都需要、但跟業務無關」的事，全部由框架統一管理，你的 Controller 只要專心寫業務邏輯。

## 05｜IoC 與 DI：物件交給容器管

建議檔名：`05-ioc-di.wav`

最後講兩個名詞，IoC 跟 DI。面試很愛考，但概念其實很生活化。IoC 是控制反轉，意思是物件的建立和生命週期不再由你手動 new，而是交給 Spring 容器管理。DI 是依賴注入，容器在執行期把你需要的物件「塞」給你。

實務上的樣子就是：Controller 依賴 Service，Service 之後會依賴 Repository，但這幾層彼此都不自己 new 對方，全部由容器安排。而在 Spring Boot 裡最推薦的注入方式是建構子注入——它保證物件一被建立，依賴就是完整的，而且之後寫測試會容易很多。

## 06｜拿問題去問 AI

建議檔名：`06-ask-ai.wav`

等一下示範專案跑起來之後，你可以拿兩個問題去問 AI 助手，加深理解。第一個：@RestController 跟 @Controller 差在哪？如果改用 @Controller，要在哪裡加什麼才能讓回傳值變成 JSON？第二個：為什麼 getById 要回傳 ResponseEntity 而不是直接回傳 Customer？

這兩段提示詞的完整原文都在教學網站上，不用抄，直接複製貼上就好。你在課堂上要做的，只有理解這兩題的答案為什麼重要——它們會讓你對這一節講的請求流程有更立體的感覺。

現在我們把剛才的流程真的跑一遍。請先在專案根目錄執行 `mvn spring-boot:run`，等待終端機出現應用程式啟動完成；接著開另一個 PowerShell 視窗，執行 `Invoke-RestMethod http://localhost:8080/api/customers`。你會看到 JSON 客戶清單，這代表請求已經走過 DispatcherServlet、Controller 與 Service，最後由 `@RestController` 序列化回來。若回傳 404，先回到 Controller 檢查路徑與 HTTP 方法；若服務尚未啟動，先看啟動視窗的第一個錯誤，不要只重試同一個命令。最後把這個回應貼給 AI，請它依照實際結果解釋每一站，不要讓它用猜的。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結一句：所有請求都先進 DispatcherServlet，再分發給 Controller，Controller 找 Service 拿資料，最後自動序列化成 JSON 回去。

下一節，我們來談這些 API 的「長相」該怎麼設計——也就是 REST API 的設計原則。我們下一節見。

## 邊念邊操作提示卡

1. 先讓畫面停在專案根目錄與終端機，念「我們先確認能建置」後執行 `mvn clean compile`；只有看到成功，才繼續啟動服務。
2. 執行 `mvn spring-boot:run` 時，請把啟動 log 保留在畫面下方；念到「現在呼叫健康檢查」就切到另一個終端機執行 `Invoke-RestMethod`。
3. 打開 Controller 檔案，再依序切到 Service 和 Repository，邊念邊用游標指出資料如何往下流；接著用不存在的 URL 示範 404。
4. 若畫面出現資料庫連線錯誤，旁白要停下來分辨它與 MVC 路由問題，並把錯誤原文保存，而不是剪掉這段。

## 交付檔案命名

```text
01-request-question.wav
02-spring-boot-value.wav
03-dispatcher-servlet.wav
04-request-flow.wav
05-ioc-di.wav
06-ask-ai.wav
07-closing.wav
```
