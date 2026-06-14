# Windows 與 macOS 開發環境安裝指引

本講義為「AI 賦能全端開發」課程的跨平台開發環境準備教材。本課程同時支援 Windows 與 macOS 兩種開發環境。

## 1. Windows 開發環境準備

### 為什麼需要 PowerShell 7+？
Windows 內建的 Windows PowerShell (5.1) 版本過舊，對於許多現代開發工具、腳本語法以及 Linux 相容指令（如：curl、wget、甚至是 JSON 處理）支援不佳。PowerShell 7+ 是基於 .NET 8 打造的跨平台 shell，效能更高且支援更多強大的語法。

### 安裝步驟

#### 方法一：使用 WinGet (推薦)
在 Windows 10/11 的終端機中執行以下指令進行快速安裝：
```powershell
winget install --id Microsoft.Powershell --source winget
```

#### 方法二：手動下載
1. 造訪 [GitHub PowerShell 發行頁面](https://github.com/PowerShell/PowerShell/releases)。
2. 下載最新穩定版 (LTS) 的 `PowerShell-x.y.z-win-x64.msi`。
3. 雙擊執行並按指示安裝。

### 驗證安裝
打開 PowerShell 7 (可在開始功能表搜尋 "pwsh")，並輸入以下指令驗證版本：
```powershell
$PSVersionTable.PSVersion
```
預期輸出主版本號應大於等於 `7`。

---

## 2. macOS 開發環境準備

### 終端機與軟體套件管理
macOS 預設使用 `zsh` 作為 Shell。為了安裝開發工具，建議使用 macOS 最熱門的軟體套件管理器 **Homebrew**。

### 安裝 Homebrew
打開 Terminal，輸入以下指令安裝 Homebrew：
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### 安裝常用開發工具鏈
安裝 Java 21、Maven 以及 Node.js：
```bash
# 安裝 OpenJDK 21
brew install openjdk@21

# 建立軟連結以使系統識別 Java
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# 安裝 Maven
brew install maven

# 安裝 Node.js
brew install node
```

### 驗證安裝
在終端機中驗證各工具版本：
```bash
java -version
mvn -v
node -v
```
