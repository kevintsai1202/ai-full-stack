# 02-jsx-basics｜JSX 基本語法及結構旁白稿

本稿依據 `02-jsx-basics.md` 的教學素材與口語稿整理，供人工錄音使用。共 7 段，錄音時可依段落分開保存，方便後續與投影片時間軸對齊。

建議錄音格式：WAV 或 MP3。每段開頭保持自然，結尾保留約 0.5 秒空白。

## 01｜為什麼非懂 JSX 不可

建議檔名：`01-why-jsx.wav`

上一節我們把 React 專案跑起來了，但如果你打開 src 目錄，第一眼可能會有點錯亂：這是 JavaScript 檔案，裡面怎麼寫著一堆 HTML 標籤？別懷疑，這就是 JSX。為什麼這一節非講不可？因為接下來整章，AI 會幫我們產出一大堆元件程式碼，如果你看不懂 JSX，AI 寫的東西你就只能全盤接受、不敢動任何一行——那就不是駕馭 AI，是被 AI 駕馭了。所以這一節的目標很務實：讓你能讀懂、也敢修改這些元件。

## 02｜JSX 與 Functional Component

建議檔名：`02-jsx-and-functional-component.wav`

JSX 本質上是 JavaScript 的語法擴充，讓我們可以在 JS 裡直接寫類似 HTML 的結構，畫面跟邏輯放在同一個地方。而 React 19 推薦的寫法是 Functional Component，函式元件——一個元件就是一個 JavaScript 函式，比舊版的 Class 元件簡潔非常多。你只要記住這個心智模型：元件收資料進來、回傳畫面出去，就是一個函式。

## 03｜四條寫作規範

建議檔名：`03-four-rules.wav`

不過 JSX 畢竟不是真的 HTML，有四條規範一定要記住，不然編譯器會直接抗議。第一條，每個元件只能回傳單一根節點；真的要並排放多個元素，就用一對空標籤把它們包起來。第二條，HTML 的 class 要改寫成 className——因為 class 在 JavaScript 是保留字。這是新手最常犯的錯：寫了 class 樣式卻沒生效，八成就是這裡。第三條，事件綁定改成小駝峰命名，onclick 要寫成 onClick，C 大寫。第四條，也是最好用的：大括號裡可以直接放 JavaScript 的變數跟邏輯表達式，React 會把求值結果渲染到畫面上。

## 04｜Counter 範例怎麼讀

建議檔名：`04-counter-example.wav`

我們來看一個完整的例子：Counter 計數器元件，四條規範它全用上了。程式碼不用抄，教學網站上有完整原文，我帶你看重點就好。它從 react 引入 useState，這是一個 Hook，用來管理元件內部的狀態。函式的參數 initialCount 叫做 props，是外部傳進來的資料，還可以設預設值。useState 解構出兩個東西：count 是目前的狀態值，setCount 是更新它的函式。再看 return：最外層一個 div，符合單一根節點；上面掛的是 className；標題用大括號把 count 嵌進文字；按鈕綁的是 onClick，裡面一個箭頭函式呼叫 setCount 加一。

## 05｜狀態改變，畫面自動跟上

建議檔名：`05-state-drives-ui.wav`

這裡有個 React 最核心的觀念：當你呼叫 setCount，React 就知道狀態變了，會自動重新渲染這個元件，畫面上的數字跟著更新。你不需要自己去抓 DOM、改 innerHTML——狀態改變，畫面自動跟上，這就是 React 的思維。理解了這一點，你之後看任何 React 程式碼，都知道要先找「狀態放在哪、誰改了它」。

## 06｜動手改改看

建議檔名：`06-hot-reload-practice.wav`

我們現在實際動手改改看。打開上一節建好的專案，隨便挑一個元件，把文字改掉，或是把計數器的初始值改成 100，存檔。你會看到瀏覽器的畫面「啪」一下就更新了，連重新整理都不用，這是 Vite 的熱更新。這個「改一行、看一眼」的循環，就是你之後審查 AI 產出程式碼的日常。為什麼這個範例對 CRM 這麼重要？因為工作台上的每一張數字卡片、客戶列表的每一列、看板上的每一張商機卡，本質上全都是這樣的函式元件：接收 props、管理自己的狀態、回傳 JSX。看懂了 Counter，你就看懂了整個前端專案的基本單位。

## 07｜本節總結與下一節

建議檔名：`07-closing.wav`

總結一下：JSX 四條規範——單一根節點、className、事件小駝峰、大括號放表達式——加上 Functional Component 跟 useState，這就是你讀懂 React 19 程式碼的鑰匙。細節不用背，網站查得到，你要帶走的是「為什麼要這樣寫」。下一節我們來處理「好看」這件事：用 uiuxpromax 的視覺優化指引，讓工作台徹底告別單調的 MVP 樣式。我們下一節見。

## 邊念邊操作提示卡

1. 打開 Counter，依序圈出單一根節點、`className`、`onClick` 和大括號；每圈一項就停下來讓學員跟改。
2. 修改 props 的初始值，再點擊按鈕觀察 state；旁白念到「只有這個元件更新」時停在畫面。
3. 把 customer 陣列用 `map` 渲染，示範穩定 key、空陣列與缺欄位；保留 Console 警告或成功結果。
4. 執行 build 並展示 hot reload，說明這套讀碼流程會用來審查 AI 元件。

## 交付檔案命名

```text
01-why-jsx.wav
02-jsx-and-functional-component.wav
03-four-rules.wav
04-counter-example.wav
05-state-drives-ui.wav
06-hot-reload-practice.wav
07-closing.wav
```
