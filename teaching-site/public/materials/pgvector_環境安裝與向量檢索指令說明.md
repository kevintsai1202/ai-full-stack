# pgvector 環境安裝與向量檢索指令說明

本講義為「AI 賦能全端開發」課程的向量搜尋與 RAG 檢索教材。

## 什麼是 pgvector？
`pgvector` 是一個開源的 PostgreSQL 擴充套件，專門用來儲存與檢索高維度向量（如 Embedding 向量），支援精確和近似的最近鄰搜尋 (L2 距離、內積與餘弦距離)。

## 向量欄位宣告與 Ingestion
1. 建立具有向量欄位的資料表：
   ```sql
   CREATE TABLE knowledge_documents (
       id BIGSERIAL PRIMARY KEY,
       content TEXT NOT NULL,
       embedding vector(1536), -- 適合 OpenAI text-embedding-3-small (1536 維)
       metadata JSONB
   );
   ```

2. 向量插入與更新：
   透過 Java 呼叫 Spring AI 的 `EmbeddingModel` 獲取向量，並寫入資料庫：
   ```java
   List<Double> vector = embeddingModel.embed(textChunk);
   // 寫入知識庫資料表
   ```

## 餘弦相似度向量檢索 SQL
在 pgvector 中，可以使用 `<=>` 運算子計算餘弦距離（1 - 餘弦相似度）：
```sql
SELECT id, content, 1 - (embedding <=> :queryVector) AS similarity
FROM knowledge_documents
WHERE 1 - (embedding <=> :queryVector) > 0.75
ORDER BY embedding <=> :queryVector
LIMIT 3;
```
以上指令會篩選出餘弦相似度高於 0.75 的前 3 筆最相關的文件片段，用以提供 RAG 的 Context 注入。
