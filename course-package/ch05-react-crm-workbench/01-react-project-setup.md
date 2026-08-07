# 章節 5 單元 1｜React 專案建立

## 單元定位

前四章我們完成了一個受 JWT 保護、資料落地 PostgreSQL 的 CRM 後端，但到目前為止所有驗證都是用 PowerShell 指令或 Swagger 敲 API——業務同仁不可能這樣用系統。本單元是章節 5 的起點：建立 Node.js / NPM 環境認知，並用 Vite 建立 React 19 前端專案，讓開發伺服器成功跑起來。下一單元會進入 JSX 語法與元件結構。建議時長：10～12 分鐘。

## 教學素材

### Node.js 與 NPM

- Node.js 是前端開發的執行環境，NPM (Node Package Manager) 是套件管理工具。
- React 19 的開發不再使用傳統手動下載 JS 檔的方式，而是透過 NPM 安裝相依套件、管理版本。

### 用 Vite 建立 React 19 專案

使用業界主流、極速的 Vite 作為建置工具，指令步驟如下：

```bash
# 建立 Vite React 專案
npx create-vite@latest frontend --template react

# 進入專案目錄
cd frontend

# 安裝最新無資安漏洞的 React 19 依賴
npm install

# 啟動本機開發伺服器 (Port 5173)
npm run dev
```

### 開發端代理 (Vite Proxy) 與後端 API 串接

前後端分離架構中，前端 Vite 伺服器跑在 `http://localhost:5173`，Spring Boot 後端跑在 `http://localhost:8080`。前端直接對後端發非同步請求 (Fetch / EventSource) 時，會因「同源政策 (Same-Origin Policy)」被瀏覽器阻擋，出現 CORS 跨網域錯誤。

解法是在 `vite.config.js` 配置 `server.proxy`，把所有以 `/api` 開頭的請求，在開發環境自動轉發到 `http://localhost:8080`。前端程式碼只需填寫相對路徑，也免除了後端配置 CORS 的繁瑣設定：

```javascript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 當前端請求 /api/ai/stream 時，Vite 自動代理為 http://localhost:8080/api/ai/stream
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

### 前後端整合的核心原則

前後端整合的核心在於「狀態的一致性」與「防禦性渲染」。後續單元會用 Axios Interceptor 自動為請求附加 JWT，並在 401 時引導重新登入；UI 上必須對 Loading、Error 及 Empty 三種狀態完整處理，保障使用者體驗。

## 示範與提示詞

本單元示範用的 AI Agent 提示詞（建立 React 前端專案骨架）：

```text
請幫我建立 CRM 智慧工作台的 React 前端專案：
1. 在專案根目錄用 Vite 建立 React 19 專案：npx create-vite@latest frontend --template react，並執行 npm install。
2. 設定 vite.config.js 的 server.proxy，把所有 /api 開頭的請求代理到 http://localhost:8080，解決前後端分離的 CORS 問題。
3. 建立 App 頁面骨架：包含漸層色 Header（linear-gradient 靛藍到紫色）、毛玻璃卡片容器（backdrop-filter: blur），以及聊天視窗的占位區塊。
4. CSS 請加上骨架屏（skeleton shimmer）載入動畫，之後聊天室等待 AI 回覆時會用到。
5. 元件需有中文註解。
完成後請告訴我如何啟動開發伺服器，以及如何確認 proxy 代理有生效。
```

搭配的口語化建置提示詞（先架版面骨架）：

```text
請幫我做出這套系統的網頁畫面外觀：要有現代感的設計（例如漸層的標題列、卡片式的區塊、載入時有過場動畫），先把整體版面骨架架好，內容之後再填。請加中文註解，完成後告訴我怎麼打開來看。
```

**驗證方式**：`npm run dev` 後瀏覽器打開 `http://localhost:5173` 能看到頁面骨架；對 `/api` 開頭的路徑發請求，確認 Vite 有把它代理到 8080 的後端。

## 口語稿

歡迎來到第五章。先幫大家把進度對一下：前面四章，我們已經把 CRM 的後端整個做起來了——有 REST API、資料真的存進 PostgreSQL、還加上了 Spring Security 跟 JWT 的保護。功能上其實已經很完整，但你有沒有發現一件事？到目前為止，我們每次要驗證功能，都是打開 PowerShell 敲 Invoke-RestMethod，或是去 Swagger 頁面按按鈕。你想像一下，如果我把這套系統交給公司的業務同仁，跟他說「你要查客戶喔，先開終端機，打這一串指令」——他大概明天就離職了。所以這一章我們要做的事情很明確：幫這個後端裝上一張臉，打造一個業務人員真的能用的 CRM 工作台前端，然後把它接上我們前四章做好的、受 JWT 保護的後端。

那要做前端，第一步是把環境跟專案架起來。這一節我們先講兩個東西：Node.js 跟 Vite。Node.js 是前端開發的執行環境，NPM 則是它的套件管理工具。這邊要提醒一下觀念上的轉變：在 React 19 的開發裡，我們不再像早期那樣，手動去下載一個 JS 檔案然後用 script 標籤引進來，而是全部透過 NPM 來安裝相依套件。這跟你在後端用 Maven 管理依賴是一模一樣的思路——版本交給工具管，不要手動搬檔案。

環境有了之後，我們用 Vite 來建專案。Vite 是目前業界主流的建置工具，特色就是快，開發伺服器幾乎是秒開。我們現在來實際跑一次，總共就四個指令。第一個，`npx create-vite@latest frontend --template react`，這會在專案根目錄建立一個叫 frontend 的 React 專案。接著 `cd frontend` 進到目錄裡，然後 `npm install`，把 React 19 的相依套件裝好——這邊裝的是最新、沒有資安漏洞的版本。最後 `npm run dev`，你會看到終端機顯示開發伺服器已經跑在 Port 5173。打開瀏覽器連到 localhost:5173，看到 Vite 加 React 的預設畫面，就代表前端專案活了。

不過馬上就會遇到第一個坑，而且是每個做前後端分離的人都一定會撞到的：CORS。你想，我們的前端跑在 5173，後端 Spring Boot 跑在 8080，這在瀏覽器眼中是兩個不同的來源。當前端直接對 8080 發非同步請求的時候，瀏覽器的「同源政策」就會跳出來把它擋掉，你會在 Console 看到紅字的 CORS 錯誤。那怎麼解？我們不去動後端，而是在 `vite.config.js` 裡面設定 `server.proxy`：告訴 Vite，凡是以 `/api` 開頭的請求，開發環境下都自動幫我轉發到 `http://localhost:8080`。這樣有兩個好處：第一，前端程式碼只要寫相對路徑，例如 `/api/customers`，不用把主機位址寫死；第二，因為請求是由 Vite 伺服器代轉的，瀏覽器根本不會覺得跨域，後端也完全不需要去配置那些繁瑣的 CORS 設定。

我們現在就把這整套流程交給 AI Agent 做。你把畫面上這段提示詞複製過去，內容就是四件事：用 Vite 建 React 19 專案、設定 proxy 把 `/api` 代理到 8080、建立一個有漸層 Header 跟毛玻璃卡片的頁面骨架、再加上之後會用到的骨架屏載入動畫，而且要求元件加中文註解。送出之後你會看到 AI 一步步建專案、改設定檔，最後它會告訴你怎麼啟動開發伺服器、怎麼確認 proxy 有生效。驗證的重點就兩個：頁面打得開，然後對 `/api` 的請求真的有被轉到後端。

總結一下：這一節我們把 Node.js 環境、Vite 建 React 19 專案的四個指令，還有解決 CORS 的 proxy 設定都搞定了，前端的地基已經打好。下一節我們要進到 React 的核心語言——JSX，看看元件到底是怎麼寫出來的。

（口語稿約 1530 字）
