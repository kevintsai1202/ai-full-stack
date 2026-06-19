# 問卷後端整合驗證：POST 一筆 → 用 admin 金鑰 GET → 檢查存在；並測 consent/蜜罐
# 用法：先啟動後端，再執行
#   $env:ADMIN_API_KEY = "dev-admin-key"; pwsh ./survey-backend/scripts/test-survey-api.ps1 -BaseUrl http://127.0.0.1:8080
param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$AdminKey = $(if ($env:ADMIN_API_KEY) { $env:ADMIN_API_KEY } else { "dev-admin-key" })
)
$ErrorActionPreference = "Stop"
$mark = "test-$(Get-Random)@example.com"

# 1. 合法問卷 → 預期 201
$body = @{ email = $mark; name = "測試"; interest = @("RAG 知識庫","MCP"); consent = $true; utm = @{ utm_source="test" } } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/survey" -Method Post -ContentType "application/json" -Body $body -SkipHttpErrorCheck
if ($r.StatusCode -ne 201) { throw "合法問卷預期 201，實得 $($r.StatusCode)" }
Write-Host "OK 合法問卷 201"

# 2. 未同意 → 預期 400
$bad = @{ email = "x@y.com"; consent = $false } | ConvertTo-Json
$r2 = Invoke-WebRequest -Uri "$BaseUrl/api/survey" -Method Post -ContentType "application/json" -Body $bad -SkipHttpErrorCheck
if ($r2.StatusCode -ne 400) { throw "未同意預期 400，實得 $($r2.StatusCode)" }
Write-Host "OK 未同意 400"

# 3. 蜜罐有值 → 預期 204
$hp = @{ email = "bot@y.com"; consent = $true; website = "spam" } | ConvertTo-Json
$r3 = Invoke-WebRequest -Uri "$BaseUrl/api/survey" -Method Post -ContentType "application/json" -Body $hp -SkipHttpErrorCheck
if ($r3.StatusCode -ne 204) { throw "蜜罐預期 204，實得 $($r3.StatusCode)" }
Write-Host "OK 蜜罐 204"

# 4. 管理 API 無金鑰 → 預期 401
$r4 = Invoke-WebRequest -Uri "$BaseUrl/api/admin/survey" -SkipHttpErrorCheck
if ($r4.StatusCode -ne 401) { throw "無金鑰預期 401，實得 $($r4.StatusCode)" }
Write-Host "OK 無金鑰 401"

# 5. 管理 API 有金鑰 → 預期 200 且含剛才那筆 email
$r5 = Invoke-WebRequest -Uri "$BaseUrl/api/admin/survey" -Headers @{ "X-Admin-Key" = $AdminKey } -SkipHttpErrorCheck
if ($r5.StatusCode -ne 200) { throw "有金鑰預期 200，實得 $($r5.StatusCode)" }
if ($r5.Content -notmatch [regex]::Escape($mark)) { throw "名單中找不到剛送出的 $mark" }
Write-Host "OK 管理查詢含剛送出資料"

Write-Host "`n全部通過 ✅"
