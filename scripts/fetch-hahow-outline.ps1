# 用途：抓取 Hahow 課程頁（https://hahow.in/cr/ai-full-stack）的「預計單元」章節與單元清單
# 背景：Hahow 為 JS 渲染 SPA，WebFetch/curl 只能拿到頁面殼，需用 agent-browser 實際渲染後擷取
# 執行方式：pwsh scripts/fetch-hahow-outline.ps1 [-OutFile <輸出檔路徑>]
# 輸出：整頁純文字（含「課程內容」行銷版章節細目與「預計單元」官方 9 章清單），存到 $OutFile
# 踩坑：本機環境 `agent-browser eval --stdin` 一律回傳 null，必須改用參數式單行 eval

param(
    # 輸出檔路徑，預設存到 repo 根目錄 output/hahow-outline.txt
    [string]$OutFile = (Join-Path $PSScriptRoot "..\output\hahow-outline.txt")
)

$ErrorActionPreference = 'Stop'

# 確保輸出目錄存在
$outDir = Split-Path -Parent $OutFile
New-Item -ItemType Directory -Force $outDir | Out-Null

# 1. 開啟課程頁並等待網路閒置（SPA 內容渲染完成）
agent-browser open "https://hahow.in/cr/ai-full-stack"
agent-browser wait --load networkidle

# 2. 逐段捲動頁面，觸發 lazy-load 內容（章節區塊在頁面中後段）
1..8 | ForEach-Object { agent-browser scroll down 1500 | Out-Null }
agent-browser wait --load networkidle

# 3. 點開「展開全部」（FAQ 與預計單元區塊各有一顆，逐一嘗試，找不到不視為失敗）
foreach ($label in '展開全部', '全部展開') {
    try { agent-browser find text $label click } catch {}
}
agent-browser wait --load networkidle

# 4. 擷取整頁純文字並存檔（注意：eval 必須用參數式單行 JS，--stdin 模式會回傳 null）
agent-browser eval "document.body.innerText" | Set-Content -Path $OutFile -Encoding UTF8

# 5. 關閉瀏覽器
agent-browser close

Write-Host "已輸出：$OutFile"
