# 讀者網頁與管理後台安全強化

日期：2026-07-28

## 修正範圍

本次只處理 `survey-backend` 的部署密鑰、登入濫用防護、瀏覽器安全標頭、預覽隔離與第三方依賴漏洞，不改變既有電子報批次、讀者洞察、文章解鎖與 Markdown 編輯契約。

## 部署密鑰

正式環境必須提供三組互不相同、長度至少 32 字元且不可使用公開預設值的密鑰：

- `ADMIN_API_KEY`
- `UNSUBSCRIBE_SECRET`
- `READER_JWT_SECRET`

任一密鑰不合格時，服務會拒絕啟動，而且錯誤訊息不會輸出密鑰內容。本機若確實要沿用開發預設值，必須明確設定 `APP_ALLOW_INSECURE_DEV_SECRETS=true`；此設定不可用於 Zeabur。

## Magic Link 濫用防護

登入請求除了既有的單一 Email 節流，另以資料庫持久化 IP 雜湊及全站請求量，避免重啟或多實例部署後繞過限制。資料庫只保存不可逆 SHA-256 雜湊，不保存原始 IP。

可調整的環境變數：

- `READER_LOGIN_THROTTLE_MINUTES`：統計時間窗，預設 10 分鐘。
- `READER_LOGIN_IP_THROTTLE_COUNT`：單一 IP 時間窗上限，預設 20 次。
- `READER_LOGIN_GLOBAL_THROTTLE_COUNT`：全站時間窗上限，預設 200 次。

Flyway `V13__login_abuse_guard.sql` 會建立 `login_request_attempt` 與查詢索引。

## HTTP 與預覽隔離

所有回應加入 CSP、`X-Content-Type-Options`、`X-Frame-Options`、Referrer Policy、Permissions Policy 與一年期 HSTS。管理及讀者 API 使用 `Cache-Control: private, no-store`；管理 API 另加入 `Vary: X-Admin-Key`。後台兩個 `srcdoc` 預覽 iframe 使用空 sandbox，避免預覽內容執行腳本或存取父頁。

## 依賴版本

- Spring Boot `3.5.14`
- Jackson BOM `2.21.5`
- Logback `1.5.34`
- Apache Commons Lang `3.18.0`
- PostgreSQL JDBC `42.7.12`
- Tomcat `10.1.57`

## 發佈閘門

2026-07-28 本機驗證結果：

- Maven：650 tests、0 failures、0 errors、0 skipped。
- CycloneDX + OSV：115 個 runtime dependencies、0 筆已知 advisory；未使用的 AWS Apache／Netty client 已排除。
- 弱預設密鑰：服務拒絕啟動。
- 舊 `dev-admin-key`：管理 API 回應 401。
- 舊預設 JWT secret 偽造 token：讀者 API 回應 401。
- 強管理密鑰：管理 API 回應 200。
- 實際回應包含 `Cache-Control: private, no-store`、`Vary: X-Admin-Key`、CSP、HSTS 與 `X-Frame-Options: DENY`。
- `reader.springai.world/admin.html` 與 `admin.springai.world/r/archive` 均回 404，讀者站與管理站入口互相隔離。
- MinIO 公開物件只允許匿名 GET；匿名 PUT 與 bucket list 均回 403。
- 後台兩個預覽 iframe 均有空 sandbox。

Zeabur 發佈前必須確認三組正式密鑰已設定、彼此不同，且未設定 `APP_ALLOW_INSECURE_DEV_SECRETS=true`；發佈後需重新驗證公開頁 200、舊管理密鑰 401、安全標頭與健康狀態。
