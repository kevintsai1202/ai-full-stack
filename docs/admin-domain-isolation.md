# 管理後台獨立網域

管理後台使用 `https://admin.springai.world`，與問卷及電子報讀者入口分開：

- `survey.springai.world`：問卷
- `reader.springai.world`：電子報讀者
- `admin.springai.world`：管理後台

## 上線順序

為避免 DNS 尚未生效時無法進入後台，採兩階段切換：

1. 設定 `ADMIN_ENTRY_HOST=admin.springai.world`，並將 Gateway 的
   `admin.springai.world` 指向 `survey-backend:web`。
2. 確認 `https://admin.springai.world` 可正常開啟、登入及呼叫管理 API。
3. 設定 `SURVEY_ENTRY_HOST=survey.springai.world`，關閉舊的
   `https://survey.springai.world/admin.html` 入口。

## 網域隔離規則

管理網域採允許清單，只開放：

- `/`、`/index.html`：暫時導向 `/admin.html`
- `/admin.html`
- `/api/admin`
- `/api/admin/**`

問卷、讀者頁與其他 API 在管理網域一律回傳 `404`。完成第二階段後，
問卷網域上的 `/admin.html` 與 `/api/admin/**` 也會回傳 `404`。

此隔離是入口邊界，管理 API 仍須使用既有的 Admin Key 驗證。
