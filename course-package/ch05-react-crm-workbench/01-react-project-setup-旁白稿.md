# 01-react-project-setup｜React 專案建立旁白稿

本稿依據 `01-react-project-setup.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜幫後端裝上一張臉

建議檔名：`01-backend-needs-a-face.wav`

歡迎來到第五章。先把進度對一下：前面四章，我們已經把 CRM 的後端整個做起來了——REST API 有了、資料真的存進 PostgreSQL、還加上 Spring Security 跟 JWT 的保護，連 API 文件都有。功能其實很完整，但你有沒有發現，我們每次驗證功能，都是開 PowerShell 敲指令，或是去 Swagger 按按鈕。你想像一下，把這套系統交給業務同仁，跟他說「查客戶要先開終端機」——他大概明天就離職了。所以這一章，我們要幫這個後端裝上一張臉：打造一個業務人員真的能用的 CRM 工作台前端。

## 02｜Node.js 與 NPM 的角色

建議檔名：`02-node-npm.wav`

要做前端，第一步是把環境架起來。這一節先認識兩個東西：Node.js 跟 NPM。Node.js 是前端開發的執行環境，NPM 是它的套件管理工具。這裡有一個觀念上的轉變要提醒：React 19 的開發，不再像早期那樣手動下載一個 JS 檔、用 script 標籤引進來，而是全部透過 NPM 安裝相依套件、管理版本。這跟你在後端用 Maven 管理依賴是一模一樣的思路——版本交給工具管，不要手動搬檔案。

## 03｜用 Vite 建立 React 19 專案

建議檔名：`03-vite-create-project.wav`

環境有了，我們用 Vite 來建專案。Vite 是目前業界主流的建置工具，特色就是快，開發伺服器幾乎秒開。整個流程就四個指令：先用 npx 執行 create-vite，套用 react 模板，建出一個叫 frontend 的專案；接著 cd 進目錄；再 npm install 把 React 19 的相依套件裝好——裝的是最新、沒有資安漏洞的版本；最後 npm run dev。你會看到終端機顯示開發伺服器跑在 Port 5173，瀏覽器打開 localhost 5173，看到 Vite 加 React 的預設畫面，前端專案就算活了。指令不用抄，教學網站上都可以直接複製。

## 04｜第一個坑：CORS

建議檔名：`04-cors-problem.wav`

不過馬上會撞到第一個坑，而且是每個做前後端分離的人都一定會遇到的：CORS。你想，前端跑在 5173，Spring Boot 後端跑在 8080，在瀏覽器眼中這是兩個不同的來源。前端直接對 8080 發非同步請求的時候，瀏覽器的「同源政策」就會跳出來把它擋掉，Console 出現紅字的 CORS 錯誤。重點是理解為什麼會被擋：不是程式寫錯，是瀏覽器的安全機制在做它該做的事。

## 05｜用 Vite Proxy 解決

建議檔名：`05-vite-proxy.wav`

那怎麼解？我們不去動後端，而是在 vite.config.js 裡設定 server.proxy：告訴 Vite，凡是以 /api 開頭的請求，開發環境下都自動轉發到 localhost 8080。這樣有兩個好處。第一，前端程式碼只要寫相對路徑，像 /api/customers，不用把主機位址寫死。第二，因為請求是由 Vite 伺服器代轉的，瀏覽器根本不覺得跨域，後端也完全不需要配置那些繁瑣的 CORS 設定。設定檔的內容，教學網站上查得到，你只要記得這個轉發的概念就好。

## 06｜把建置交給 AI Agent

建議檔名：`06-ai-scaffold.wav`

我們現在把這整套流程交給 AI Agent 做。教材裡的提示詞講了四件事：用 Vite 建 React 19 專案、設定 proxy 把 /api 代理到 8080、建一個有漸層 Header 跟毛玻璃卡片的頁面骨架、再加上之後聊天室會用到的骨架屏載入動畫，而且要求元件加中文註解。提示詞不用抄，網站上直接複製。送出之後你會看到 AI 一步步建專案、改設定檔，最後告訴你怎麼啟動、怎麼確認 proxy 有生效。你的工作不是打指令，是確認它做的每一步符合我們剛剛講的架構。

## 07｜驗證與下一節

建議檔名：`07-verify-and-next.wav`

驗收重點就兩個：第一，npm run dev 之後，瀏覽器打開 5173 能看到頁面骨架；第二，對 /api 開頭的路徑發請求，確認 Vite 真的把它代理到 8080 的後端。總結一下：這一節我們搞定了 Node.js 環境、Vite 建 React 19 專案的四個指令，還有解決 CORS 的 proxy 設定，前端的地基已經打好。下一節我們進到 React 的核心語言——JSX，看看元件到底是怎麼寫出來的。我們下一節見。

## 邊念邊操作提示卡

1. 先在終端機執行 Node 和 npm 版本，再執行 Vite 建立命令；念到 `npm run dev` 時切到瀏覽器 5173，停下來等畫面載入。
2. 打開 `vite.config.js` 指向 proxy，接著用 Network 面板發一個 `/api` 請求；若後端未啟動，保留 connection refused 並說明原因。
3. 示範 Header、卡片、聊天占位和 skeleton，先看畫面再接 API；旁白提醒不要把 `node_modules` 放進交付。
4. 最後執行 `npm run build`，保存 terminal 和瀏覽器證據。

## 交付檔案命名

```text
01-backend-needs-a-face.wav
02-node-npm.wav
03-vite-create-project.wav
04-cors-problem.wav
05-vite-proxy.wav
06-ai-scaffold.wav
07-verify-and-next.wav
```
