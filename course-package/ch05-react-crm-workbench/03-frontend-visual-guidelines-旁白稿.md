# 03-frontend-visual-guidelines｜前端視覺優化指引旁白稿

本稿依據 `03-frontend-visual-guidelines.md` 的教學素材與口語稿整理，供人工錄音使用。共 8 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜為什麼要在意「好看」

建議檔名：`01-why-visual-matters.wav`

前兩節做完，我們的專案能跑、程式碼也看得懂了，但你打開畫面看一眼——白底黑字、方方正正，像上個世紀的系統。你可能會說，能用就好啊？我要講一個很現實的事：這套 CRM 最終是要給業務同仁天天用的，第一眼的觀感直接決定他們願不願意買單。一個看起來廉價的系統，就算功能再強，使用者也會下意識地不信任它。所以這一節我們專門處理「好看」這件事——一個優秀的 Web 應用不只要能跑，更要能 WOW 使用者。

## 02｜uiuxpromax 不是 npm 套件

建議檔名：`02-uiuxpromax-not-npm.wav`

我們採用的是 uiuxpromax 的設計哲學。先澄清一個很多人會誤會的點：uiuxpromax 不是一個 npm 套件，你不需要、也沒辦法 npm install 它。它的「安裝方式」，是把一組精心調校過的 Vanilla CSS 樣式，直接整合到 React 專案的 src/index.css 或 App.css 裡，之後元件掛上對應的 className 就能用。純 CSS，不引入任何第三方 UI 框架——這也代表你對每一行樣式都有完全的掌控權。完整的 CSS 不用抄，教學網站上可以直接複製。

## 03｜第一招：毛玻璃效果

建議檔名：`03-glassmorphism.wav`

這套指引有四個核心招式。第一招，毛玻璃效果，Glassmorphism。關鍵是 backdrop-filter blur 14 像素這個屬性，讓卡片背後的內容透出來、但是糊掉的，再搭配半透明的白色背景跟半透明邊框，整張卡片就有一種精緻的浮空感。你看樣式表裡的 glass-card，就這幾行，質感立刻不一樣。

## 04｜第二招：漸層極光配色

建議檔名：`04-gradient-header.wav`

第二招，漸層極光配色。Header 不要用死板的單色，改用 135 度的 linear-gradient，從靛藍漸變到紫色，再搭配一顆會動態閃爍的狀態指示燈，像 Pulse LED 那樣一亮一暗，頁面頂部馬上就有了科技感。色碼不用記，網站上查得到，你要記的是這個設計意圖：頂部是使用者的第一眼，值得多花一點心思。

## 05｜第三招：微懸停動畫

建議檔名：`05-micro-interactions.wav`

第三招，微懸停動畫，Micro-interactions。概念是：滑鼠懸停在客戶摘要或待辦任務卡片上的時候，卡片要有反應。做法是 hover 時套 transform，往上浮 4 個像素、微微放大百分之一，再配上 0.2 秒的 transition 讓動作滑順。就這麼一點點位移，卡片就「活」起來了，使用者會很清楚知道現在滑到的是哪一張、哪裡可以點。

## 06｜第四招：骨架屏

建議檔名：`06-skeleton-shimmer.wav`

第四招是我認為最重要的：骨架屏，Skeleton Screen。想一個情境：使用者按下查詢，後端要一兩秒才回資料——或者到了下一章，AI 正在思考、正在呼叫工具，等更久——這段時間畫面該長什麼樣？空白一片，使用者會以為當掉了。我們的做法是先顯示灰白色的骨架屏，用 shimmer 動畫讓一道光從左掃到右。原理看 CSS 就懂：一條三段式的灰色漸層，把 background-size 拉到兩倍寬，再用動畫平移 background-position，視覺上就是光在流動。這個閃爍在心理上傳達一個訊息：「系統活著，正在幫你忙」，能大幅降低等待的無聊感。

## 07｜防禦性渲染與三態

建議檔名：`07-three-states.wav`

這裡順便把一個貫穿前後端整合的原則講清楚：防禦性渲染。任何跟後端要資料的畫面，都有三種狀態要處理——載入中 Loading、載入失敗 Error、查無資料 Empty。骨架屏就是 Loading 狀態的標準答案；Error 跟 Empty 也各自要有清楚的提示畫面，不能讓使用者對著空白發呆。下一節寫頁面的時候，我們會在提示詞裡明確要求 AI 把這三態全部做出來。

## 08｜驗證成果與下一節

建議檔名：`08-verify-and-next.wav`

我們來驗證成果。把這組 CSS 整合進專案後重新整理頁面，你應該看到：Header 是靛藍到紫的漸層，卡片是半透明的毛玻璃，滑鼠移上去卡片會輕輕浮起來，模擬載入的區塊有 shimmer 光在流動。同一個頁面骨架，質感跟十分鐘前完全是兩個世界。總結一下：毛玻璃、漸層 Header、微懸停動畫、骨架屏，四招 Vanilla CSS 讓工作台徹底告別 MVP 樣式，其中骨架屏更是三態渲染裡 Loading 的標準處理。下一節我們把視野拉高，設計 CRM 工作台的完整頁面架構，並把真實的後端資料接進來。我們下一節見。

## 邊念邊操作提示卡

1. 打開 CSS，依序套用玻璃卡、漸層 Header、hover 和 skeleton；每加一個 class 就切回瀏覽器確認。
2. 示範 loading、error、empty、success 四個狀態，停在每個畫面讓學員比較，避免只顯示成功資料。
3. 用窄視窗和鍵盤操作檢查 focus、overflow、對比度；若動畫造成不適，展示 reduced-motion 的處理位置。
4. 結尾執行 build，保存畫面和 CSS diff，並提醒 uiuxpromax 在本課程是 CSS 指引。

## 交付檔案命名

```text
01-why-visual-matters.wav
02-uiuxpromax-not-npm.wav
03-glassmorphism.wav
04-gradient-header.wav
05-micro-interactions.wav
06-skeleton-shimmer.wav
07-three-states.wav
08-verify-and-next.wav
```
