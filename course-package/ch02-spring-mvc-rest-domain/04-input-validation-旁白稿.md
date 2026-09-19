# 04-input-validation｜輸入驗證旁白稿

本稿依據 `04-input-validation.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜這支 API 有個大洞

建議檔名：`01-the-hole.wav`

這一節我們來補一個大洞。上一節做完的 API，你有沒有試過這樣玩它：POST 一筆客戶，name 給空字串，email 給「abc」這種根本不是信箱的東西？你會發現——它照單全收，開開心心地存進去了。

現在資料在記憶體裡看起來沒什麼大不了，但下一章接上資料庫之後，這些垃圾資料就會真的落地。再往後，第六章我們的 AI 助理要讀這些客戶資料給建議，你餵它垃圾，它就只能給你垃圾建議。所以這一節的主題只有一句話：永遠不要信任前端傳來的資料。

## 02｜為什麼前端驗證不夠

建議檔名：`02-why-backend.wav`

為什麼不能信任？因為你的 API 一旦上線，呼叫它的不一定是你自己寫的前端。可能是別人的程式、可能是測試工具、甚至可能是惡意的請求。

前端的表單驗證做得再漂亮，都只是「使用者體驗」層級的防護，繞過它太容易了。真正的防線，必須在後端。

## 03｜Bean Validation：規則標在欄位上

建議檔名：`03-bean-validation.wav`

那要怎麼防？最土法煉鋼的做法是在 Controller 或 Service 裡寫一堆 if：name 是不是空的、email 有沒有小老鼠。問題是驗證邏輯會散落得到處都是，五個端點就要複製五份，改一條規則要找五個地方。

Spring 給了我們優雅得多的解法，叫 Bean Validation，規格編號 JSR-380。它的思路是：驗證規則不寫在流程裡，而是直接「標」在資料模型的欄位上。name 欄位標一個 @NotBlank，email 欄位標一個 @Email，然後 Controller 的方法參數前面加一個 @Valid——就這樣，結束了。Spring 會在呼叫 Service 之前自動跑驗證，不合法的請求直接回 400 Bad Request，連帶告訴呼叫方是哪個欄位、錯在哪，完全不會進入你的業務邏輯。

## 04｜這個設計漂亮在哪

建議檔名：`04-design-benefits.wav`

這個設計有兩個很漂亮的性質。第一，規則跟著資料走：不管這個物件是從哪個端點傳進來的，同一套規則都生效，不會有「這個入口有驗、那個入口忘了驗」的漏洞。第二，關注點乾淨：Controller 不用寫防禦性的 if，Service 拿到的一定是通過驗證的資料，每一層都更單純。

你發現了嗎？這其實是上一節分層精神的延續——驗證這件事也有它該待的位置。

## 05｜常用標註走一遍

建議檔名：`05-annotations.wav`

實際使用上，先在 pom.xml 引入 spring-boot-starter-validation，所有標註都來自 jakarta.validation.constraints 這個套件。完整速查表在教學網站上，不用抄，我這裡帶你過重點。

字串類最常用的是 @NotBlank，非空而且不能全是空白——注意它跟 @NotEmpty 的差別，@NotEmpty 只要求不是空的，全空白字串是過得了的，所以名字這種欄位要用 @NotBlank。@Size 限制長度範圍，@Email 檢查信箱格式，@Pattern 可以上正規表示式。數字類有 @NotNull、@Min、@Max，小數用 @DecimalMin、@DecimalMax，還有 @Positive 要求必須大於零——之後商機金額這種欄位就用得上。集合類可以用 @NotEmpty 和 @Size。

最後一個進階技巧：如果欄位本身是一個物件或一個 List，在欄位上標 @Valid，Spring 會遞迴進去，把裡面每個元素的規則也驗一遍。

## 06｜故意送壞資料驗證看看

建議檔名：`06-test-validation.wav`

我們現在來實際驗證一下。把 Customer 的 name 標上 @NotBlank、email 標上 @Email，Controller 的 create 方法參數加上 @Valid，重新啟動專案。

然後用 PowerShell 的 Invoke-RestMethod 故意送一筆壞資料——name 給空字串。你會看到，這次它不再照單全收了，回來的是 400，而且回應內容清楚指出 name 這個欄位沒通過哪條規則。再送一筆正常的資料，201，順利建立。

這一來一往，你的 API 就從「來者不拒」升級成「先驗明正身」。驗收標準很明確：壞資料回 400、好資料回 201，兩個都看到才算過關。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

一句話總結：驗證規則標在欄位上、Controller 加一個 @Valid，壞資料在門口就被擋下，永遠到不了你的業務邏輯。

下一節是本章的收官——我們把視野從單一個 Customer 拉高，看整個 CRM 的領域模型該怎麼設計。我們下一節見。

## 邊念邊操作提示卡

1. 先在畫面左側顯示欄位規則表，再打開 request DTO，逐一圈出 `@NotBlank`、`@Size` 或 `@Email` 與規則的對應。
2. 送出一筆正常 JSON，接著一次只改一個錯誤；每次收到 400 後停下來對照欄位名稱和訊息，不要只報「驗證失敗」。
3. 切到錯誤處理程式時，說明 response 不應含 stack trace、密碼或內部類別；再展示測試輸出。
4. 結尾提醒前端驗證只是體驗，真正防線在 API 邊界，並保存 request、response 和測試檔案。

## 交付檔案命名

```text
01-the-hole.wav
02-why-backend.wav
03-bean-validation.wav
04-design-benefits.wav
05-annotations.wav
06-test-validation.wav
07-closing.wav
```
