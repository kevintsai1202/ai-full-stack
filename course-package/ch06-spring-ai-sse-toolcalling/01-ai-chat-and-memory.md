# 章節 6 單元 1｜AI 對話與記憶

## 單元定位

本節要解決的問題：如何在既有的 Spring Boot CRM 後端裡，建立第一個真正可用的 AI 對話入口——包含模型端點與金鑰配置、System Prompt 設計，以及用 sessionId 隔離不同使用者的對話記憶。

與前後節的銜接：前五章我們完成了一個可登入、可查客戶、有完整權限控管的全端 CRM；本節是「把 AI 真正接進系統」的第一步，先讓 AI 會說話、會記得上下文。下一節（單元 2）再讓它會查真實資料。

建議時長：25 ～ 30 分鐘。

## 教學素材

### ChatClient 在架構中的位置

`ChatClient` 是 Spring AI 2.0 中相對高階、可組合的入口，負責把使用者輸入、Advisor、Tools 與模型回應串成一條可讀的流程。教學上可以把它想成「客服總機」：總機本身不處理所有問題，但它知道該把輸入送到哪裡、要不要帶記憶、要不要叫工具。

### ChatClient.Builder 常用預設設定

- `defaultSystem(String)`：設定預設的系統提示詞（System Prompt），定義 AI 角色與回覆風格（例如「你是一個親切的 CRM 智慧助手」）。
- `defaultUser(String)`：設定預設的使用者輸入提示詞。
- `defaultOptions(ChatOptions)`：設定預設對話參數，例如溫度值（temperature）、使用的模型等。
- `defaultAdvisors(Advisor...)`：設定預設的攔截增強器，例如記憶元件（MessageChatMemoryAdvisor）或 RAG 元件（QuestionAnswerAdvisor），使所有對話自動啟用該功能。
- `defaultFunctions(String...)`：設定預設啟用的 Tool/Function Calling 工具 Bean 名稱。
- `defaultTools(Object...)` / `defaultToolCallbacks(ToolCallback...)`：設定預設啟用的工具物件或回呼。

### Spring AI 內建 Advisor

- `MessageChatMemoryAdvisor`：最常用的對話記憶 Advisor，自動讀取指定 sessionId 的對話歷史並注入上下文，回答後自動將新問答存回記憶庫。
- `PromptChatMemoryAdvisor`：文字範本式對話記憶，將歷史格式化為一段文字填入 Prompt 變數，適用於不支援多輪 Message 格式的特殊模型。
- `QuestionAnswerAdvisor`：RAG 檢索增強生成 Advisor，自動將提問向量化並至 VectorStore 檢索相關知識片段注入 Prompt，降低幻覺。
- `SimpleLoggerAdvisor`：日誌記錄 Advisor，調試時自動將完整 Prompt、參數配置與模型回應印到 Log。
- `VectorStoreChatMemoryAdvisor`：基於向量資料庫的持久化對話記憶，適合極大規模或長期對話歷史的語意檢索與儲存。
- `SafeGuardAdvisor`：內容安全防護 Advisor，在發送給 LLM 前或回應給用戶前攔截、過濾敏感詞。

### 模型端點與金鑰（Groq API 端點設定）

Groq 提供與 OpenAI 相容的 API 介面，因此可以直接使用 Spring AI 的 OpenAI Starter，只需把 Chat 端點的 base-url 覆寫為 Groq：

- Chat：前往 console.groq.com 建立免費 API Key，設定 `$env:GROQ_API_KEY="gsk_xxx..."`。
- Embedding：前往 voyageai.com 建立 API Key（每月 50M tokens 免費），設定 `$env:VOYAGE_API_KEY="pa-xxx..."`。
- Voyage AI 提供 OpenAI 相容介面，直接設定在 openai.embedding 區塊即可，不需要額外的 Starter 依賴。

```yaml
spring:
  ai:
    # OpenAI Starter 同時處理 Chat（Groq）與 Embedding（Voyage AI）
    openai:
      chat:
        base-url: https://api.groq.com/openai/v1   # 覆寫為 Groq 端點
        api-key: ${GROQ_API_KEY:your_groq_api_key}
        options:
# ... 完整程式碼請參考課程 GitHub 專案 ...
        options:
          model: voyage-3-lite  # 每月 50M tokens 免費，512 維度
```

### 串流輸出為什麼重要

對使用者來說，串流輸出最大的價值不是技術炫耀，而是「等待感受」不同：模型可以一邊生成一邊顯示，前端不需要等整段文字全部完成才開始呈現。本課程前端使用 SSE 讀取後端串流結果（單元 3 詳述）。

### 對話記憶設計原則

- 不同使用者應對應不同 `sessionId`。
- 記憶不應全站共用，否則會互相污染上下文。
- 清除對話時應一併重建 session 識別值。
- 進一步整合 Spring Security，以 Principal（登入者身分）作為隔離對話 Session 的依據。

## 示範與提示詞

### AI Agent 提示詞 — 建立 ChatClient 對話入口

```text
請在現有的 Spring Boot CRM 專案中加入 AI 對話功能

1. 請在現有的 Spring Boot CRM 專案中加入 AI 對話功能
2. 設定 Groq 的 base-url，模型用 openai/gpt-oss-120b，API Key 為 "xxxxxxx"。
3. 系統提示詞：「你是一個親切的 CRM 智慧助手」。
4. 使用 SSE串流回覆的API，並掛上記憶，以 sessionId 參數隔離不同使用者的對話記憶。
```

### 口語化建置提示詞 ①｜加上一個會聊天的 AI 智慧助手

> 回答即時逐字跳出，不同人的對話分開記住

```text
請幫這套系統加上一個 AI 智慧助手，讓我可以用「聊天」的方式問跟客戶有關的問題。回答要像打字一樣一個字一個字即時跳出來，而且不同使用者的對話要各自分開記住、不會混在一起。（串接 AI 用的金鑰請放在設定裡讀取，不要寫死在程式碼中。）請加中文註解。
```

## 口語稿

歡迎來到第六章，這一章可以說是整門課的分水嶺。先問你一個問題：如果只是寫一支程式去呼叫 ChatGPT 的 API，把回答印出來，這樣算不算「AI 應用」？老實說，那只是一個聊天玩具。它聊得再流暢，你問它「台積電這個客戶最近的商機金額是多少」，它只會一本正經地編一個數字給你，因為它根本看不到你資料庫裡的東西。企業要的不是會聊天的玩具，而是能讀企業真實數據、講的每個數字都有憑有據的助理。所以這一章的核心口訣，我會反覆講到你耳朵長繭：「數字由工具算、文字由模型寫，查無資料不亂答」。這條信任邊界，就是聊天玩具跟企業級 AI 應用的分界線。

回顧一下我們走到哪裡了。前五章，你已經有一套可以登入的全端 CRM：後端有 REST API、資料落在 PostgreSQL、有 JWT 權限控管，前端有 React 工作台可以查客戶。這一節，我們要把 AI 真正接進這套系統，第一步就是建立對話入口。

那要從哪裡接？Spring AI 2.0 給我們的高階入口叫 ChatClient。你可以把它想像成一個客服總機：總機自己不處理所有問題，但它知道使用者的話該送去哪裡、要不要帶上之前的對話記憶、要不要叫工具幫忙查資料。所有東西都掛在這條呼叫鏈上。建 ChatClient 的時候有幾個常用設定你要認得：defaultSystem 是系統提示詞，用來定義 AI 的角色——我們這裡就是「你是一個親切的 CRM 智慧助手」，之後信任邊界的規矩也是寫在這裡；defaultOptions 設定模型跟溫度；defaultAdvisors 掛上攔截增強器，最重要的就是 MessageChatMemoryAdvisor，它會自動把某個 sessionId 的歷史對話撈出來塞進上下文，回答完再自動存回去，記憶就是這樣來的。

模型從哪裡來？我們用 Groq，因為它提供跟 OpenAI 相容的 API，所以不用換依賴，直接用 Spring AI 的 OpenAI Starter，只要在 application.yml 把 chat 的 base-url 覆寫成 Groq 的端點就好。你先到 console.groq.com 建一個免費的 API Key，然後用環境變數 GROQ_API_KEY 設定進去。這裡有個工程紀律：金鑰一定放設定檔用環境變數讀取，絕對不要寫死在程式碼裡，不然哪天推上 GitHub 就是資安事故。順帶一提，之後第七章要用的 Embedding 我們走 Voyage AI，它每個月有五千萬 tokens 免費額度，一樣是 OpenAI 相容介面，直接設定在 embedding 區塊。

再來是記憶的設計，這裡有三條鐵律。第一，不同使用者要對應不同 sessionId——想像一下業務 A 在跟 AI 討論客戶的報價策略，業務 B 一問話竟然看得到 A 的上下文，這在企業裡是災難。第二，記憶絕對不能全站共用，不然上下文會互相污染，AI 會把不同人的對話攪在一起亂答。第三，使用者按「清除對話」的時候，session 識別值要一併重建，不是只把畫面清空。而且我們後面會更進一步，直接整合 Spring Security，用登入者的 Principal 來當隔離依據，這樣連 sessionId 被亂傳的空間都沒有。

我們現在來實際做一次。把講義裡那段 AI Agent 提示詞複製給你的 AI Agent：在現有 CRM 專案加入 AI 對話功能、設定 Groq 的 base-url、模型用 openai/gpt-oss-120b、系統提示詞是親切的 CRM 智慧助手、用 SSE 串流回覆並掛上記憶、以 sessionId 隔離對話。跑完之後你會看到專案多了一個對話 endpoint。啟動起來測試：先自我介紹「我叫凱文」，再問「我剛剛說我叫什麼？」——你會看到 AI 記得你的名字；然後換一個 sessionId 再問一次，它就不認識你了。這就證明記憶有掛上、而且是隔離的。

總結一句：這一節我們用 ChatClient 建好了對話總機，掛上了以 sessionId 隔離的記憶。但現在的它還是個「只會講話」的助理，你問它客戶資料它還是會瞎編。下一節，我們就用 Tool Calling 讓它把手伸進真實的資料庫——記住口訣，數字由工具算、文字由模型寫。
