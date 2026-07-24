# 用途：開啟 Hahow 募資購買頁，擷取頁面實際顯示的課程金額與購買（贊助）人數
# 使用方式：pwsh land-page/scripts/fetch-hahow-info.ps1
# 輸出：頁面顯示的價格文字、贊助人數，以及截圖（存於 land-page/assets/hahow/purchase-page.png）

$ErrorActionPreference = 'Stop'

# 目標購買頁網址
$url = 'https://hahow.in/cr/ai-full-stack'
# 截圖輸出路徑
$shot = Join-Path $PSScriptRoot '..\assets\hahow\purchase-page-live.png'

# 1. 開啟頁面並等待 SPA 載入完成
agent-browser open $url
agent-browser wait --load networkidle

# 2. 用 JavaScript 擷取頁面上的價格與購買人數（Hahow 募資頁的文案包含「人購買 / 已募資」等字樣）
@'
// 從整頁文字中擷取價格（NT$ 開頭數字）與購買人數（「N 人購買」或「N 人已購買」）
const body = document.body.innerText;
const prices = [...body.matchAll(/NT\$\s*([\d,]+)/g)].map(m => m[1]);
const buyers = body.match(/([\d,]+)\s*人(已)?(購買|贊助|募資)/);
const progress = body.match(/([\d,]+)\s*%/);
({ prices, buyers: buyers ? buyers[0] : null, progress: progress ? progress[0] : null });
'@ | agent-browser eval --stdin

# 3. 截圖留存以便人工核對
agent-browser screenshot $shot

# 4. 關閉瀏覽器釋放資源
agent-browser close
