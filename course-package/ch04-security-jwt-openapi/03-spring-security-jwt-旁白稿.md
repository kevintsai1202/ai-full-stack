# 03-spring-security-jwt｜Spring Security 及 JWT 認證旁白稿

本稿依據 `03-spring-security-jwt.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜裸奔的 API

建議檔名：`01-unprotected-api.wav`

好，來到這一章最重要的一節。我先問你一個問題：現在你的客戶 API，如果我拿到網址，我能不能把你資料庫裡的客戶全部刪光？答案是可以，而且不需要任何身分。這就是 demo 跟正式系統最赤裸裸的差距——正式系統的第一條防線就是：沒登入的人，什麼都不能做。

## 02｜認證與授權

建議檔名：`02-authn-authz.wav`

要搞懂 API 安全，先分清楚兩個詞。Authentication，認證，回答的是「你是誰」——你用帳號密碼登入，系統確認你的身分。Authorization，授權，回答的是「你能做什麼」——同樣是登入的人，管理員能刪客戶，業務人員只能查詢和編輯。這兩個詞整章會一直出現，認證是門禁，授權是門禁後面的房間鑰匙。

## 03｜JWT 三段式與無狀態

建議檔名：`03-jwt-stateless.wav`

那身分要怎麼在每次請求之間傳遞？傳統做法是 Session，伺服器記住每個登入的人。但我們要做的是無狀態認證，用的是 JWT。JWT 長什麼樣子？三段字串用點連起來。第一段 Header，宣告簽章演算法；第二段 Payload，放你的帳號、角色、過期時間；第三段 Signature，是伺服器用密鑰對前兩段做的簽章。關鍵就在這個簽章：任何人只要改動前兩段的任何一個字，簽章就對不起來，伺服器立刻知道這是假的。所以伺服器收到 Token，只要驗簽章、看沒過期，就能信任裡面寫的身分和角色，完全不用查資料庫、不用記 Session。這就是「無狀態」，好處是以後開十台伺服器也不用共享 Session。但有兩件事你一定要記住：第一，Payload 只是 Base64 編碼、不是加密，任何人都解得開來看，所以絕對不要把密碼放進去；第二，密鑰要從環境變數讀，不要寫死在程式碼裡。

## 04｜五大零件心智模型

建議檔名：`04-five-parts.wav`

概念懂了，實作怎麼做？這一段我們交給 AI，但是——這正是這門課一直強調的——你要先有心智模型，才有能力核對 AI 的產物。Spring Security 加 JWT 總共就五個零件。第一，JwtUtils，用 jjwt 套件負責簽發和解析 Token。第二，登入端點，POST /api/auth/login，收帳密、驗證成功就把角色寫進 Payload 簽發 Token，這是整條認證鏈唯一不用帶 Token 的入口。第三，JWT 驗證過濾器，攔截每一個進來的請求，從 Authorization: Bearer 標頭取出 Token 驗章，成功就把身分放進 SecurityContext。第四，SecurityFilterChain，集中設定規則：關掉 Session 改成 STATELESS、放行登入和 Swagger、其他一律要驗證，然後把過濾器掛進鏈裡。第五，角色授權，刪除客戶必須 ADMIN，可以用 @PreAuthorize 或在設定鏈裡依路由限制。

## 05｜交給 AI，你負責核對

建議檔名：`05-ai-implementation.wav`

這五個零件記熟，AI 少做哪個你一眼就看得出來：少了過濾器，你帶了 Token 還是被擋；少了無狀態設定，系統會莫名其妙長出 Session；少了角色限制，一般使用者也刪得掉資料。我們現在來實際跑一次。把課程提供的提示詞丟給 AI Agent——完整原文在教學網站上，不用抄——它會引入 Security 和 jjwt 依賴，把這五個零件全部生出來。

## 06｜在 Swagger 上驗證：401、403、204

建議檔名：`06-swagger-verify.wav`

跑完之後重啟應用，來驗證——這是本節最過癮的部分。先不帶 Token 呼叫客戶查詢 API，你會看到 401，被擋在門外了，這是好事。接著打開 Swagger UI，因為文件也被保護了，瀏覽器會先跳出登入框，輸入 admin 和 password 進去。然後展開 /api/auth/login，用 user 帳號執行，把回傳的 token 複製起來，點頁面最上方的 Authorize 按鈕，貼進 BearerAuth 欄位。現在你是「一般使用者」的身分了——查詢客戶，成功；試著刪除客戶，你會看到 403 Forbidden，被角色權限擋下來了。最後換成 admin 的 Token 重新 Authorize，再刪一次——204 No Content，刪掉了。401、403、204，這三個狀態碼跑一輪，你的認證和授權就都驗證完了。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結：這一節我們用五個零件把 API 從「裸奔」變成「有門禁、有房間鑰匙」的受保護系統，而且全程在 Swagger 上視覺化驗證。不過 ADMIN 和 USER 兩種角色對真實的 CRM 來說還太粗糙——業務看得到誰的客戶？主管看得到什麼報表？下一節我們就來設計 AI CRM 真正的權限模型。我們下一節見。

## 邊念邊操作提示卡

1. 先畫 login 到 protected API 的箭頭，念出 public route、token 簽發與驗證位置，再打開 SecurityFilterChain。
2. 不帶 token 呼叫受保護 endpoint，停在 401；再貼有效 Bearer token 呼叫，讓畫面出現成功 response。
3. 依序展示錯誤簽章、過期 token 和不足權限，清楚區分 401 與 403；claims 畫面只保留非敏感內容。
4. 收尾保存 auth flow 和 HTTP 證據，提醒下一單元會測試資源級授權。

## 交付檔案命名

```text
01-unprotected-api.wav
02-authn-authz.wav
03-jwt-stateless.wav
04-five-parts.wav
05-ai-implementation.wav
06-swagger-verify.wav
07-closing.wav
```
