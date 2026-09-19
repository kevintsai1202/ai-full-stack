# 章節 5 單元 2｜JSX 基本語法及結構

## 單元定位

上一單元用 Vite 把 React 19 專案建起來、也設定好 proxy 了，但打開 `src/` 目錄你會看到副檔名是 `.jsx` 的檔案，裡面 HTML 跟 JavaScript 混在一起——看不懂這個語法，就改不動任何畫面。本單元講清楚 JSX 的四條寫作規範與 Functional Component 的結構，讓你有能力閱讀並修改 AI 產出的元件程式碼。下一單元進入前端視覺優化。建議時長：10～12 分鐘。

## 教學素材

### JSX 是什麼

JSX 是 JavaScript 的語法擴充，允許我們在 JavaScript 中直接撰寫一種類似 HTML 的結構。React 19 推薦使用 Functional Component（函式元件）進行開發，相比舊版的 Class 元件更加簡潔。

### JSX 寫作規範與注意事項

1. 所有元件必須回傳「單一根節點」（若有多個元素，需用空標籤 `<></>` 包裹）。
2. 由於 `class` 在 JS 中是保留字，JSX 中必須改寫為 `className`。
3. HTML 事件綁定需改為 React 的小駝峰命名（例如 `onclick` 改為 `onClick`）。
4. 變數與邏輯表達式可直接放在大括號 `{}` 中進行求值與渲染。

### Functional Component 範例

```jsx
import React, { useState } from 'react';

// 宣告一個 Functional Component 元件
export default function Counter({ initialCount = 0 }) {
  // 使用 useState Hook 管理元件內部的狀態
  const [count, setCount] = useState(initialCount);

  return (
    <div className="counter-container">
      <h3>當前計數器：{count}</h3>
      {/* 點擊事件小駝峰命名，並使用大括號綁定 JavaScript 方法 */}
      <button onClick={() => setCount(count + 1)}>
        累加 +1
      </button>
    </div>
  );
}
```

範例重點：`useState` Hook 管理元件內部狀態；props（如 `initialCount`）由外部傳入並可設定預設值；點擊按鈕呼叫 `setCount` 更新狀態後，畫面上的 `{count}` 會自動重新渲染。

### 與 CRM 前端的關聯

CRM 工作台的每一張數字卡片、每一列客戶資料，本質上都是這樣的函式元件：接收 props、管理自己的狀態、回傳 JSX。看懂這個範例，就看得懂 AI 幫我們產生的所有頁面元件。

## 示範與提示詞

本單元以閱讀與修改 Counter 範例為主，示範沿用單元 1 建立的專案骨架。搭配的口語化建置提示詞（讓網頁接上後端並記住登入，為單元 4 的頁面實作鋪路）：

```text
請幫網頁接上後端：做一個登入頁呼叫 POST /api/auth/login，登入成功後把 token 與使用者資訊存在瀏覽器（localStorage），之後每次呼叫 API 都用 axios 攔截器自動帶上 Authorization: Bearer；用一個 AuthContext 讓所有頁面都知道目前登入的人與角色；沒登入的人開任何頁面都導回 /login；如果 API 回 401（登入過期），就自動清掉 token 並把我導回登入頁重新登入。側欄的「團隊分析」只有 MANAGER 與 ADMIN 看得到。請加繁體中文註解。
```

**驗證方式**：修改 Counter 的初始值或按鈕文字後存檔，瀏覽器頁面即時熱更新；登入成功後重新整理頁面身分仍在，之後的 API 請求都自動帶著 Token。

## 逐步操作與驗收

### 用小元件讀懂 JSX

1. 打開 Counter 元件，先找單一根節點、`className`、`onClick` 與大括號四項規則；每找到一項就修改一個最小例子並保存。
2. 將 `initialCount` 當作 props 傳入兩個 Counter，讓它們顯示不同初始值；再用 `useState` 點擊按鈕，觀察只有對應元件的狀態更新。
3. 把一組客戶陣列用 `map` 渲染成列表，補上穩定的 `key`；測試空陣列與缺少欄位時的防禦性渲染。
4. 執行 `npm run build`，修正 compiler 指出的 JSX 語法，再用瀏覽器 hot reload 確認畫面和程式碼同步；將 AI 產出的元件先閱讀再採用。

### 預期結果與證據

- 能修改 Counter、props、state 與 customer list；build 通過且 Console 沒有 key 或 runtime error。
- 交付前後 diff、瀏覽器操作錄影或截圖、build 輸出與一份 JSX 規則檢查表。

### 失敗分流與銜接

- 畫面空白先看編譯錯誤與 Console；list 出現重複或跳動先查 key；state 互相影響先查是否誤用共用變數。
- 下一單元會在這些元件上套用 CSS 和三態畫面，先保留可工作的 JSX 基線。

## 口語稿

上一節我們把 React 專案跑起來了，但如果你打開 `src` 目錄下的檔案，第一眼可能會有點錯亂：這是 JavaScript 檔案，裡面怎麼寫著一堆 HTML 標籤？別懷疑，這就是 JSX。為什麼這一節非講不可？因為接下來整章，AI 會幫我們產出一大堆元件程式碼，如果你看不懂 JSX，AI 寫出來的東西你就只能全盤接受、不敢動任何一行——那就不是駕馭 AI，是被 AI 駕馭了。所以這一節的目標很務實：讓你能讀懂、也敢修改這些元件。

JSX 本質上是 JavaScript 的語法擴充，讓我們可以在 JS 裡直接寫類似 HTML 的結構，畫面跟邏輯放在同一個地方。而 React 19 推薦的寫法是 Functional Component，也就是函式元件——一個元件就是一個 JavaScript 函式，比舊版的 Class 元件簡潔非常多。

不過 JSX 畢竟不是真的 HTML，有四條規範你一定要記住，不然編譯器會直接跟你抗議。第一條：每個元件只能回傳「單一根節點」。意思是你的 return 裡面最外層只能有一個標籤，如果真的需要並排放好幾個元素，就用一對空標籤，`<>` 跟 `</>`，把它們包起來。第二條：HTML 的 `class` 屬性要改寫成 `className`。原因很單純，`class` 在 JavaScript 是保留字，拿去宣告類別用的，所以 JSX 只好換個名字。這是新手最常犯的錯，寫了 `class` 樣式卻沒生效，八成就是這裡。第三條：事件綁定要改成小駝峰命名，HTML 裡的 `onclick` 全小寫，到了 JSX 要寫成 `onClick`，C 大寫。第四條，也是最好用的一條：大括號 `{}` 裡面可以直接放 JavaScript 的變數跟邏輯表達式，React 會把求值結果渲染到畫面上。

我們現在來看一個完整的例子，Counter 計數器元件，四條規範它全用上了。你會看到第一行從 react 引入了 `useState`，這是一個 Hook，用來管理元件內部的狀態。接著宣告一個函式，名字叫 Counter，用 `export default` 匯出——這就是一個元件了。注意它的參數 `initialCount = 0`，這叫 props，是外部傳進來的資料，等號後面是預設值。函式裡面第一行，`const [count, setCount] = useState(initialCount)`，左邊解構出兩個東西：`count` 是目前的狀態值，`setCount` 是更新它的函式。然後看 return 的部分：最外層一個 div，符合單一根節點；div 上寫的是 `className`，不是 class；h3 裡面用大括號把 `{count}` 嵌進文字裡；按鈕上綁的是 `onClick`，裡面放一個箭頭函式呼叫 `setCount(count + 1)`。

這裡有個 React 最核心的觀念：當你呼叫 `setCount`，React 就知道狀態變了，會自動重新渲染這個元件，畫面上的數字就跟著更新。你不需要自己去抓 DOM、改 innerHTML——狀態改變，畫面自動跟上，這就是 React 的思維。

我們現在來實際動手改改看。你打開上一節建好的專案，隨便挑一個元件，把文字改掉、或是把計數器的初始值改成 100，存檔。你會看到瀏覽器的畫面「啪」一下就更新了，連重新整理都不用，這是 Vite 的熱更新。這個「改一行、看一眼」的循環，就是你之後審查 AI 產出程式碼的日常。

為什麼這個範例對我們的 CRM 這麼重要？因為工作台上的每一個東西——Dashboard 的每一張數字卡片、客戶列表的每一列、看板上的每一張商機卡——本質上全都是這樣的函式元件：接收 props、管理自己的狀態、回傳 JSX。看懂了 Counter，你就看懂了整個前端專案的基本單位。

總結一下：JSX 四條規範——單一根節點、className、事件小駝峰、大括號放表達式——加上 Functional Component 跟 useState，這就是你讀懂 React 19 程式碼的鑰匙。下一節我們來處理「好看」這件事：用 uiuxpromax 的視覺優化指引，讓工作台徹底告別單調的 MVP 樣式。

（口語稿約 1650 字）
