<#
.SYNOPSIS
    Zeabur 正式 PostgreSQL 的備份工具：列出既有備份、建立新備份、等待完成。

.DESCRIPTION
    部署 Flyway migration 前必須先備份正式庫（migration 一旦被記錄版本即不可逆）。
    本腳本走 Zeabur 官方 GraphQL API 的 createBackup，好處是不必把資料庫連線埠
    對外開放——那會讓正式庫暴露在公網上，風險遠高於備份本身要解決的問題。

    token 從環境變數 ZEABUR_TOKEN 讀取，絕不寫進檔案，因此本腳本可安全進版控。

.PARAMETER Action
    list   ：只列出既有備份（唯讀，預設）
    create ：建立新備份並等待完成

.EXAMPLE
    $env:ZEABUR_TOKEN = "sk-..."
    ./zeabur-db-backup.ps1 -Action list
    ./zeabur-db-backup.ps1 -Action create
#>
[CmdletBinding()]
param(
    [ValidateSet('list', 'create')]
    [string]$Action = 'list',

    # 建立備份後最多等待幾秒；PostgreSQL 資料量小時通常數秒內完成
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'

# --- 目標服務座標（hahow-ai-full-stack / production / postgresql）---
$ProjectId     = '6a3483c107afd8c0435e56c0'
$EnvironmentId = '6a3483c1aeb19c03fe465a71'
$ServiceId     = '6a350332558aac447d431a52'

if (-not $env:ZEABUR_TOKEN) {
    throw ' 請先設定 $env:ZEABUR_TOKEN（Zeabur API token），本腳本刻意不內嵌任何憑證。'
}

# Cloudflare 會用 error code 1010 封鎖非瀏覽器的 User-Agent，故必須帶 UA
$headers = @{
    Authorization  = "Bearer $($env:ZEABUR_TOKEN)"
    'Content-Type' = 'application/json'
    'User-Agent'   = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
}

<#
.SYNOPSIS
    送出一則 GraphQL 查詢，回傳已解析的 data 區塊；GraphQL 層級的錯誤一律拋出。
#>
function Invoke-Zeabur {
    param([string]$Query)

    $body = @{ query = $Query } | ConvertTo-Json -Compress -Depth 5
    $resp = Invoke-RestMethod -Uri 'https://api.zeabur.com/graphql' `
        -Headers $headers -Method Post -Body $body

    # GraphQL 的錯誤是 HTTP 200 帶 errors 欄位，不檢查會靜默當成成功
    if ($resp.errors) {
        throw "Zeabur API 錯誤：$($resp.errors | ConvertTo-Json -Compress -Depth 5)"
    }
    return $resp.data
}

<#
.SYNOPSIS
    取得該服務目前所有備份，依建立時間排序後印成表格。
#>
function Get-Backups {
    $data = Invoke-Zeabur @"
{ backups(environmentID: "$EnvironmentId", serviceID: "$ServiceId") {
    _id status fileSize createdAt finishedAt errorMessage } }
"@
    return @($data.backups)
}

<#
.SYNOPSIS
    把位元組數格式化成人類可讀的大小，方便一眼確認備份不是 0 位元組。
#>
function Format-Size {
    param([Nullable[long]]$Bytes)
    if ($null -eq $Bytes) { return '-' }
    if ($Bytes -ge 1MB) { return "{0:N2} MB" -f ($Bytes / 1MB) }
    if ($Bytes -ge 1KB) { return "{0:N1} KB" -f ($Bytes / 1KB) }
    return "$Bytes B"
}

# --- 主流程 ---
Write-Host "目標：hahow-ai-full-stack / production / postgresql ($ServiceId)" -ForegroundColor Cyan

$before = Get-Backups
Write-Host "`n=== 現有備份（$($before.Count) 筆）===" -ForegroundColor Cyan
if ($before.Count -eq 0) {
    Write-Host '（無）'
} else {
    $before | Sort-Object createdAt -Descending | ForEach-Object {
        Write-Host ("  {0}  {1,-10} {2,-10} {3}" -f $_._id, $_.status, (Format-Size $_.fileSize), $_.createdAt)
        if ($_.errorMessage) { Write-Host "      錯誤：$($_.errorMessage)" -ForegroundColor Red }
    }
}

if ($Action -eq 'list') {
    Write-Host "`n（唯讀模式，未建立任何備份。要備份請加 -Action create）" -ForegroundColor Yellow
    return
}

# createBackup 只回 Boolean，不回 job id，所以用「備份清單新增了哪一筆」來辨識本次建立的備份
$knownIds = @($before | ForEach-Object { $_._id })

Write-Host "`n=== 建立備份 ===" -ForegroundColor Cyan
$ok = (Invoke-Zeabur @"
mutation { createBackup(environmentID: "$EnvironmentId", serviceID: "$ServiceId") }
"@).createBackup

if (-not $ok) { throw 'createBackup 回傳 false，備份未建立。' }
Write-Host 'createBackup 已接受，等待完成……'

# 輪詢直到新備份離開進行中狀態；不能只看 status 就當成功，還要確認 fileSize > 0
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$target = $null
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $target = Get-Backups | Where-Object { $knownIds -notcontains $_._id } |
        Sort-Object createdAt -Descending | Select-Object -First 1

    if ($null -eq $target) { Write-Host '  尚未出現新備份紀錄……'; continue }
    Write-Host "  $($target._id) 狀態：$($target.status)"
    if ($target.status -notin @('RUNNING', 'PENDING', 'CREATED')) { break }
}

if ($null -eq $target) { throw "等待 $TimeoutSeconds 秒仍未出現新備份紀錄，請到 Zeabur 後台確認。" }

Write-Host "`n=== 結果 ===" -ForegroundColor Cyan
Write-Host "備份 ID  ：$($target._id)"
Write-Host "狀態     ：$($target.status)"
Write-Host "大小     ：$(Format-Size $target.fileSize)"
Write-Host "建立時間 ：$($target.createdAt)"
Write-Host "完成時間 ：$($target.finishedAt)"
if ($target.errorMessage) { Write-Host "錯誤     ：$($target.errorMessage)" -ForegroundColor Red }

# 明確判定成功條件：狀態非失敗、且檔案大小大於 0（0 位元組的備份等於沒有備份）
if ($target.status -match 'FAIL|ERROR' -or -not $target.fileSize -or $target.fileSize -le 0) {
    throw '備份未成功（狀態失敗或檔案大小為 0），請勿在此狀態下部署 migration。'
}
Write-Host "`n備份成功，可以進行 migration 部署。" -ForegroundColor Green
