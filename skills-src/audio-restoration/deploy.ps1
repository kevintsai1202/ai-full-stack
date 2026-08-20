# 將技能從專案原始碼部署到全域技能目錄。
# ~/.claude 不是 git repo，故原始碼在專案內維護，部署只是複製。
param(
    [string]$Target = "$HOME\.claude\skills\audio-restoration"
)

$ErrorActionPreference = "Stop"
$source = $PSScriptRoot

# 只部署執行時需要的檔案，測試與開發用檔案不進全域目錄
$include = @("SKILL.md", "scripts", "references")

if (Test-Path $Target) {
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
