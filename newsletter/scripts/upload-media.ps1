# 電子報媒體上傳：把圖片送進媒體庫，取回可直接寫進內文的 URL。
#
# 用途：電子報內文的圖片必須是媒體庫的公開 URL（信件客戶端讀不到本機檔案）。
#       這支腳本把上傳與「記錄 URL」兩件事一起做完，避免手動上傳後忘了回填
#       uploaded-urls.txt，導致日後查不出某個 hash 對應哪張圖。
#
# 金鑰處理：從環境變數讀取，不寫進檔案、不印在畫面上。
#   $env:ADMIN_API_KEY = '<你的管理金鑰>'
#   （若後台網域不同，另設 $env:ADMIN_BASE_URL，預設 https://admin.springai.world）
#
# 用法：
#   pwsh newsletter/scripts/upload-media.ps1 newsletter/assets/png/012-c-feasibility-difficulty.png
#   pwsh newsletter/scripts/upload-media.ps1 (Get-ChildItem newsletter/assets/png/012-*.png).FullName
#
#   上傳並直接把內文的 TODO 佔位 URL 換成真網址（一步到位）：
#   pwsh newsletter/scripts/upload-media.ps1 -ApplyTo newsletter/newsletter-12-auditable-output-feasibility.md `
#        (Get-ChildItem newsletter/assets/png/012-*.png).FullName
#
# 檔名與佔位的對應規則：`012-c-feasibility-difficulty.png` 對應內文的
# `TODO-012-feasibility-difficulty.png`（去掉排序用的 a/b/c/d 那一段）。
#
# 輸出：逐檔印出「檔名 -> URL」，並追加到 newsletter/assets/uploaded-urls.txt

[CmdletBinding()]
param(
    # 要上傳的檔案路徑，可帶多個
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]] $Files,

    # 選填：上傳成功後，把這個 Markdown 檔裡對應的 TODO 佔位 URL 換成真網址
    [string] $ApplyTo
)

$ErrorActionPreference = 'Stop'

$key = $env:ADMIN_API_KEY
if ([string]::IsNullOrWhiteSpace($key)) {
    throw "請先設定管理金鑰：`$env:ADMIN_API_KEY = '<你的金鑰>'（本腳本不會把它寫進任何檔案）"
}

$baseUrl = if ($env:ADMIN_BASE_URL) { $env:ADMIN_BASE_URL.TrimEnd('/') } else { 'https://admin.springai.world' }
$endpoint = "$baseUrl/api/admin/media"

# uploaded-urls.txt 與本腳本的相對位置固定，直接由腳本位置推得
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$urlsFile = Join-Path $scriptDir '..\assets\uploaded-urls.txt' | Resolve-Path -ErrorAction SilentlyContinue
if (-not $urlsFile) {
    $urlsFile = Join-Path $scriptDir '..\assets\uploaded-urls.txt'
}

# 既有紀錄：同名檔案已上傳過就提醒，避免重複佔用媒體庫空間
$existing = @{}
if (Test-Path $urlsFile) {
    Get-Content $urlsFile | ForEach-Object {
        if ($_ -match '^(.+?)\s*->\s*(\S+)$') { $existing[$Matches[1].Trim()] = $Matches[2] }
    }
}

$results = @()
foreach ($file in $Files) {
    $item = Get-Item $file -ErrorAction Stop
    $name = $item.Name

    if ($existing.ContainsKey($name)) {
        Write-Host "  跳過  $name（已存在：$($existing[$name])）"
        continue
    }

    Write-Host "  上傳  $name ..."
    $response = Invoke-RestMethod -Uri $endpoint -Method Post `
        -Headers @{ 'X-Admin-Key' = $key } `
        -Form @{ file = $item }

    $url = $response.url
    if ([string]::IsNullOrWhiteSpace($url)) {
        throw "上傳 $name 後未取得 url，回應：$($response | ConvertTo-Json -Compress)"
    }

    Add-Content -Path $urlsFile -Value "$name -> $url" -Encoding UTF8
    $results += [pscustomobject]@{ File = $name; Url = $url }
    Write-Host "   OK   $name"
}

Write-Host ''
if ($results.Count -eq 0) {
    Write-Host '沒有新檔案需要上傳。'
    return
}

Write-Host "已上傳 $($results.Count) 個檔案，URL 已追加到 $urlsFile："
$results | ForEach-Object { Write-Host "  $($_.File) -> $($_.Url)" }

if (-not $ApplyTo) {
    Write-Host ''
    Write-Host '接著把內文中對應的 TODO 圖片 URL 換成上面的網址（或改用 -ApplyTo 讓腳本自動替換）。'
    return
}

# --- 自動替換內文的 TODO 佔位 URL ---
$target = Get-Item $ApplyTo -ErrorAction Stop
$content = Get-Content $target.FullName -Raw -Encoding UTF8
$replaced = 0

foreach ($r in $results) {
    # 012-c-feasibility-difficulty.png → TODO-012-feasibility-difficulty.png
    if ($r.File -notmatch '^(\d{3})-[a-z]-(.+\.png)$') {
        Write-Host "  略過  $($r.File)（檔名不符 NNN-x-名稱.png 慣例，無法推得佔位名）"
        continue
    }
    $placeholder = "TODO-$($Matches[1])-$($Matches[2])"

    # 只比對「完整的佔位 URL」，避免動到寄送提醒裡單純提及檔名的文字
    $pattern = 'https?://\S*?' + [regex]::Escape($placeholder)
    if ($content -match $pattern) {
        $content = [regex]::Replace($content, $pattern, $r.Url)
        $replaced++
        Write-Host "  替換  $placeholder → $($r.Url)"
    } else {
        Write-Host "  找不到佔位  $placeholder（內文可能已經換過了）"
    }
}

if ($replaced -gt 0) {
    Set-Content -Path $target.FullName -Value $content -Encoding UTF8 -NoNewline
    Write-Host ''
    Write-Host "已更新 $($target.Name)，共替換 $replaced 個圖片 URL。"
    Write-Host '請重新產生後台預覽並寄一封測試信確認圖片顯示正常。'
} else {
    Write-Host ''
    Write-Host "$($target.Name) 沒有任何佔位被替換。"
}
