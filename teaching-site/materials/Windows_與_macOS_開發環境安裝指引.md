# Windows 與 macOS 開發環境安裝指引

## 環境需求

- **Java**: JDK 21 (Eclipse Temurin)
- **Maven**: 3.9+
- **Node.js**: 20 LTS
- **pnpm**: 最新版
- **Git**: 最新版
- **PowerShell**: 7+ (Windows) / Terminal (macOS)

## Windows 快速安裝

```powershell
# 安裝 JDK 21
winget install EclipseAdoptium.Temurin.21.JDK

# 安裝 Maven
winget install Apache.Maven

# 安裝 Node.js
winget install OpenJS.NodeJS.LTS

# 安裝 Git
winget install Git.Git
```

## macOS 快速安裝

```bash
# 安裝 Homebrew（若無）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安裝 JDK 21
brew install --cask temurin@21

# 安裝 Maven
brew install maven

# 安裝 Node.js
brew install node

# 安裝 pnpm
npm install -g pnpm

# 安裝 Git
brew install git
```

## 驗證指令

```powershell
java -version
mvn -version
node -v
git --version
```
