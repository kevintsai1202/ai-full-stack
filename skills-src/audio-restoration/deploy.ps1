# 將技能從專案原始碼部署到全域技能目錄。
# ~/.claude 不是 git repo，故原始碼在專案內維護，部署只是複製。
param(
    [string]$Target = "$HOME\.claude\skills\audio-restoration"
)

$ErrorActionPreference = "Stop"
$source = $PSScriptRoot

# 只部署執行時需要的檔案，測試與開發用檔案不進全域目錄
# requirements.txt 也要帶：使用者換機或 venv 損毀時，需要能在全域目錄
# 直接 pip install -r 重建環境，不必回頭翻專案原始碼
$include = @("SKILL.md", "scripts", "references", "requirements.txt")

if (Test-Path $Target) {
    # 整個刪除重建。全域目錄非版控，手動改過的內容會消失，故先明說
    Write-Host "移除既有的 $Target（手動修改過的內容不會保留）"
    Remove-Item -Recurse -Force $Target
}
New-Item -ItemType Directory -Force -Path $Target | Out-Null

foreach ($item in $include) {
    $sourcePath = Join-Path $source $item
    if (Test-Path $sourcePath) {
        Copy-Item -Recurse -Force $sourcePath (Join-Path $Target $item)
    }
}

# 清掉複製過來的 __pycache__
Get-ChildItem -Path $Target -Recurse -Directory -Filter "__pycache__" |
    Remove-Item -Recurse -Force

Write-Host "已部署至 $Target"
