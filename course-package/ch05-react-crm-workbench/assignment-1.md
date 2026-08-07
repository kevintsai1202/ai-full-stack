# 章節 5 作業 1｜完成前端基本架構

## 單元定位

本作業對應章節 5 的四個實作任務：完成後，你的手上會有一個環境正常、能跑 React 19、代理設定正確、視覺質感到位的前端基本架構，作為下一章接入 AI 對話功能的基礎。

## 作業說明

依序完成以下四項實作任務（對應 u5-t1 ~ u5-t4）：

1. **安裝 Node.js 並執行 npm 驗證**
   安裝 Node.js 執行環境，並在命令列執行 npm 相關指令確認安裝成功。

2. **使用 Vite 建立 React 19 專案並以 npm install 安裝**
   執行 `npx create-vite@latest frontend --template react` 建立專案，進入 `frontend` 目錄後執行 `npm install` 安裝依賴，並以 `npm run dev` 啟動開發伺服器（Port 5173）。

3. **配置 vite.config.js 的 server.proxy 代理**
   在 `vite.config.js` 設定 `server.proxy`，把所有 `/api` 開頭的請求代理到 `http://localhost:8080`，解決前後端分離的 CORS 問題。

4. **套用 CSS 動畫實作骨架屏與毛玻璃視覺效果**
   將 uiuxpromax 核心樣式（`.glass-card` 毛玻璃、`.gradient-header` 漸層 Header、`.micro-interaction` 微懸停動畫、`.skeleton-shimmer` 骨架屏）整合進 `src/index.css`（或 `App.css`），並在頁面骨架上實際套用。

**驗收標準**：

- `npm run dev` 後，瀏覽器打開 `http://localhost:5173` 能看到頁面。
- 對 `/api` 開頭的請求會被代理到 8080 的後端（proxy 生效）。
- 頁面上能看到漸層 Header 與毛玻璃卡片，懸停卡片有微動畫，載入區塊有骨架屏 shimmer 動畫。
- 元件與樣式具備中文註解。

## 口語稿

這一章的作業來了，題目是「完成前端基本架構」。內容就是把我們這幾節課做過的事情，在你自己的機器上完整走一遍，總共四個任務，我一個一個交代清楚。

第一個任務，安裝 Node.js 並且用 npm 驗證。這一步看起來簡單，但請你務必實際在命令列跑過 npm 指令、看到版本號或執行結果，確認環境真的是好的——前端所有後續動作都建立在這上面，環境有問題，後面全部卡住。

第二個任務，用 Vite 建立 React 19 專案。指令就是課堂上那一句：`npx create-vite@latest frontend --template react`，然後進到 frontend 目錄跑 `npm install` 把依賴裝好，再用 `npm run dev` 把開發伺服器跑起來。驗收的第一關就在這裡：瀏覽器打開 localhost:5173，要能看到你的頁面。

第三個任務，配置 vite.config.js 的 server.proxy。把所有 `/api` 開頭的請求代理到 localhost:8080 的 Spring Boot 後端。這一步是前後端能不能牽上線的關鍵，交作業之前請自己驗證一次：發一個 `/api` 開頭的請求，確認它真的被轉到後端，而不是在瀏覽器 Console 噴 CORS 紅字。

第四個任務，套用 CSS 動畫做出骨架屏跟毛玻璃效果。把課堂上那組 uiuxpromax 樣式整合進你的 index.css 或 App.css——毛玻璃卡片、漸層 Header、微懸停動畫、骨架屏 shimmer，四個都要，而且要真的掛到頁面元件上，不是貼在樣式表裡沒人用。驗收時我會看：卡片是不是半透明帶模糊、Header 有沒有靛藍到紫的漸層、滑鼠移上卡片會不會浮起來、載入區塊有沒有那道流動的光。

整體的驗收標準再重複一次：開發伺服器跑得起來、頁面打得開、proxy 代理有生效、四個視覺效果都在頁面上看得到，然後別忘了全課程的共同要求——元件跟樣式都要有中文註解。做這份作業的時候，鼓勵你直接用課堂上給的提示詞去指揮 AI Agent，但 AI 做完之後，每一項你都要親手驗證過才算數，這正是我們第一章講的協作三原則：先讀需求、產程式、然後驗證。

把這四件事做完，你就有了一個地基穩固、質感到位的前端架構。下一章我們就要在這個架構上，把 Spring AI 的對話能力接進來，讓你的 CRM 開始真正「思考」。加油，我們下一章見。

（口語稿約 960 字）
