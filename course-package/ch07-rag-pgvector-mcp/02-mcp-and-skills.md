# 章節 7 單元 2｜MCP 與 Skills 擴充

## 單元定位

上一節 AI 學會了引用公司文件，加上第六章的 tool calling，我們的助理已經「查得到數字、讀得到文件」。但企業裡還有一類需求跨出應用程式本身：安排行事曆、產生 Email 草稿、匯出報表。本節介紹 MCP（Model Context Protocol）作為 AI 接上外部工具的標準介面、Skills 作為打包工作流程知識的開放標準，並建立最重要的選型判斷：什麼時候用 domain tool、什麼時候用 RAG、什麼時候才需要 MCP。本節屬於進階選修性質，重點在觀念與選型，實作可先用模擬版。建議時長：15～20 分鐘。

## 教學素材

### MCP 應該怎麼理解：AI 世界的標準插座

MCP 可以想成 AI 世界的標準插座。當系統中有多個模型平台、IDE 工具與資料來源時，MCP 提供一致的方式描述工具與上下文。

它的價值不是取代應用程式內部工具呼叫，而是讓不同 AI 客戶端可以以標準方式接入同一批能力。

- Client 是 AI 平台或整合工具
- Host / Server 提供工具與上下文能力
- Model 專注在理解與生成

### AI 呼叫遠端工具的完整流程

以「有哪些客戶？」為例，說明從使用者輸入到 AI 回答的跨服務執行路徑：

- ① 使用者呼叫 `GET http://localhost:8081/api/mcp/chat?message=有哪些客戶`
- ② MCP Client（port 8081）的 ChatClient 收到訊息，交由 LLM 判斷意圖
- ③ AI 判斷需要查客戶，透過已建立的 SSE 連線，向 MCP Server（port 8080）發送 getCustomers 工具呼叫請求
- ④ MCP Server 執行 `CustomerTools.getCustomers("")` → 查詢 PostgreSQL → 回傳客戶 JSON
- ⑤ MCP Client 收到工具執行結果，AI 組合成自然語言回答
- ⑥ 使用者收到：「目前共有 5 筆客戶：1. 台積電（VIP）...」

### MCP 適用場景與選型：domain tool / RAG / MCP

依 Hahow 課程大綱的定位，MCP 的典型適用場景是跨系統的外部能力：安排行事曆、產生 Email 草稿、匯出生意報表。選型的判斷方式：

- 查詢自家資料庫、動自家業務邏輯 → 用第六章的 domain tool（tool calling），最直接、延遲最低
- 回答要依據公司文件知識、附上來源 → 用本章的 RAG 知識庫
- 要接上外部系統或讓多個 AI 客戶端共用同一批工具 → 才考慮 MCP

### Skills 是什麼：把專業知識打包給 AI

Skills（Agent Skills）是 Anthropic 於 2025 年提出的開放標準：把某個領域的程序性知識（操作流程、規範、範本、輔助腳本）打包成一個資料夾，核心是一份帶有名稱與描述的 SKILL.md 說明檔。AI 平時只看到每個 Skill 的一行描述，判斷相關時才載入完整內容，這種「漸進式載入」（progressive disclosure）讓模型能掛上大量專業知識而不撐爆上下文。

如果說 Tool Calling 與 MCP 解決的是「AI 能呼叫什麼工具、拿到什麼資料」，Skills 解決的則是「AI 應該照什麼流程與規範做事」。兩者互補，不是替代關係。

- Skill = 資料夾 + SKILL.md（frontmatter 描述）+ 選配的範本與腳本
- 平時只載入描述，被點名才載入全文
- MCP 擴充「能力與資料」，Skills 擴充「知識與流程」

### MCP vs Skills：擴充 AI 能力的兩條路

- MCP：標準化「連接」— 讓 AI 客戶端以一致方式接上工具與資料來源，重點在執行能力
- Skills：標準化「知識」— 讓 AI 依描述按需載入工作流程與規範，重點在做事方法
- 判斷方式：要讓 AI「查得到、做得到」用 MCP / Tool Calling；要讓 AI「做得對、有章法」用 Skills
- 實務上常見組合：Skill 內的流程指示 AI 在特定步驟呼叫 MCP 工具完成查詢或寫入

### Spring AI 如何加入 Skills：spring-ai-agent-utils

Skills 是 Claude、Claude Code 等 AI 客戶端原生支援的標準，但 Spring AI 核心框架尚未內建 Skills 概念。在 Spring Boot 應用中要讓 AI 具備 Skills 能力，目前的推薦做法是引用 Spring AI Community（Spring AI 官方社群組織）維護的 spring-ai-agent-utils 套件——它把 Claude Code 風格的 Agent 工具與 Skills 機制帶進 Java 應用。

版本注意：spring-ai-agent-utils 0.9.0 要求 Spring AI 2.0.0-RC1 以上、Java 17+、Spring Boot 3.x / 4.x。本課程專案使用 Spring AI 2.0.0-M8，導入前需先把 Spring AI 升到 RC1 以上版本。

- 路線一（推薦）：引用 spring-ai-agent-utils 的 SkillsTool，以 Markdown + YAML frontmatter 定義可重用知識模組
- 路線二：自行實作最小核心——掃描 skills/ 目錄、解析 SKILL.md、把描述注入 system prompt（原理與套件相同）
- Skill 檔案本身是純 Markdown、與平台無關，可在 Claude Code 與 Spring Boot 應用間共用同一份

## 示範與提示詞

**口語化任務提示詞 —（選修）讓 AI 接上外部工具［build］**

```text
（這題是進階選修，行有餘力再做）請讓 AI 助手能接上一些外部工具，例如：幫忙安排行事曆、產生 Email 草稿、匯出生意報表（先用模擬的版本就好）。讓 AI 在對話中除了查公司內部資料，也能順手幫我做這些跨系統的小事。請加中文註解。
```

## 口語稿

上一節結束的時候，我們的 AI 助理已經有兩把刷子了：第六章的 tool calling 讓它查得到資料庫的數字，上一節的 RAG 讓它讀得到公司文件、答題還附來源。那你可能會想，這樣是不是就夠了？我們來看幾個業務實際會提的需求：「幫我跟這個客戶約下週三下午的會，放進我的行事曆」、「幫我起草一封跟進的 Email」、「把這一季的生意數字匯出成報表」。你發現了嗎？這些事情的共同點是——它們都跨出了我們這個 CRM 應用本身，要去碰行事曆系統、郵件系統、報表工具。這就是這一節要聊的主題：MCP。

MCP，Model Context Protocol，我最推薦的理解方式是「AI 世界的標準插座」。想像一下沒有標準插座的世界：每個電器都有自己的接頭，每換一個牌子就要重拉一次線。AI 工具的世界本來就是這樣——每個 AI 平台接工具的方式都不一樣，你為 A 平台寫的整合，換到 B 平台要重寫一遍。MCP 做的事，就是定義一個標準：工具怎麼描述自己、上下文怎麼傳遞，讓不同的 AI 客戶端都能用同一種方式，接上同一批能力。注意我的用詞——它的價值不是取代你應用程式內部的工具呼叫，而是讓「多個 AI 客戶端共用同一批工具」變得可能。

角色分工很簡單：Client 是 AI 平台或整合工具那一端；Server 提供工具與上下文能力；Model 就專心做它擅長的理解與生成。我帶你走一遍完整的呼叫流程，用「有哪些客戶」當例子。使用者打到 8081 埠的 MCP Client，ChatClient 收到訊息交給 LLM 判斷意圖；AI 判斷需要查客戶，透過已經建立的 SSE 連線，向 8080 埠的 MCP Server 發出 getCustomers 的工具呼叫請求；Server 執行 CustomerTools 查 PostgreSQL、回傳客戶 JSON；Client 收到結果，AI 組成自然語言回覆：「目前共有 5 筆客戶」。你會發現這個流程跟第六章的 tool calling 長得很像，差別只在工具從「同一個應用程式裡面」搬到了「另一個服務上」，中間用標準協定溝通。

那問題來了，什麼時候該用哪一個？這是本節最重要的選型判斷，我給你三條線：第一，查自家資料庫、動自家業務邏輯，用第六章的 domain tool 就好，最直接、延遲最低，不要為了用 MCP 而用 MCP。第二，回答需要依據公司文件、要附來源的，走 RAG 知識庫。第三，真的要接外部系統——行事曆、Email、報表匯出——或者你有多個 AI 客戶端要共用同一批工具，這時候 MCP 才登場。

接著講另一個常常跟 MCP 一起被提起的東西：Skills。Skills 是 Anthropic 在 2025 年提出的開放標準，做法是把某個領域的程序性知識——操作流程、規範、範本、輔助腳本——打包成一個資料夾，核心是一份 SKILL.md。它聰明的地方在於漸進式載入：AI 平時只看到每個 Skill 的一行描述，判斷跟當前任務相關時，才把完整內容載進來，所以你可以掛上大量的專業知識而不會撐爆上下文。跟 MCP 怎麼區分？一句話：MCP 擴充的是「能力與資料」，讓 AI 查得到、做得到；Skills 擴充的是「知識與流程」，讓 AI 做得對、有章法。兩者是互補，實務上常見的組合是 Skill 裡的流程指示 AI 在某個步驟去呼叫 MCP 工具。

那 Spring Boot 應用怎麼加 Skills？Spring AI 核心目前還沒內建這個概念，推薦做法是用 Spring AI 官方社群維護的 spring-ai-agent-utils 套件。這裡有個版本地雷要先講：0.9.0 版要求 Spring AI 2.0.0-RC1 以上，而我們專案目前用的是 M8，導入前要先升版。如果你不想引套件，也可以自己實作最小核心——掃描 skills 目錄、解析 SKILL.md、把描述注入 system prompt，原理跟套件一模一樣。而且 Skill 檔案就是純 Markdown，跟平台無關，同一份可以在 Claude Code 和你的 Spring Boot 應用之間共用。

最後提醒，這一節的實作是進階選修，提示詞裡也寫明了外部工具先用模擬版就好，行有餘力再接真的。總結一下：MCP 是 AI 接外部工具的標準插座，Skills 是打包工作流程的知識模組，選型口訣是——自家資料用 domain tool、文件知識用 RAG、跨系統與共用才上 MCP。下一節我們回到 RAG 的延伸戰場：讓 AI 把對話歷史也記進向量庫，擁有跨對話的長期記憶。
