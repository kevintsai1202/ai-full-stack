# Docker PostgreSQL 與 pgvector 設定說明

## docker-compose.yml

```yaml
version: "3.9"
services:
  postgres:
    image: pgvector/pgvector:pg17
    container_name: ai-crm-db
    environment:
      POSTGRES_DB: aicrm
      POSTGRES_USER: aicrm
      POSTGRES_PASSWORD: aicrm
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

volumes:
  pgdata:
```

## 啟動指令

```powershell
docker compose up -d
```

## 驗證連線

```powershell
docker exec -it ai-crm-db psql -U aicrm -d aicrm -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

如果看到 `vector` 擴充套件已安裝，代表 pgvector 正常運作。
