# 章節 2 單元 1｜Spring Boot 與 MVC 架構

## 單元定位

上一章我們已經把開發環境裝好、建立了前後端分離的專案骨架，後端也能成功啟動。本節要回答的問題是：「一個 HTTP 請求打進 Spring Boot 之後，到底發生了什麼事？」理解 DispatcherServlet 的請求流程與 IoC / DI 的實務意義，是後面所有 API 開發的地基。下一節會接著講 REST API 的設計原則。建議時長：12～15 分鐘。

## 教學素材

### Spring Boot 為什麼適合教學起點

Spring Boot 的核心價值是把大量繁瑣設定折疊起來，讓開發者更快進入業務流程與架構理解：

- **Starter Dependencies**：讓依賴以「情境」為單位引入，例如 `web`、`data-jpa`，幫你組合常見依賴。
- **Auto-Configuration**：根據 classpath 與設定推導出合理預設，自動建立常見 Bean。
- 教學上最重要的不是背誦 Spring Boot 幫你做了哪些設定，而是理解它讓你省下什麼樣的機械工作——開發者的重點因此可以轉移到模組責任與 API 設計。

### Spring MVC 的核心：請求如何流動

Spring MVC 是 Spring Framework 的 Web 層模組，也是 Spring Boot 預設的 HTTP 請求處理機制。它採用「前端控制器（Front Controller）」設計模式：所有進來的 HTTP 請求都先經過統一入口 **DispatcherServlet**，再由它分發給對應的 Controller 方法。

這個設計的好處是：請求路由、例外處理、內容協商（JSON / XML 格式）等橫切關注點都由框架統一管理，Controller 只需要專心寫業務邏輯，完全不需要自己解析 HTTP 細節。

完整流程：

```text
HTTP 請求（例如 GET /api/customers）
    ↓
DispatcherServlet（Spring MVC 統一入口）
    ↓  根據 @GetMapping("/api/customers") 路由
@RestController 方法
    ↓  呼叫業務層
@Service
    ↓  取得資料（本章示範：Java List，下一章才接資料庫）
資料來源
    ↓
JSON 序列化 → 回傳給前端
```

- 瀏覽器或前端發出 HTTP 請求（例如 `GET /api/customers`）
- DispatcherServlet 接收後，根據 URL 與 HTTP Method 找到對應的 @Controller 方法
- Controller 呼叫 Service 取得資料
- `@RestController` 自動把回傳的 Java 物件序列化成 JSON 回應給前端
- 這整個流程對開發者幾乎是透明的，Spring Boot 自動組態幫你啟動好一切

### IoC 與 DI 的實務解釋

- **IoC（控制反轉）**：物件建立與生命週期不再由你手動處理，而是交給 Spring 容器。
- **DI（依賴注入）**：容器在執行期把相依物件注入給需要它的類別。
- 在 Spring Boot 中，最推薦的形式是**建構子注入**——保證依賴在物件建立時就完整，並且更容易撰寫測試。
- 實際的依賴鏈：Controller 依賴 Service、Service 依賴 Repository；各層不自己 `new` 彼此，而是由容器安排。

## 示範與提示詞

示範專案跑起來後，可以用以下問題請 AI 助手解釋細節，加深對 Spring MVC 的理解（引自 u2 的 AI 提示詞練習）：

```text
「@RestController 與 @Controller 差別是什麼？如果改用 @Controller，我需要在哪裡加什麼才能讓回傳值變成 JSON？」

「為什麼 getById 回傳 ResponseEntity<Customer> 而不是直接回傳 Customer？兩種做法有什麼差別？」
```

## 口語稿

歡迎來到第二章。上一章我們把環境裝好了，專案骨架也建起來了，後端可以啟動、前端可以看到 API 的健康狀態。但你有沒有想過一個問題：當你在瀏覽器打一個網址，或者前端發一個請求給後端的時候，這個請求進到 Spring Boot 裡面，到底發生了什麼事？很多人寫了好幾年 Spring Boot，其實都答不出這題。而答不出這題的後果是什麼？就是出錯的時候不知道要去哪裡找問題——404 到底是路由沒對到，還是資料不存在？回傳的 JSON 格式怪怪的，是誰負責序列化的？這一節我們就把這條路徑一次走清楚。

先講 Spring Boot 本身。為什麼全世界的 Java 課程幾乎都用 Spring Boot 當起點？因為它把大量繁瑣的設定折疊起來了。它有兩個關鍵機制：第一個叫 Starter Dependencies，你不用一個一個去挑函式庫，而是以「情境」為單位引入依賴——我要做 Web 就引 web 這個 starter，之後要做資料庫就引 data-jpa。第二個叫自動配置，Auto-Configuration，它會根據你的 classpath 和設定，推導出合理的預設值，自動幫你建好常見的 Bean。這裡我要特別提醒：教學上最重要的，不是去背 Spring Boot 幫你做了哪些設定，而是理解它讓你省下了什麼樣的機械工作。省下來的時間要放在哪裡？放在模組責任的劃分和 API 的設計上，這才是工程師的價值。

好，那請求進來之後怎麼流動？Spring MVC 用的是一個叫「前端控制器」的設計模式，英文是 Front Controller。意思是：所有進來的 HTTP 請求，不管你打哪個網址，都會先經過同一個統一入口，這個入口叫做 DispatcherServlet。你可以把它想像成公司的總機——所有電話都先打到總機，總機再根據你要找誰，轉接到對應的分機。DispatcherServlet 做的事情就是：接到請求之後，根據 URL 和 HTTP 方法，找到對應的 Controller 方法，把請求轉過去。

我們拿一個具體例子來走一遍。前端發出 GET /api/customers，第一站是 DispatcherServlet；它看到這個路徑和 GET 方法，去比對哪個 Controller 上標了 @GetMapping("/api/customers")，找到之後就呼叫那個方法；Controller 接著呼叫 Service 拿資料——注意喔，這一章我們的資料先放在 Java 的 List 裡面，還不接資料庫，下一章才會換成真的 PostgreSQL；Service 把資料交回給 Controller，最後因為我們用的是 @RestController，Spring 會自動把回傳的 Java 物件序列化成 JSON 回給前端。這整條路對你來說幾乎是透明的，Spring Boot 的自動組態把一切都準備好了。這個設計的好處是：路由、例外處理、JSON 格式轉換這些「每支 API 都需要、但跟業務無關」的事情，全部由框架統一管理，你的 Controller 只要專心寫業務邏輯。

最後講兩個名詞，IoC 跟 DI，這兩個詞面試很愛考，但其實概念很生活化。IoC 是控制反轉，意思是物件的建立和生命週期，不再由你手動 new，而是交給 Spring 容器管理。DI 是依賴注入，容器在執行期把你需要的物件「塞」給你。實務上的樣子就是：Controller 依賴 Service，Service 之後會依賴 Repository，但這幾層彼此都不自己 new 對方，全部由容器安排。而在 Spring Boot 裡最推薦的注入方式是建構子注入——因為它保證物件一被建立，依賴就是完整的，而且之後寫測試會容易很多。

等一下示範專案跑起來之後，你可以拿兩個問題去問 AI 助手，加深理解：第一個，「@RestController 跟 @Controller 差在哪？如果改用 @Controller，要在哪裡加什麼才能讓回傳值變成 JSON？」第二個，「為什麼 getById 要回傳 ResponseEntity 而不是直接回傳 Customer？」這兩題的答案，會讓你對這一節講的請求流程有更立體的感覺。

總結一句：所有請求都先進 DispatcherServlet，再分發給 Controller，Controller 找 Service 拿資料，最後自動序列化成 JSON 回去。下一節，我們來談這些 API 的「長相」該怎麼設計——也就是 REST API 的設計原則。
