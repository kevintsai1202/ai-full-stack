# 文章圖片／檔案上傳與 MinIO 實作

日期：2026-07-28

## 目標與範圍

- Admin 可上傳圖片與安全白名單文件，檔案本體存 MinIO。
- 圖片可插入 Markdown 內文，也可設為文章封面。
- 文件以 Markdown 下載連結插入，不在頁面中直接執行或預覽。
- 讀者 archive 顯示封面圖；未設定時沿用封面 Emoji。
- 單篇文章在標題下方顯示封面圖，內嵌圖片保持響應式。
- Markdown 渲染器會直接為每張圖片輸出 Email 可攜的 inline
  `max-width:100%; height:auto`，避免郵件客戶端不套用網站 CSS 時撐破版面。
- 後台預覽與測試信以伺服器依 `coverMediaId` 解析出的公開 URL 顯示封面，
  最大寬度 560px、最大高度 360px；未選圖片時顯示封面 Emoji。

不做圖片裁切、線上圖片編輯、影片／音訊、任意 HTML／SVG 上傳與媒體刪除。公開信件必須能匿名載入圖片，因此媒體 bucket 為匿名唯讀；後台是唯一寫入入口。

## 資料與儲存

Flyway V14 新增 `media_asset`，只保存 `object_key`、SHA-256、實際內容型別、大小、原始檔名、圖片尺寸與建立時間。`campaign.cover_media_id` 以外鍵指向圖片；刪除媒體時設為 NULL。

MinIO bucket 預設為 `newsletter-media`。啟動時後端以 AWS SDK for Java v2、path-style S3：

1. 檢查 bucket，不存在才建立。
2. 套用只允許 `s3:GetObject` 的匿名 policy。
3. 任何初始化失敗都中止啟動，不以「已啟用但不能上傳」的狀態提供服務。

物件 key 使用完整 SHA-256 加上可信的標準副檔名，內容相同時直接回傳既有媒體，避免重複占用空間。

## API

- `POST /api/admin/media`：multipart 欄位 `file`，需 `X-Admin-Key`。
- `GET /api/admin/media?limit=100`：媒體庫，新到舊，需 `X-Admin-Key`。

回應包含 `id`、`kind`、`contentType`、`sizeBytes`、`originalName`、圖片尺寸與絕對 `url`。瀏覽器不取得 MinIO access key／secret key。

寄送與發布請求新增可省略的 `coverMediaId`。後端在任何寄送／發布副作用前確認該 ID 存在且為圖片；`coverEmoji` 仍為 fallback。

## 安全驗證

- 圖片只接受 PNG、JPEG、GIF、WebP，單檔最多 5 MB。
- 文件只接受 PDF、UTF-8 TXT／CSV、DOCX／XLSX／PPTX，單檔最多 10 MB。
- SVG、HTML、JavaScript、可執行檔與泛用 ZIP 一律拒絕。
- 不信任檔名與瀏覽器 MIME：依 magic bytes／檔案結構判定實際類型。
- 圖片解析寬高並限制最多 4,000 萬像素，避免解碼炸彈。
- 原始檔名移除路徑與控制字元；物件 key 不使用使用者輸入。
- 公開物件回應使用不可變快取；非圖片文件以 attachment 下載。
- 此 bucket 不得存放敏感資料；公開 URL 寄出後無法可靠撤回。

## 驗證

- 單元測試覆蓋格式偽造、超量、像素上限、去重與 MinIO 停用。
- Migration safety 測試從 V1 套用到 V14，既有資料筆數與同意狀態不變。
- 瀏覽器驗證上傳、插圖、設封面、預覽、發布、archive 封面與單篇圖片。
- 正式部署後驗證 MinIO 匿名只能讀、未授權不能寫，Admin API 無金鑰為 401。
- 以 CycloneDX 產出完整 runtime SBOM（115 個元件）交由 OSV 掃描；排除未使用的 AWS Apache／Netty client 後，已知漏洞為 0。

## Zeabur 環境變數

- `MEDIA_STORAGE_ENABLED=true`
- `MINIO_ENDPOINT`：後端連線用的 MinIO 內網 S3 endpoint。
- `MINIO_PUBLIC_BASE_URL`：讀者與信箱可讀取的 HTTPS 公開網域。
- `MINIO_ACCESS_KEY`／`MINIO_SECRET_KEY`：僅設於後端與 MinIO，不得輸出至頁面或提交 Git。
- `MINIO_BUCKET=newsletter-media`
- `MINIO_REGION=us-east-1`
