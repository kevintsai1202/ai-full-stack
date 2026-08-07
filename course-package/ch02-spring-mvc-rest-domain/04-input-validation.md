# 章節 2 單元 4｜輸入驗證

## 單元定位

上一節做出來的客戶 API 有一個明顯的漏洞：`create` 端點前端傳什麼就存什麼。本節解決「不能信任前端資料」的問題——用 Bean Validation（JSR-380）把驗證規則標在 Model 欄位上，Controller 加一個 `@Valid`，讓不合法的請求在進入業務邏輯之前就被擋下、回傳 400。下一節把視野拉高到整個 CRM 領域模型的設計。建議時長：12～15 分鐘。

## 教學素材

### 為什麼不能信任前端傳來的資料

目前的 `create` 端點直接把 `@RequestBody` 拿到的物件存進去，前端傳什麼就存什麼。這代表 `name` 傳空字串、`level` 傳不合法的值，全部都會寫入記憶體（或之後的資料庫）而不會報錯。

Bean Validation（JSR-380）讓你把驗證規則標在 Model 欄位上，Controller 只需加一個 `@Valid`，Spring 就會在呼叫 Service 之前自動驗證，不合法的請求直接回傳 400，完全不進入業務邏輯。

- 驗證規則標在 Model / DTO 欄位，不散落在 Service 或 Controller 各處
- 規則跟資料走：不管從哪個 Controller 端點傳入，同一套規則都生效
- 驗證失敗時 Spring 自動回傳 `400 Bad Request`，並帶上每個欄位的錯誤訊息

### 常用 Bean Validation 標註速查

所有標註都來自 `jakarta.validation.constraints` 套件，引入 `spring-boot-starter-validation` 即可使用。依驗證對象分組：

- **字串類**：`@NotBlank`（非空且非空白）、`@NotEmpty`（非空但可以全空白）、`@Size(min, max)`（長度範圍）、`@Email`（Email 格式）、`@Pattern(regexp)`（正規表示式）
- **數字類**：`@NotNull`（非 null）、`@Min(value)`（整數最小值）、`@Max(value)`（整數最大值）、`@DecimalMin`（含小數的最小值）、`@DecimalMax`（含小數的最大值）、`@Positive`（必須大於 0）、`@PositiveOrZero`（大於等於 0）
- **集合類**：`@NotEmpty`（集合不可空）、`@Size(min, max)`（集合元素數量範圍）
- **巢狀物件**：`@Valid` 標在欄位上 → 對該物件的欄位遞迴驗證（如 List 裡的每個元素）

## 示範與提示詞

驗證行為可延續上一節的示範專案操作：在 Customer 欄位標上驗證標註、`create` 端點加 `@Valid` 後，用 PowerShell `Invoke-RestMethod` 送出一筆 `name` 為空字串或 `email` 格式錯誤的請求，觀察 Spring 回傳的 `400 Bad Request` 與欄位錯誤訊息。

也可以搭配 u2 的 AI 提示詞練習，請 AI 助手解釋驗證前後的行為差異，例如：

```text
「為什麼 getById 回傳 ResponseEntity<Customer> 而不是直接回傳 Customer？兩種做法有什麼差別？」
```

## 口語稿

這一節我們來補一個大洞。上一節做完的 API，你有沒有試過這樣玩它：POST 一筆客戶，name 給空字串，email 給「abc」這種根本不是信箱的東西？你會發現——它照單全收，開開心心地存進去了。現在資料在記憶體裡看起來沒什麼大不了，但下一章接上資料庫之後，這些垃圾資料就會真的落地。再往後，第六章我們的 AI 助理要讀這些客戶資料給建議，你餵它垃圾，它就只能給你垃圾建議。所以這一節的主題只有一句話：永遠不要信任前端傳來的資料。

為什麼不能信任？因為你的 API 一旦上線，呼叫它的不一定是你自己寫的前端。可能是別人的程式、可能是測試工具、甚至可能是惡意的請求。前端的表單驗證做得再漂亮，都只是「使用者體驗」層級的防護，繞過它太容易了。真正的防線必須在後端。

那要怎麼防？最土法煉鋼的做法是在 Controller 或 Service 裡寫一堆 if：name 是不是空的、email 有沒有小老鼠。這樣寫的問題是，驗證邏輯會散落得到處都是，五個端點就要複製五份，改一條規則要找五個地方。Spring 給了我們優雅得多的解法，叫 Bean Validation，規格編號 JSR-380。它的思路是：驗證規則不寫在流程裡，而是直接「標」在資料模型的欄位上。name 欄位標一個 @NotBlank，email 欄位標一個 @Email，然後 Controller 的方法參數前面加一個 @Valid——就這樣，結束了。Spring 會在呼叫你的 Service 之前自動跑驗證，不合法的請求直接回 400 Bad Request，連帶告訴呼叫方是哪個欄位、錯在哪，而且完全不會進入你的業務邏輯。

這個設計有兩個很漂亮的性質。第一，規則跟著資料走：不管這個物件是從哪個端點傳進來的，同一套規則都生效，不會有「這個入口有驗、那個入口忘了驗」的漏洞。第二，關注點乾淨：Controller 不用寫防禦性的 if、Service 拿到的一定是通過驗證的資料，每一層都更單純。

實際使用上，先在 pom.xml 引入 spring-boot-starter-validation，所有標註都來自 jakarta.validation.constraints 這個套件。我帶你把常用的過一遍。字串類最常用的是 @NotBlank，非空而且不能全是空白——注意它跟 @NotEmpty 的差別，@NotEmpty 只要求不是空的，全空白字串是過得了的，所以名字這種欄位要用 @NotBlank。@Size 限制長度範圍，@Email 檢查信箱格式，@Pattern 可以上正規表示式。數字類有 @NotNull、@Min、@Max，小數用 @DecimalMin、@DecimalMax，還有 @Positive 要求必須大於零——之後商機金額這種欄位就用得上。集合類可以用 @NotEmpty 要求不可為空、@Size 限制元素數量。最後一個進階技巧：如果欄位本身是一個物件或一個 List，在欄位上標 @Valid，Spring 會遞迴進去，把裡面每個元素的規則也驗一遍。

我們現在來實際驗證一下。把 Customer 的 name 標上 @NotBlank、email 標上 @Email，Controller 的 create 方法參數加上 @Valid，重新啟動專案。然後用 PowerShell 的 Invoke-RestMethod 故意送一筆壞資料——name 給空字串。你會看到，這次它不再照單全收了，回來的是 400，而且回應內容清楚指出 name 這個欄位沒通過哪條規則。再送一筆正常的資料，201，順利建立。這一來一往，你的 API 就從「來者不拒」升級成「先驗明正身」。

一句話總結：驗證規則標在欄位上、Controller 加一個 @Valid，壞資料在門口就被擋下，永遠到不了你的業務邏輯。下一節是本章的收官——我們把視野從單一個 Customer 拉高，看整個 CRM 的領域模型該怎麼設計。
