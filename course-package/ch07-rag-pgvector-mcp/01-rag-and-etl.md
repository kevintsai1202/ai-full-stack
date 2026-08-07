# 章節 7 單元 1｜RAG 原理與 ETL 流程

## 單元定位

上一章的 AI 助理已經能透過 tool calling 查詢資料庫、回答「有幾個客戶」這種數字問題，但一問到「客戶服務規範怎麼規定」這類文件知識，它就只能靠模型訓練時的記憶硬答，甚至憑空編造。本節要解決的就是這個問題：釐清 RAG 解決了 LLM 的哪些盲區（時效性、企業內部知識）、又有什麼限制，並掌握文件進向量庫的 Extract → Transform → Load 三步驟，用 pgvector 建立第一版可上傳文件、會附上來源的知識庫。這是本章後續長期記憶與 CRM 知識庫設計的地基。建議時長：20～25 分鐘。

## 教學素材

### RAG 的基本想法：開卷考試

RAG 可以把它想成「開卷考試」。模型不再只依賴自己訓練時學過的內容，而是先去找與問題最相關的文檔片段，再根據那些片段作答。

這種做法的好處是知識更新成本低、可控性高，而且可以明確限制回答只依據文件內容——這正是「無相關文件時 AI 不硬答、回答附上來源」的技術基礎。

### 向量嵌入與 pgvector

Embedding 會把文字轉成固定長度的向量。在向量空間中，語意越接近，距離就越近。這讓資料庫可以用「相似度」而不是精確關鍵字比對來找內容。

架構上的分工：`pgvector` 是 PostgreSQL 的擴充套件，負責提供向量欄位與最近鄰搜尋能力；`VectorStore` 則是 Spring AI 在程式中的抽象介面。

### RAG vs Fine-Tuning：為什麼企業文件場景選 RAG

選擇 RAG 還是 Fine-Tuning，取決於資料更新頻率、幻覺容忍度與開發成本。對大多數企業文件場景，RAG 是首選。

```text
                    RAG（本課程方案）        Fine-Tuning（微調）
──────────────────────────────────────────────────────────
本質               外部知識檢索（開卷考試）   內部參數調整（閉卷考試）
即時更新            極快（只需更新向量庫）     極慢（需要重新訓練）
幻覺控制            極佳（可強制依文件回答）   較差（仍可能胡言亂語）
開發成本            低                        高
適用場景            FAQ、內規、產品文件        垂直領域語言風格微調
```

### ETL 三步驟：文件到向量庫

上傳文件到向量庫要經過 Extract → Transform → Load 三步驟。`TokenTextSplitter` 把長文切成小塊，讓每塊語意聚焦，檢索時相關度才會精準。

- Extract — 用 `TextReader` 讀取檔案，封裝成 `Document` 物件
- Transform — 用 `TokenTextSplitter` 切分，預設每塊約 800 tokens，相鄰塊重疊 100 tokens 防止語意被截斷
- Load — `vectorStore.accept()` 自動呼叫 EmbeddingModel 產生向量並寫入 PostgreSQL

```java
// RAGController.java — 文件上傳 ETL 流程
@PostMapping("/upload")
public String uploadDocument(@RequestParam("file") MultipartFile file) {
    // Extract：讀取檔案為 Document 清單
    Resource resource = new ByteArrayResource(file.getBytes());
    List<Document> documents = new TextReader(resource).get();

    // Transform：切分長文，避免 Embedding 向量資訊過度稀釋
    TokenTextSplitter splitter = new TokenTextSplitter();
    List<Document> splitDocuments = splitter.apply(documents);

    // Load：向量化並寫入 pgvector
    vectorStore.accept(splitDocuments);

    return "已上傳並完成向量化，共 " + splitDocuments.size() + " 個片段";
}
```

## 示範與提示詞

**AI Agent 提示詞 — 建立 RAG 知識庫**

```text
請為專案加入 RAG 知識庫功能，使用 PostgreSQL pgvector 儲存向量：
1. 在 pom.xml 加入 spring-ai-starter-vector-store-pgvector 依賴，並確認 application.yml 已設定 Voyage AI 的 embedding 端點（環境變數 VOYAGE_API_KEY）。
2. 建立 RAGController，提供兩支 API：
   - POST /api/rag/upload：接收上傳的文字檔，用 TextReader 讀取、TokenTextSplitter 切分成小段，最後用 vectorStore.accept() 向量化寫入資料庫
   - GET /api/rag/query：掛上 QuestionAnswerAdvisor，讓 AI 先檢索相關文件片段再回答
3. 程式碼需有中文函式註解。
完成後請示範驗證流程：先上傳一份「客戶服務規範」文件，再提問「客戶服務規範是什麼？」，確認 AI 是根據文件內容回答，而不是自由發揮。
```

**口語化任務提示詞 — 建立可上傳文件的 RAG 知識庫［build］**

```text
請幫我建立一個「知識庫」：我可以上傳公司的文件（例如客戶服務規範、產品手冊、銷售話術範本）。之後當我問相關問題時，AI 要先去這些文件裡找出最相關的段落，根據文件內容來回答，而不是自由發揮亂講；回答時也要告訴我「這是參考哪一份文件」。完成後我上傳一份『客戶服務規範』，再問相關問題，AI 就會依文件內容回答。請加中文註解。
```

## 口語稿

歡迎來到章節七。先回顧一下我們走到哪裡了：上一章，我們的 AI CRM 助理已經會用 tool calling 去查資料庫，你問它「目前有幾個高風險客戶」，它會真的去撈數字回來，而不是瞎掰。但是我要你想一個新的問題——如果業務問它：「我們公司的客戶服務規範，退貨要在幾天內處理？」它答得出來嗎？答不出來。因為這份規範存在你公司的 Word 檔裡，模型訓練的時候根本沒看過。更糟的是，很多時候它不會老實說「我不知道」，而是一本正經地編一個聽起來很合理的答案。這就是大型語言模型的兩個先天盲區：第一，時效性，它的知識停在訓練截止那一天；第二，企業內部知識，你公司的內規、產品手冊、銷售話術，它一個字都沒讀過。

那怎麼辦？這一節的主角就是 RAG，檢索增強生成。我最喜歡的比喻是「開卷考試」。以前模型是閉卷考，全靠腦袋裡的記憶答題，記錯了就是幻覺；RAG 是讓它開卷考——回答之前，先去你的文件堆裡找出跟問題最相關的幾段內容，然後根據那幾段來作答。這樣做有三個好處：知識更新成本低，換文件就好，不用重新訓練模型；可控性高，你可以明確要求它只根據文件內容回答；而且它可以告訴你答案是參考哪一份文件——這一點對企業應用超級重要，因為主管要的不是一個很會講話的 AI，而是一個講得出依據的 AI。

你可能會問，那為什麼不做 Fine-Tuning、直接把公司知識訓練進模型裡？我們比較過了：微調是動模型的內部參數，更新極慢，文件改一版就要重訓一次，成本高，而且幻覺控制反而比較差。RAG 是外部知識檢索，更新極快，只要更新向量庫就好。所以對 FAQ、內規、產品文件這類企業場景，RAG 是首選；Fine-Tuning 留給垂直領域語言風格那種特殊需求。

接下來講「怎麼做」。核心技術叫 Embedding，向量嵌入。它會把一段文字轉成一串固定長度的數字，也就是向量。神奇的地方在於：語意越接近的文字，向量在空間中的距離就越近。「退貨流程」和「客戶要求退款怎麼辦」字面上沒幾個字重疊，但向量距離很近。這讓資料庫可以用「相似度」而不是關鍵字比對來找內容。那向量存哪裡？還記得第三章我們選資料庫映像的時候，特地選了帶 pgvector 的 PostgreSQL 嗎？就是為了今天。這裡的分工要分清楚：pgvector 是資料庫端的擴充套件，負責存向量、做最近鄰搜尋；VectorStore 是 Spring AI 在程式端的抽象介面。一個管儲存，一個管程式怎麼呼叫。

文件要進向量庫，走的是 ETL 三步驟。Extract，用 TextReader 把檔案讀成 Document 物件；Transform，用 TokenTextSplitter 把長文切成小塊，預設每塊大約八百個 token，相鄰的塊還會重疊一百個 token，避免一句話剛好被切斷、語意斷在邊界上。為什麼要切？因為一整份幾十頁的文件如果壓成一個向量，語意會被稀釋得什麼都像、什麼都不像；切成小塊，每塊語意聚焦，檢索相關度才會精準。最後是 Load，呼叫 vectorStore.accept()，它會自動呼叫 EmbeddingModel 產生向量、寫進 PostgreSQL。

我們現在來實際做。把提示詞交給 AI Agent，請它加入 pgvector 的依賴、建立 RAGController，提供兩支 API：一支 upload 負責上傳文件走 ETL，一支 query 掛上 QuestionAnswerAdvisor，讓 AI 回答前先檢索。完成之後你會看到驗證流程：先上傳一份「客戶服務規範」，再問「客戶服務規範是什麼？」，這時候 AI 的回答會明顯貼著文件內容走，而且標出參考來源。還有一個反向驗證一樣重要——問一個文件裡根本沒有的問題，看它會不會老實說知識庫裡找不到相關內容，而不是硬答。記住這兩個驗收重點：有文件，答案要附來源；沒文件，不能硬答。

總結一下：這一節我們把 AI 從閉卷考帶進開卷考，用 ETL 三步驟把公司文件切塊、向量化、存進 pgvector，讓 AI 依文件作答並標出來源。下一節我們換一個方向——除了讓 AI「讀得到文件」，還要讓它「接得上外部工具」，我們來聊 MCP 與 Skills。
