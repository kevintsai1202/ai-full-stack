# 章節 5 單元 3｜前端視覺優化指引

## 單元定位

前兩單元把專案架起來、也看懂了 JSX，但此時的畫面還是素到不行的 MVP 樣式。本單元引入 uiuxpromax 的設計哲學，用 Vanilla CSS 做四件事：毛玻璃卡片、漸層 Header、微懸停動畫、骨架屏載入動畫——其中骨架屏正是 loading 狀態的標準處理方式，直接服務「loading / error / empty 三態」的核心原則。下一單元進入 CRM 工作台的頁面設計思維。建議時長：10～12 分鐘。

## 教學素材

### uiuxpromax 設計哲學

一個優秀的 Web 應用不僅要能跑，更要能 WOW 使用者。我們採用 uiuxpromax 的設計哲學，利用 Vanilla CSS 優化整體視覺，徹底告別單調的 MVP 樣式：

- **毛玻璃效果 (Glassmorphism)**：卡片使用 `backdrop-filter: blur(14px)` 搭配半透明邊框，營造精緻浮空感。
- **漸層極光配色**：Header 使用 `linear-gradient(135deg, indigo, purple)` 漸層配色，搭配狀態指示燈 (Pulse LED) 動態閃爍。
- **微懸停動畫 (Micro-interactions)**：滑鼠懸停於客戶摘要、待辦任務卡片時，加入 `transform: translateY(-4px) scale(1.01)` 與 `transition`，讓卡片活起來。
- **骨架屏載入動畫 (Skeleton Screen)**：資料載入中（例如 AI 正在思考或呼叫 Tool）時，顯示灰白色的骨架屏閃爍 (shimmer keyframe)，大幅降低等待期間的無聊感。

### uiuxpromax 的「安裝」與配置方式

`uiuxpromax` 並非傳統的 npm 第三方套件，因此**不需要執行 `npm install`**。它的「安裝與引入方式」是將以下精心調校的 Vanilla CSS 樣式直接整合進 React 專案的 `src/index.css`（或 `App.css`）中，讓元件直接透過 `className` 引用：

```css
/* uiuxpromax 核心樣式配置 */
.glass-card {
  background: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.07);
}

.gradient-header {
  background: linear-gradient(135deg, #4f46e5, #9333ea);
}

.micro-interaction {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.micro-interaction:hover {
  transform: translateY(-4px) scale(1.01);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.skeleton-shimmer {
  background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
}
```

### 與三態渲染的關係

前後端整合的核心原則之一是防禦性渲染：UI 必須對 Loading、Error 及 Empty 三種狀態完整處理。骨架屏（`.skeleton-shimmer`）就是 Loading 狀態的標準畫面——比空白頁或轉圈圈更能降低等待焦慮；Error 與 Empty 則各自需要清楚的提示畫面（單元 4 的頁面實作提示詞中會明確要求這三態）。

## 示範與提示詞

視覺骨架的建置提示詞（與單元 1 的骨架提示詞相同，本節聚焦其中的視覺要求）：

```text
請幫我做出這套系統的網頁畫面外觀：要有現代感的設計（例如漸層的標題列、卡片式的區塊、載入時有過場動畫），先把整體版面骨架架好，內容之後再填。請加中文註解，完成後告訴我怎麼打開來看。
```

**驗證方式**：頁面上能看到漸層 Header 與毛玻璃卡片；滑鼠懸停卡片會浮起；模擬資料載入中時出現 shimmer 骨架屏動畫。

## 口語稿

前兩節做完，我們的專案能跑、程式碼也看得懂了，但你打開畫面看一眼——白底黑字、方方正正，像是上個世紀的系統。你可能會說，能用就好啊？我要講一個很現實的事：這套 CRM 最終是要給業務同仁天天用的，第一眼的觀感直接決定他們願不願意買單。一個看起來廉價的系統，就算功能再強，使用者也會下意識地不信任它。所以這一節我們專門來處理「好看」這件事——一個優秀的 Web 應用不只要能跑，更要能 WOW 使用者。

我們採用的是 uiuxpromax 的設計哲學。先講一個很多人會誤會的點：uiuxpromax 不是一個 npm 套件，你不需要、也沒辦法 `npm install` 它。它的「安裝方式」其實是把一組精心調校過的 Vanilla CSS 樣式，直接整合到 React 專案的 `src/index.css` 或 `App.css` 裡面，之後元件要用的時候，掛上對應的 `className` 就好。純 CSS，不引入任何第三方 UI 框架，這也表示你對每一行樣式都有完全的掌控權。

這套指引有四個核心招式，我們一個一個看。第一招，毛玻璃效果，英文叫 Glassmorphism。關鍵就是 `backdrop-filter: blur(14px)` 這個屬性，讓卡片背後的內容透出來但是糊掉的，再搭配半透明的白色背景跟半透明邊框，整張卡片就有一種精緻的浮空感。你看樣式表裡的 `.glass-card`，就這幾行，質感立刻不一樣。第二招，漸層極光配色。Header 不要用死板的單色，改用 `linear-gradient(135deg)` 從靛藍漸變到紫色，也就是 #4f46e5 到 #9333ea，再搭配一顆會動態閃爍的狀態指示燈，像 Pulse LED 那樣一亮一暗，整個頁面的頂部馬上就有了科技感。

第三招，微懸停動畫，Micro-interactions。概念是：當滑鼠懸停在客戶摘要或待辦任務卡片上的時候，卡片要有反應。做法是 hover 的時候套 `transform: translateY(-4px) scale(1.01)`——往上浮 4 個像素、微微放大百分之一——再配上 0.2 秒的 transition 讓動作滑順。就這麼一點點位移，卡片就「活」起來了，使用者會很清楚知道現在滑到的是哪一張、哪裡可以點。

第四招是我認為最重要的：骨架屏，Skeleton Screen。想一個情境：使用者按下查詢，後端要花一兩秒才回資料——或者到了下一章，AI 正在思考、正在呼叫工具，等更久——這段時間畫面該長什麼樣？空白一片，使用者會以為當掉了。我們的做法是先顯示灰白色的骨架屏，用 shimmer 這個 keyframe 動畫讓一道光從左掃到右，不斷閃爍。原理你看 CSS 就懂：一條三段式的灰色漸層，把 `background-size` 拉到兩倍寬，然後用動畫去平移 `background-position`，視覺上就是光在流動。這個閃爍在心理上傳達一個訊息：「系統活著，正在幫你忙」，能大幅降低等待的無聊感。

這裡順便把一個貫穿前後端整合的原則講清楚：防禦性渲染。任何跟後端要資料的畫面，都有三種狀態要處理——載入中（Loading）、載入失敗（Error）、查無資料（Empty）。骨架屏就是 Loading 狀態的標準答案；Error 跟 Empty 也各自要有清楚的提示畫面，不能讓使用者對著空白發呆。下一節寫頁面的時候，我們會在提示詞裡明確要求 AI 把這三態全部做出來。

我們現在來驗證一下成果。把這組 CSS 整合進專案之後，重新整理頁面，你會看到：Header 是靛藍到紫的漸層，卡片是半透明的毛玻璃，滑鼠移上去卡片會輕輕浮起來，模擬載入的區塊有 shimmer 光在流動。同一個頁面骨架，質感跟十分鐘前完全是兩個世界。

總結一下：毛玻璃、漸層 Header、微懸停動畫、骨架屏，四招 Vanilla CSS 讓工作台徹底告別 MVP 樣式，其中骨架屏更是三態渲染裡 Loading 的標準處理。下一節我們把視野拉高，來設計 CRM 工作台的完整頁面架構，並且把真實的後端資料接進來。

（口語稿約 1620 字）
