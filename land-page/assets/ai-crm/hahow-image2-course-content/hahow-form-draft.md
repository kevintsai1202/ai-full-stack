# Hahow 後台填寫草稿

> 來源：`land-page/index.html` 既有課程銷售頁內容整理。此檔用於複製到 Hahow creator 後台，不會被正式 landing page 引用。

## 課程標題

AI 賦能全端開發：從零打造企業級智慧應用

## 課程副標 / 一句話介紹

用一套 AI CRM 實戰專案，把 Spring Boot、Spring AI 與 React 串成可上線的企業級智慧應用。

## 課程摘要

這門課不是只示範如何呼叫 LLM API，而是帶你從零建立一套可操作、可驗證、可展示的企業級 AI CRM。你會從 Spring Boot 後端、PostgreSQL / JPA 資料層、JWT 權限保護、React CRM 工作台一路做到 Spring AI、SSE 串流、Tool Calling、RAG 知識庫與 Demo Day 端到端展示。

課程的重點是讓 AI 真的進入產品流程：AI 可以讀取 CRM 真實資料、引用知識庫文件、產生客戶風險摘要與下一步建議，同時保留工程系統該有的資料邊界、權限邊界與驗證流程。完課後，你會擁有一套能放進作品集的 AI 全端專案，而不是一個只會聊天的 demo。

## 適合對象

- 已具備 Java 基礎，想把 Spring Boot、React 與 AI 串成完整產品的工程師。
- 熟悉後端或全端開發，想理解 Spring AI、RAG、Tool Calling 如何落地在真實 JVM 系統的人。
- 想用 AI 輔助開發，但不想停在 Vibe Coding，而是想建立可維護、可驗收工程流程的人。
- 技術主管、接案工程師、產品型工程師，想快速整理一套企業 AI 應用作品與展示腳本的人。

## 你會學到

- 建立 Spring Boot 4 + React 19 的 full-stack monorepo。
- 設計 CRM domain model、REST API、DTO、Validation 與 Swagger 文件。
- 使用 JPA、PostgreSQL、Flyway 管理資料模型與種子資料。
- 實作 Spring Security + JWT，讓系統具備登入與權限保護。
- 建立 React CRM Dashboard、客戶列表、商機看板與 AI 助理介面。
- 使用 Spring AI、ChatClient、SSE 串流與 Tool Calling 串接 Java service。
- 使用 pgvector 建立 RAG 知識庫，讓 AI 回答能引用產品文件與銷售話術。
- 整理 Demo Day 腳本，從登入、客戶資料、AI 對話、RAG 查詢到 Swagger 完整展示。

## 完課後成果

你會完成一套可登入、可查客戶、可引用知識庫、可產生業務建議、可追蹤 AI 執行過程的企業級 AI CRM。這套作品可以作為履歷作品集、內部技術提案、企業 AI 導入 prototype 或接案展示素材。

## 課程內容章節

1. 開發環境、專案骨架與 AI 協作流程
2. Spring MVC、REST API 與 CRM Domain Modeling
3. JPA、PostgreSQL、Flyway 與資料查詢
4. Spring Security、JWT 與權限保護
5. React CRM 工作台與前後端整合
6. Spring AI、SSE 串流與 Tool Calling
7. RAG、pgvector、MCP 與知識庫擴充
8. Demo Day：從登入到 AI CRM 端到端驗收

## 建議上傳圖片

- 長方形封面：`cover-wide-title.webp`
- 方形封面：`cover-square-title.webp`
- 課程內容直式圖：`01-ai-crm-project-course.webp` ~ `04-demo-day-outcome-course.webp`
- 課程內容章節橫幅：`banner-01.webp` ~ `banner-08.webp`

## 詳細內容頁草稿

### 你是否也遇到這些問題？

你已經會寫 Spring Boot 或 React，但一談到 AI 應用，就只剩下「呼叫 API、把回應顯示在畫面上」。這樣的 demo 很快能做出來，卻很難放進真實產品：資料從哪裡來？權限怎麼控？AI 回答怎麼引用公司文件？使用者怎麼知道 AI 做了什麼？這些問題，才是企業導入 AI 時真正會卡住的地方。

這門課會用一套 AI CRM 專案，把這些問題一次串起來。你會從 Spring Boot 後端、React 前端、資料庫、JWT 權限開始，逐步加入 Spring AI、SSE 串流、Tool Calling、RAG 與 pgvector，最後整理成可以展示的 Demo Day 作品。

### 這門課不是只教 Vibe Coding

AI 可以加速開發，但不能取代工程判斷。課程會示範如何把需求拆成可驗收的任務，如何讓 AI 協助產生程式碼，如何用 API 測試、畫面檢查、Swagger、Agent Trace 與 Demo 腳本確認功能真的能跑。你學到的不只是工具操作，而是讓 AI 進入工程流程的方法。

### 課程專案：企業級 AI CRM 智慧業務助理

完課後，你會做出一套可登入、可查客戶、可看商機、可分析風險、可用 AI 產生下一步建議的 AI CRM。系統中會包含：

- React CRM Dashboard 與客戶列表
- Spring Boot REST API 與分層架構
- PostgreSQL / JPA / Flyway 資料模型
- Spring Security + JWT 登入與權限
- Spring AI ChatClient 與 SSE 串流回應
- Tool Calling 讀取 CRM 真實資料
- RAG + pgvector 知識庫引用文件來源
- Agent Trace 追蹤 AI 執行過程

### 8 大單元內容

#### 01 開發環境、專案骨架與 AI 協作流程

建立 JDK 21、Maven、Node、Docker 與 PowerShell 7+ 開發環境，完成 Spring Boot + React monorepo，並建立第一個健康檢查與 AI 協作流程。

#### 02 Spring MVC、REST API 與 CRM Domain Modeling

拆解 Customer、Contact、Interaction、Opportunity 等 CRM domain，建立 Controller、Service、DTO、Validation 與 Swagger 文件。

#### 03 JPA、PostgreSQL、Flyway 與資料查詢

用 JPA 管理資料模型，用 Flyway 建立 migration 與 seed data，讓後續報表、AI tool calling 與 RAG 都有真實資料可用。

#### 04 Spring Security、JWT 與權限保護

實作登入、JWT、角色權限與 API 保護，讓 AI CRM 不是公開 demo，而是具備企業系統基本安全邊界。

#### 05 React CRM 工作台與前後端整合

建立 Dashboard、客戶列表、客戶詳情、商機狀態與 AI 助理介面，讓前後端資料流完整串接。

#### 06 Spring AI、SSE 串流與 Tool Calling

用 Spring AI ChatClient 建立 AI 助理，透過 SSE 讓回應即時顯示，再用 Tool Calling 讓 AI 能讀取 Java service 的真實資料。

#### 07 RAG、pgvector、MCP 與知識庫擴充

把產品文件、服務條款、銷售話術整理成可檢索知識庫，讓 AI 回答能引用來源，而不是只依賴模型記憶。

#### 08 Demo Day：從登入到 AI CRM 端到端驗收

整理展示腳本，從登入、客戶資料、AI 對話、RAG 查詢到 Swagger 全流程跑通，形成可放進作品集的專案。

### 你會帶走什麼？

- 一套完整 AI CRM 專案
- 一套 Spring Boot + React + Spring AI 的實作架構
- 一套 AI 輔助開發的驗收流程
- 可放履歷、接案提案或內部技術分享的 Demo Day 腳本

### 誰適合這門課？

- 已有 Java / Spring Boot 基礎，想升級 AI 應用能力的工程師
- 熟悉前端或全端開發，想補齊企業級後端與 AI 整合的人
- 想用 AI 協助開發，但不想停在 prototype 的學習者
- 想做出可展示作品，而不是只看範例程式的人
