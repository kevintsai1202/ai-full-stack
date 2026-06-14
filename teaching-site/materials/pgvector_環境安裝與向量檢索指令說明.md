# pgvector 環境安裝與向量檢索指令說明

## 啟用 pgvector 擴充

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## 建立含向量欄位的資料表

```sql
CREATE TABLE customer_docs (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(1536)
);
```

## 相似度查詢

```sql
-- 歐幾里得距離
SELECT * FROM customer_docs ORDER BY embedding <-> '[0.1, 0.2, ...]' LIMIT 5;

-- 餘弦相似度
SELECT * FROM customer_docs ORDER BY embedding <=> '[0.1, 0.2, ...]' LIMIT 5;

-- 內積
SELECT * FROM customer_docs ORDER BY embedding <#> '[0.1, 0.2, ...]' LIMIT 5;
```

## 建立索引（資料量大時）

```sql
CREATE INDEX ON customer_docs USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```
