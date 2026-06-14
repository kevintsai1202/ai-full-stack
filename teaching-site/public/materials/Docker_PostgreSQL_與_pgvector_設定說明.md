# Docker PostgreSQL 與 pgvector 設定說明

本講義為「AI 賦能全端開發」課程的資料庫與向量擴充設定教材。

## Docker Compose 設定
在專案根目錄下建立 `docker-compose.yml` 檔案，配置支援 pgvector 插件的 PostgreSQL 映像檔：

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg18
    container_name: crm-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: crm_user
      POSTGRES_PASSWORD: crm_password
      POSTGRES_DB: ai_crm
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

## 啟動資料庫
使用 PowerShell 7 執行以下指令啟動容器：
```powershell
docker-compose up -d
```

## 驗證 pgvector 插件
進入 PostgreSQL 容器並驗證向量擴充功能：
```powershell
docker exec -it crm-postgres psql -U crm_user -d ai_crm
```
在 psql 中執行以下 SQL 啟用向量擴充：
```sql
CREATE EXTENSION IF NOT EXISTS vector;
SELECT * FROM pg_extension WHERE extname = 'vector';
```
如果查詢結果有出現 `vector`，則代表向量資料庫配置成功！
