# teaching-site 提示詞重構 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 teaching-site 8 個單元的 AI 協作提示詞改寫為「目標 + 點名技術 + 少量關鍵約束 + 驗收」的乾淨有序卡片，移除實作細節與說明散文，並修正資料雙軌與驗證規則。

**Architecture:** 以 `course.day1.units` + `course.day2.units` 為唯一權威來源。每個 unit 的 `prompts[]` 改為帶 `kind`（build/verify/fix）的有序卡片；移除與 `prompt` 重複的大師範本卡；`app.js` 簡化渲染並加 verify 徽章；`verify-site.mjs` 改讀 day units 並換新規則；移除頂層死資料 `course.units`。

**Tech Stack:** 純 HTML + vanilla JS（無框架、無 build），Node.js 驗證腳本（vm 載入 course-data.js），Playwright 渲染驗證。

設計來源：`docs/superpowers/specs/2026-06-19-teaching-site-prompt-refactor-design.md`

---

## 重要慣例

- 所有編輯在 `teaching-site/` 下。
- `course-data.js` 是 JSON-like JS 物件（`window.COURSE = {...}`），逗號陷阱要小心：在物件/陣列中段插入或調整時確認逗號正確。
- 每個 unit 的提示詞卡 `text` **禁止**包含三反引號 ```` ``` ````（不貼程式碼）、禁止編號實作步驟清單、禁止指定類別/方法/欄位名稱。
- port / 資料庫名保留教學預設值：backend 8080、PostgreSQL 5432、frontend 5173、DB 名 `learn_spring`。
- API Key / embedding 金鑰一律寫「從環境變數讀，不要寫死」。
- 驗證指令（Windows）：`cd teaching-site` 後跑 `node scripts/verify-site.mjs` 與 `node scripts/verify-render.mjs`。

---

## Task 1: app.js 提示詞渲染簡化 + verify 徽章

**Files:**
- Modify: `teaching-site/app.js:760-767`（renderPromptBox）
- Modify: `teaching-site/app.js:769-796`（renderPromptAccordion 卡片組裝）

- [ ] **Step 1: 改寫 renderPromptBox 支援 kind 徽章**

把 `app.js:760-767` 的 `renderPromptBox` 改為接受 `kind`，verify 卡加 `is-verify` class：

```js
/** 可複製的 AI 協作提示詞；kind 控制徽章樣式（build/verify/fix） */
function renderPromptBox(title, note, text, key, kind) {
  const cls = kind === "verify" ? " is-verify" : (kind === "fix" ? " is-fix" : "");
  return `<div class="prompt-box${cls}">
    <div class="prompt-head"><div><strong>${esc(title)}</strong>${note ? `<span>${esc(note)}</span>` : ""}</div>
      <button class="prompt-copy-btn" type="button" data-action="copy-prompt" data-prompt-key="${esc(key)}" data-label="複製提示詞">複製提示詞</button>
    </div>
    <pre><code>${esc(text)}</code></pre>
  </div>`;
}
```

- [ ] **Step 2: 簡化 renderPromptAccordion 卡片組裝**

把 `app.js:773-803`（從 `const promptCards = [];` 到 `cardsHtml` 那行之前）整段邏輯，替換為：當 `unit.prompts` 存在時只依序渲染這些卡（不再注入大師範本卡）；沒有 `prompts` 時才退回用 `effectivePrompt` 顯示單卡。

```js
  const promptCards = [];
  if (Array.isArray(unit.prompts) && unit.prompts.length) {
    // 新結構：依序渲染 prompts[]，保留平台文字轉換，攜帶 kind 供徽章使用
    unit.prompts.forEach((p) => {
      promptCards.push({
        title: translatePlatformText(p.title, platform),
        note: translatePlatformText(p.note, platform),
        text: translatePlatformText(p.text, platform),
        kind: p.kind || "build"
      });
    });
  } else if (effectivePrompt) {
    // 舊結構退回：整章總提示詞單卡
    const platformLabel = hasOsSpecific ? (platform === "mac" && unit.promptMac ? "（macOS 版）" : "（Windows 版）") : "";
    promptCards.push({ title: translatePlatformText(`AI 協作提示詞 ${platformLabel}`, platform).trim(), note: "", text: effectivePrompt, kind: "build" });
  }

  if (!promptCards.length) return "";

  // 將提示詞文字存入全域 map 供複製時取用
  promptCards.forEach((card, i) => { promptTextMap[`${unit.id}-${i}`] = card.text; });

  const cardsHtml = promptCards.map((card, i) => renderPromptBox(card.title, card.note, card.text, `${unit.id}-${i}`, card.kind)).join("");
```

> 註：刪除 `hasPlaceholder` 分支與 `alreadyContainsEffective` 邏輯。保留上方 `hasOsSpecific`、`effectivePrompt` 兩行宣告（`app.js:771-772`）與下方 `platformBar`（`app.js:804-808`）不動。

- [ ] **Step 3: 語法檢查**

Run: `cd teaching-site && node -e "require('./app.js')" 2>&1 | head -5`
Expected: 可能因瀏覽器 API（document）報錯，但**不可**出現 `SyntaxError`。改用純語法檢查：
Run: `cd teaching-site && node --check app.js`
Expected: 無輸出（語法正確）。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/app.js
git commit -m "refactor(teaching-site): 簡化提示詞渲染並支援 kind 徽章"
```

---

## Task 2: styles.css 新增 verify / fix 徽章樣式

**Files:**
- Modify: `teaching-site/styles.css:1161-1172`（`.prompt-head strong::before` 區附近）

- [ ] **Step 1: 新增徽章變體樣式**

在 `styles.css` `.prompt-head strong::before`（約 1172 行結尾）之後插入：

```css
/* 驗證提示詞徽章：綠色，標示「驗證」 */
.prompt-box.is-verify {
  border-left-color: oklch(72% 0.17 150);
}
.prompt-box.is-verify .prompt-head strong::before {
  content: "驗證";
  background: oklch(72% 0.17 150);
}
/* 排錯提示詞徽章：琥珀色，標示「排錯」 */
.prompt-box.is-fix {
  border-left-color: oklch(75% 0.15 70);
}
.prompt-box.is-fix .prompt-head strong::before {
  content: "排錯";
  background: oklch(75% 0.15 70);
}
```

- [ ] **Step 2: Commit**

```bash
git add teaching-site/styles.css
git commit -m "style(teaching-site): 新增驗證/排錯提示詞徽章"
```

---

## Task 3: verify-site.mjs 改讀 day units + 換新規則（先寫測試規則，預期失敗）

**Files:**
- Modify: `teaching-site/scripts/verify-site.mjs:51-72`

- [ ] **Step 1: 改寫驗證主體**

把 `verify-site.mjs:51-72`（從 `if (!course.title...` 到 `console.log(...)` 結束）替換為：

```js
  if (!course.title.includes("AI 賦能全端開發")) errors.push("course title mismatch");

  // 畫面真正渲染來源：day1.units + day2.units
  const allUnits = [...(course.day1?.units || []), ...(course.day2?.units || [])];
  if (allUnits.length !== 8) errors.push(`course must contain 8 units (got ${allUnits.length})`);

  for (const unit of allUnits) {
    if (!unit.principle || unit.principle.length < 80) errors.push(`${unit.id} principle is too thin`);
    if (!Array.isArray(unit.illustrations) || unit.illustrations.length < 3) errors.push(`${unit.id} needs hero, diagram and term illustrations`);

    const prompts = Array.isArray(unit.prompts) ? unit.prompts : [];
    const builds = prompts.filter((p) => (p.kind || "build") === "build");
    const verifies = prompts.filter((p) => p.kind === "verify");
    if (!builds.length) errors.push(`${unit.id} needs at least one build prompt`);
    if (!verifies.length) errors.push(`${unit.id} needs at least one verify prompt`);
    // 提示詞不得貼程式碼（三反引號）
    for (const p of prompts) {
      if (p.text && p.text.includes("```")) errors.push(`${unit.id} prompt "${p.title}" must not contain code fences`);
    }

    for (const illustration of unit.illustrations || []) {
      const asset = path.join(root, "assets", "illustrations", illustration.name);
      if (!(await exists(asset))) errors.push(`${unit.id} missing illustration: ${illustration.name}`);
    }
  }

  if (errors.length) {
    console.error(errors.join("\n"));
    process.exitCode = 1;
    return;
  }

  const assetCount = allUnits.reduce((sum, unit) => sum + (unit.illustrations?.length || 0), 1);
  console.log(`OK: ${allUnits.length} units, ${assetCount} referenced visual assets verified.`);
```

- [ ] **Step 2: 跑驗證，預期因新規則失敗**

Run: `cd teaching-site && node scripts/verify-site.mjs`
Expected: FAIL，列出多個 `uN needs at least one build/verify prompt` 與 `must not contain code fences`（因 unit 尚未改寫）。這證明新規則生效。

- [ ] **Step 3: Commit**

```bash
git add teaching-site/scripts/verify-site.mjs
git commit -m "test(teaching-site): verify-site 改讀 day units 並換提示詞新規則"
```

---

## Task 4: 改寫 u1 提示詞（開發環境、專案骨架與 AI 協作流程）

**Files:**
- Modify: `teaching-site/course-data.js`（u1 的 `prompt`/`promptMac`/`prompts`，約 262-321 行區段）

- [ ] **Step 1: 將 u1 的 `prompts` 改為下列陣列**

```js
        "prompts": [
          {
            "title": "① 用 AI 安裝開發環境（JDK 21 / Maven / Git）",
            "kind": "build",
            "note": "把環境準備交給 AI，自己只負責核對結果",
            "text": "我用 Windows 11，請用 winget 幫我安裝 Eclipse Temurin JDK 21、Apache Maven 與 Git，並用 PowerShell 設定好 JAVA_HOME、M2_HOME 與 Path（永久生效）。完成後我要能在新開的終端機分別執行 java -version、mvn -version、git --version 都看到正確版本。"
          },
          {
            "title": "② 建立 full-stack 專案骨架",
            "kind": "build",
            "note": "monorepo：backend（Spring Boot）+ frontend（之後放 React）",
            "text": "請幫我建立一個課程用的 monorepo 專案，包含 backend（Spring Boot 3.5、Java 21，先只放 web 起手依賴）與 frontend 兩個資料夾，並初始化 git。完成後我要能 cd 進 backend 用 mvn spring-boot:run 把空專案先跑起來。"
          },
          {
            "title": "✅ 驗證 — 環境與骨架就緒",
            "kind": "verify",
            "note": "確認版本與啟動都正常",
            "text": "請幫我逐一確認開發環境就緒：執行 java -version、mvn -version、git --version 檢查版本是否正確，並確認 backend 能用 mvn spring-boot:run 成功啟動。若有任何一項不符，請直接幫我修正設定。"
          },
          {
            "title": "🔧 排錯 — 版本不對或啟動失敗",
            "kind": "fix",
            "note": "常見：電腦上有舊版 JDK",
            "text": "我跑驗證指令時看到非預期結果（我會貼上輸出）。常見狀況是電腦上有舊版 JDK 讓 java -version 不是 21。請依我貼上的訊息判斷原因，用 PowerShell 幫我修正 JAVA_HOME 與 Path，並讓 Maven 也使用同一版 JDK 21。"
          }
        ],
```

- [ ] **Step 2: 將 u1 的 `prompt` 與 `promptMac` 精簡為章節目標**

`prompt`（Windows）：
```js
        "prompt": "本章我們要建立整個 AI CRM 專案的開發環境基線：用 winget 安裝 JDK 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。理解原理後，依序使用下方提示詞請 AI Agent 協助完成，再用驗證提示詞核對結果。",
```
`promptMac`（macOS，差異在套件管理工具）：
```js
        "promptMac": "本章我們要建立整個 AI CRM 專案的開發環境基線：用 Homebrew 安裝 JDK 21、Maven、Git，並建立 backend 與 frontend 的 full-stack 專案骨架。理解原理後，依序使用下方提示詞請 AI Agent 協助完成，再用驗證提示詞核對結果。",
```

> 註：u1 的 ① 卡含 winget；若 `translatePlatformText` 會把 winget→brew、PowerShell→Terminal，則 mac 切換時卡片自動轉換。執行時先檢視 `translatePlatformText` 行為（搜尋 `function translatePlatformText`），若不會自動轉，於 ① 卡 text 結尾補一句「（若我用 macOS，請改用 Homebrew 安裝）」。

- [ ] **Step 3: 跑驗證確認 u1 通過該單元規則**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u1"`
Expected: 無 `u1 ...` 錯誤行（u1 規則已過；其他 unit 仍報錯屬正常）。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u1 提示詞為目標導向有序卡片"
```

---

## Task 5: 改寫 u2 提示詞（Spring MVC、REST API 與 CRM Domain Modeling）

**Files:**
- Modify: `teaching-site/course-data.js`（u2 `prompt`/`promptMac`/`prompts`）

- [ ] **Step 1: 將 u2 的 `prompts` 改為下列陣列**

```js
        "prompts": [
          {
            "title": "① 暖身：記憶體版客戶 REST API",
            "kind": "build",
            "note": "先不接資料庫，熟悉 Controller / Service 分工",
            "text": "我有一個只含 web 起手依賴的 Spring Boot 專案。請幫我做一組客戶 REST API，資料先存在記憶體（不接資料庫）：能查全部、查單筆（查不到回 404）、新增（回 201）。請加中文函式註解。完成後我要能用瀏覽器或 PowerShell 打這幾個端點拿到回應。"
          },
          {
            "title": "② 設計 CRM 核心 domain model",
            "kind": "build",
            "note": "客戶 / 聯絡人 / 互動紀錄 / 商機與其關聯",
            "text": "請把這個專案擴充成 CRM 的核心領域模型，涵蓋客戶、聯絡人、互動紀錄與商機四個概念以及它們之間的關聯（一個客戶有多位聯絡人、多筆互動、多個商機），並為每個概念補上對應的 REST API。欄位與關聯細節你依 CRM 常識設計即可，我會再核對。請加中文註解。"
          },
          {
            "title": "✅ 驗證 — 用 PowerShell 打 API",
            "kind": "verify",
            "note": "整理成可重跑的測試腳本",
            "text": "專案啟動在 8080 後，請用 PowerShell 的 Invoke-RestMethod 幫我測試客戶與商機的新增、查詢端點是否正常回應，並把測試指令整理成我之後可以重跑的腳本。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（兩者相同）**

```js
        "prompt": "請接續 Unit 1。本章我們用 Spring MVC 與 REST API 設計 CRM 的核心領域模型（客戶、聯絡人、互動紀錄、商機）。理解原理後，依序使用下方提示詞請 AI Agent 從記憶體版暖身做到完整 domain model，最後用驗證提示詞核對。",
        "promptMac": "請接續 Unit 1。本章我們用 Spring MVC 與 REST API 設計 CRM 的核心領域模型（客戶、聯絡人、互動紀錄、商機）。理解原理後，依序使用下方提示詞請 AI Agent 從記憶體版暖身做到完整 domain model，最後用驗證提示詞核對。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u2"`
Expected: 無 `u2 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u2 提示詞"
```

---

## Task 6: 改寫 u3 提示詞（PostgreSQL、Flyway、JPA 與動態查詢）

**Files:**
- Modify: `teaching-site/course-data.js`（u3 `prompt`/`promptMac`/`prompts`）

- [ ] **Step 1: 將 u3 的 `prompts` 改為下列陣列**

```js
        "prompts": [
          {
            "title": "① 用 Docker 起一個 PostgreSQL",
            "kind": "build",
            "note": "用 pgvector 映像，之後 RAG 要用向量擴充",
            "text": "請在專案根目錄建立 docker-compose 設定來啟動 PostgreSQL（用 pgvector/pgvector:pg18 映像，因為之後 RAG 會用到向量擴充），資料庫名 learn_spring、本機 5432 埠、資料要持久化。每個設定加中文註解。完成後我要能啟動容器並確認它是 Up。"
          },
          {
            "title": "② 讓 Flyway 接管資料庫 schema",
            "kind": "build",
            "note": "JPA 只驗證、由 Flyway 建表",
            "text": "請設定 Spring Boot 連到剛才的 PostgreSQL，並改用 Flyway 管理資料表結構（JPA 設成只驗證、不自動建表）。請建立第一支 migration，把 CRM 的客戶 / 聯絡人 / 互動 / 商機建表並塞入少量示範資料。完成後啟動時我要在 log 看到 Flyway 成功套用 migration。"
          },
          {
            "title": "③ domain model 接上 JPA 與動態查詢",
            "kind": "build",
            "note": "客戶查詢支援多條件任意組合",
            "text": "請把現有的 CRM domain model 從記憶體版改為 JPA Entity 並接上資料庫，Repository 用 Spring Data JPA。客戶查詢要支援多條件動態組合（例如產業、分級、關鍵字可任意搭配），請用適合的做法實作可組合的搜尋。請加中文註解。"
          },
          {
            "title": "✅ 驗證 — 資料真的進資料庫且持久化",
            "kind": "verify",
            "note": "重啟後資料仍在、Flyway 不重複建表",
            "text": "請幫我確認資料確實寫進 PostgreSQL：啟動專案後新增一筆客戶，用查詢 API 或進 psql 確認資料存在；重啟專案後資料仍在，且 Flyway 沒有重複建表或報錯。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（相同）**

```js
        "prompt": "請接續 Unit 2。本章我們把資料落地：用 Docker 跑 PostgreSQL、用 Flyway 管理 schema、用 JPA 與動態查詢操作資料。理解原理後，依序使用下方提示詞請 AI Agent 完成容器化、遷移與 JPA 接線，最後用驗證提示詞確認資料持久化。",
        "promptMac": "請接續 Unit 2。本章我們把資料落地：用 Docker 跑 PostgreSQL、用 Flyway 管理 schema、用 JPA 與動態查詢操作資料。理解原理後，依序使用下方提示詞請 AI Agent 完成容器化、遷移與 JPA 接線，最後用驗證提示詞確認資料持久化。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u3"`
Expected: 無 `u3 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u3 提示詞"
```

---

## Task 7: 改寫 u4 提示詞（Spring Security、JWT、OpenAPI 與企業級錯誤處理）

**Files:**
- Modify: `teaching-site/course-data.js`（u4 `prompt`/`promptMac`/`prompts`）

- [ ] **Step 1: 將 u4 的 `prompts` 改為下列陣列**

```js
        "prompts": [
          {
            "title": "① 加上 JWT 登入與角色授權",
            "kind": "build",
            "note": "ADMIN 與一般使用者；只有 ADMIN 能刪客戶",
            "text": "請用 Spring Security + JWT 為現有 CRM API 加上登入與角色授權：登入以外的 API 都要帶 Token 才能存取；角色分 ADMIN 與一般使用者，只有 ADMIN 能刪客戶；提供一支登入 API 回傳 Token。請加中文註解。完成後我要能：一般帳號登入後查得到客戶、刪客戶被擋；用 admin 才刪得掉。"
          },
          {
            "title": "② 補 API 文件與統一錯誤處理",
            "kind": "build",
            "note": "OpenAPI/Swagger + ProblemDetail 一致錯誤格式",
            "text": "請為專案加上 OpenAPI / Swagger UI 線上文件，並設計一致的錯誤回應格式（用 ProblemDetail），讓驗證失敗、查無資料、無權限等情況都回傳結構一致、好讀的錯誤。Swagger UI 也要在登入帶 Token 後才能瀏覽與測試。請加中文註解。"
          },
          {
            "title": "✅ 驗證 — 在 Swagger 上跑一次權限流程",
            "kind": "verify",
            "note": "一般使用者刪客戶應被擋、admin 應成功",
            "text": "請帶我在 Swagger UI 上驗證：先呼叫登入 API 拿 Token 並授權，接著用一般使用者身分試刪客戶（應被擋），再換 admin 重試（應成功），確認角色授權與錯誤回應都如預期。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（相同）**

```js
        "prompt": "請接續 Unit 3。本章我們把專案升級到企業級：用 Spring Security + JWT 做登入與角色授權、用 OpenAPI 產生 API 文件、用 ProblemDetail 做統一錯誤處理。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後在 Swagger 上驗證權限流程。",
        "promptMac": "請接續 Unit 3。本章我們把專案升級到企業級：用 Spring Security + JWT 做登入與角色授權、用 OpenAPI 產生 API 文件、用 ProblemDetail 做統一錯誤處理。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後在 Swagger 上驗證權限流程。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u4"`
Expected: 無 `u4 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u4 提示詞"
```

---

## Task 8: 改寫 u5 提示詞（React CRM 工作台與前後端整合）

**Files:**
- Modify: `teaching-site/course-data.js`（u5 `prompt`/`promptMac`/`prompts`，約 880-898 行）

- [ ] **Step 1: 將 u5 的 `prompts` 改為下列陣列**

```js
        "prompts": [
          {
            "title": "① 建立 React 前端骨架",
            "kind": "build",
            "note": "Vite + React 19、proxy 解 CORS、毛玻璃視覺",
            "text": "請在專案根目錄用 Vite 建立 React 19 前端（frontend 資料夾），設定開發 proxy 把 /api 轉到後端 8080 以免 CORS，並做一個有漸層 Header、毛玻璃卡片與骨架屏載入動畫的版面骨架。請加中文註解，完成後告訴我怎麼啟動，以及怎麼確認 proxy 有生效。"
          },
          {
            "title": "② 建立 API Client 與登入狀態管理",
            "kind": "build",
            "note": "自動帶 JWT、401 自動導回登入",
            "text": "請建立一個共用的 API 呼叫層（用 axios），自動把 localStorage 裡的 JWT 帶進每個請求，遇到 401 就清掉 token 並導回登入頁；再用 React 的機制管理登入狀態並提供給各頁面使用。請加中文註解。"
          },
          {
            "title": "③ 建立 CRM 核心頁面",
            "kind": "build",
            "note": "登入 / Dashboard / 客戶列表 / 詳情 / 商機看板",
            "text": "請建立 CRM 工作台的核心頁面並串接後端真實資料：登入頁、Dashboard（關鍵指標卡片）、客戶列表（可搜尋與篩選）、客戶詳情（聯絡人 / 互動 / 商機分頁）、商機看板（依階段分欄）。未登入要導回登入頁，載入中 / 失敗 / 空資料都要有明確 UI。請加中文註解。"
          },
          {
            "title": "✅ 驗證 — 登入後看得到真實資料",
            "kind": "verify",
            "note": "端到端串接確認",
            "text": "請幫我做一次端到端確認：用測試帳號登入後，Dashboard 指標、客戶列表與詳情都顯示後端來的真實資料；登出或 token 過期會回到登入頁。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（相同）**

```js
        "prompt": "請接續 Unit 4。本章我們做前端：用 Vite + React 19 建立 CRM 工作台，靠 proxy 串接後端、用 API Client 自動帶 JWT，並完成登入、Dashboard、客戶列表與商機看板。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後做端到端驗證。",
        "promptMac": "請接續 Unit 4。本章我們做前端：用 Vite + React 19 建立 CRM 工作台，靠 proxy 串接後端、用 API Client 自動帶 JWT，並完成登入、Dashboard、客戶列表與商機看板。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後做端到端驗證。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u5"`
Expected: 無 `u5 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u5 提示詞"
```

---

## Task 9: 改寫 u6 提示詞（Spring AI ChatClient、SSE、tool calling）

**Files:**
- Modify: `teaching-site/course-data.js`（u6 `prompt`/`promptMac`/`prompts`）

- [ ] **Step 1: 將 u6 的 `prompts` 改為下列陣列（API Key 改環境變數）**

```js
        "prompts": [
          {
            "title": "① 建立 AI 對話入口（SSE 串流 + 記憶）",
            "kind": "build",
            "note": "金鑰從環境變數讀，不寫死",
            "text": "請在現有 CRM 專案加入 Spring AI 的對話功能：用 ChatClient 接一個 OpenAI 相容的模型（base-url 與 API Key 都從環境變數讀，不要寫死在程式或設定檔），系統提示詞設定成親切的 CRM 智慧助手，並提供一支以 SSE 串流回覆的對話 API，用 sessionId 區隔不同使用者的對話記憶。請加中文註解。"
          },
          {
            "title": "② 讓 AI 能查 CRM 真實資料（Tool Calling）",
            "kind": "build",
            "note": "把現有查詢服務包成 AI 工具",
            "text": "請為 CRM 助手加上工具呼叫能力，把現有的客戶與商機查詢服務包成 AI 可呼叫的工具，讓 AI 回答時能取得資料庫的即時資料而不是憑空編造。每個工具請用中文清楚描述「什麼情況該呼叫」（這段是寫給模型看的）。請加中文註解。"
          },
          {
            "title": "③ 前端串流聊天室",
            "kind": "build",
            "note": "打字機效果；想清楚 SSE 怎麼安全帶 JWT",
            "text": "請在前端做一個即時打字機效果的 AI 聊天室，連到後端的 SSE 串流對話 API。注意瀏覽器原生的串流連線無法在標頭自訂帶 Token，請設計一個安全的方式把 JWT 帶給後端驗證。請加中文註解。"
          },
          {
            "title": "④ 客戶摘要與下一步建議",
            "kind": "build",
            "note": "結訓必做功能；數字要來自真實資料",
            "text": "請加一支「客戶摘要」功能：給一個客戶，AI 根據其基本資料、最近互動與進行中商機，產出結構化摘要（客戶概況、近期活躍度、風險訊號、建議的下一步行動），並在客戶詳情頁的 AI 建議分頁呈現。摘要裡的數字必須來自工具查到的真實資料，不可自行編造。"
          },
          {
            "title": "✅ 驗證 — AI 講的數字是真的",
            "kind": "verify",
            "note": "從日誌確認工具被呼叫、數字相符",
            "text": "請幫我驗證 AI 真的有查資料：問一個 VIP 客戶（例如台積電）的等級與進行中商機，從後端日誌確認工具確實被呼叫，且回答中的金額、次數與資料庫一致；再對久未互動的客戶產生摘要，確認出現風險訊號。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（相同）**

```js
        "prompt": "請接續 Unit 5。本章我們讓 CRM 長出 AI 大腦：用 Spring AI ChatClient 建對話入口、用 SSE 串流回覆、用 Tool Calling 讓 AI 查真實資料，並做客戶摘要與下一步建議。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後驗證 AI 回答的數字確實來自資料庫。",
        "promptMac": "請接續 Unit 5。本章我們讓 CRM 長出 AI 大腦：用 Spring AI ChatClient 建對話入口、用 SSE 串流回覆、用 Tool Calling 讓 AI 查真實資料，並做客戶摘要與下一步建議。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後驗證 AI 回答的數字確實來自資料庫。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u6"`
Expected: 無 `u6 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u6 提示詞並移除寫死的 API Key"
```

---

## Task 10: 改寫 u7 提示詞（RAG、pgvector、MCP 與知識庫擴充）

**Files:**
- Modify: `teaching-site/course-data.js`（u7 `prompt`/`promptMac`/`prompts`）

- [ ] **Step 1: 將 u7 的 `prompts` 改為下列陣列（embedding 金鑰用環境變數）**

```js
        "prompts": [
          {
            "title": "① 建立 RAG 知識庫",
            "kind": "build",
            "note": "pgvector 存向量；embedding 金鑰用環境變數",
            "text": "請為專案加入 RAG 知識庫：用 PostgreSQL 的 pgvector 存向量，embedding 服務的端點與金鑰從環境變數讀。提供「上傳文字文件」與「帶檢索的問答」兩支 API，讓 AI 回答前先從上傳的文件檢索相關片段。請加中文註解。完成後我要能上傳一份『客戶服務規範』，再問相關問題時 AI 是依文件作答而非自由發揮。"
          },
          {
            "title": "② 對話歷史長期記憶",
            "kind": "build",
            "note": "雙路檢索：客戶文件 + 個人歷史對話",
            "text": "請為聊天室加上長期記憶：把每輪對話也向量化存進 pgvector（標上 sessionId 與類型），寫入採非阻塞方式避免拖慢串流回覆。使用者提問時同時檢索『客戶文件』與『該使用者的歷史對話』兩路，合併作為上下文。完成後開新對話也能記得我先前說過的偏好。請加中文註解。"
          },
          {
            "title": "③ 用 MCP 擴充外部工具",
            "kind": "build",
            "note": "獨立 MCP Server（8081）+ 主專案接 MCP Client",
            "text": "請建立一個獨立的 MCP Server（Spring Boot），提供幾個跨系統的外部工具：行事曆排程、產生 Email 草稿、匯出商機報表（都用模擬實作即可），啟動在 8081。再在 CRM 主專案接上 MCP Client，讓聊天室能同時使用內部 domain 工具與這些 MCP 工具。請順帶說明哪些功能適合做 domain 工具、哪些適合 MCP。請加中文註解。"
          },
          {
            "title": "✅ 驗證 — 文件問答與 MCP 共存",
            "kind": "verify",
            "note": "RAG 引用文件 + domain/MCP 工具同場運作",
            "text": "請幫我驗證：上傳文件後的問答確實引用文件內容；單獨啟動 MCP Server（8081）健康檢查正常、主專案 log 出現 MCP 工具註冊成功；在同一段對話裡先查客戶（domain 工具）再請 AI 安排會議（MCP 工具），確認兩者都被正確呼叫。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（相同）**

```js
        "prompt": "請接續 Unit 6。本章我們給 AI 長期知識與外部手腳：用 pgvector 做 RAG 知識庫與對話長期記憶，再用 MCP 擴充行事曆、Email、報表等外部工具。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後驗證文件問答與 MCP 工具能同場運作。",
        "promptMac": "請接續 Unit 6。本章我們給 AI 長期知識與外部手腳：用 pgvector 做 RAG 知識庫與對話長期記憶，再用 MCP 擴充行事曆、Email、報表等外部工具。理解原理後，依序使用下方提示詞請 AI Agent 完成，最後驗證文件問答與 MCP 工具能同場運作。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u7"`
Expected: 無 `u7 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u7 提示詞"
```

---

## Task 11: 改寫 u8 提示詞（結訓專案衝刺與 Demo Day 驗收）

**Files:**
- Modify: `teaching-site/course-data.js`（u8 `prompt`/`promptMac`/`prompts`）

- [ ] **Step 1: 將 u8 的 `prompts` 改為下列陣列**

```js
        "prompts": [
          {
            "title": "① 整合 Unit 1–7 成可展示的 AI CRM",
            "kind": "build",
            "note": "三層串接 + 三個客戶場景的端到端測試",
            "text": "請接續前面所有產出，把後端（Spring Boot + PostgreSQL）、前端（React）與 AI / RAG / MCP 功能整合成一套可展示的 AI CRM，確認三層能正確啟動串接，並針對高價值客戶、資料不足客戶、流失風險客戶三個場景建立端到端測試。請加中文註解。"
          },
          {
            "title": "② 上線檢查與 Demo 準備",
            "kind": "build",
            "note": "檢查清單 + 一條龍 Demo 腳本",
            "text": "請幫我整理一份上線檢查清單（安全性、效能、可觀測性、錯誤處理）並逐項檢查；再準備一套 Demo Day 展示腳本，依序串起：登入 → 客戶列表 → AI 對話 → RAG 文件問答 → Swagger UI。"
          },
          {
            "title": "✅ 驗證 — 完整走一次 Demo 流程",
            "kind": "verify",
            "note": "從零跑通即通過結訓驗收",
            "text": "請陪我從零跑一次完整流程驗收：啟動三層服務、登入、瀏覽客戶、與 AI 對話並確認它查到真實資料、上傳文件做一次 RAG 問答、開 Swagger 看文件，全程沒有錯誤即通過結訓驗收。"
          }
        ],
```

- [ ] **Step 2: 精簡 `prompt` / `promptMac`（相同）**

```js
        "prompt": "請接續 Unit 7。本章是結訓衝刺：把 Unit 1–7 的所有產出整合成一套可展示的 AI CRM，建立端到端測試與上線檢查清單，並準備 Demo Day。理解重點後，依序使用下方提示詞請 AI Agent 完成整合與準備，最後完整走一次 Demo 流程驗收。",
        "promptMac": "請接續 Unit 7。本章是結訓衝刺：把 Unit 1–7 的所有產出整合成一套可展示的 AI CRM，建立端到端測試與上線檢查清單，並準備 Demo Day。理解重點後，依序使用下方提示詞請 AI Agent 完成整合與準備，最後完整走一次 Demo 流程驗收。",
```

- [ ] **Step 3: 驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs 2>&1 | grep "u8"`
Expected: 無 `u8 ...` 錯誤。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "content(teaching-site): 改寫 u8 提示詞"
```

---

## Task 12: 移除頂層死資料 course.units

**Files:**
- Modify: `teaching-site/course-data.js`（頂層 `"units": [ ... ]`，約 1530 行到對應結束 `]`）

- [ ] **Step 1: 確認頂層 units 範圍**

Run: `cd teaching-site && grep -n '^  "units": \[' course-data.js`
Expected: 顯示頂層 units 起始行（縮排 2 空格，與 `meta`/`overview` 同層）。再用 node 確認它與 day units 無關：

Run: `cd teaching-site && node -e "global.window={};require('./course-data.js');const c=window.COURSE;console.log('top-level units:', Array.isArray(c.units)?c.units.length:'none');console.log('day1:',c.day1.units.length,'day2:',c.day2.units.length)"`
Expected: `top-level units: 8` 與 `day1: 4 day2: 4`。

- [ ] **Step 2: 刪除頂層 units 陣列**

刪除頂層那段 `"units": [ ... ],`（整個鍵值對）。注意保留前後鍵的逗號正確（前一鍵 `appendix` 結尾、或其相對位置）。刪除後用 node 載入確認結構完整。

- [ ] **Step 3: 驗證物件仍可載入且 day units 不變**

Run: `cd teaching-site && node -e "global.window={};require('./course-data.js');const c=window.COURSE;console.log('top units:',c.units);console.log('day1/2:',c.day1.units.length,c.day2.units.length)"`
Expected: `top units: undefined` 且 `day1/2: 4 4`。

- [ ] **Step 4: Commit**

```bash
git add teaching-site/course-data.js
git commit -m "chore(teaching-site): 移除未被渲染的頂層 units 死資料"
```

---

## Task 13: 全站驗證與視覺確認

**Files:**（無修改；驗證）

- [ ] **Step 1: 資料驗證**

Run: `cd teaching-site && node scripts/verify-site.mjs`
Expected: `OK: 8 units, N referenced visual assets verified.`（無錯誤）。

- [ ] **Step 2: 渲染驗證**

Run: `cd teaching-site && node scripts/verify-render.mjs`
Expected: 通過（RWD 無水平溢出、進度儲存、複製按鈕正常）。若因環境缺 Playwright 失敗，記錄但不視為內容問題。

- [ ] **Step 3: 人工視覺抽查（用 web-visual-verification 或 agent-browser）**

啟動 `python -m http.server 5173`，開 u4 與 u6，確認：建置卡在前、驗證卡有綠色「驗證」徽章、排錯卡（u1）有琥珀「排錯」徽章、無重複的大師範本卡、提示詞卡內無程式碼區塊。

- [ ] **Step 4: 最終 commit（若視覺抽查有微調）**

```bash
git add -A teaching-site
git commit -m "chore(teaching-site): 提示詞重構全站驗證通過"
```

---

## Self-Review 對照

- **Spec A（公式）**：Task 4–11 每張卡皆為目標+技術+約束+驗收，u4 用 spec 範例。✅
- **Spec B（kind 與排序）**：Task 1 渲染 kind、Task 2 徽章、Task 4–11 卡片帶 kind 且 build 在前、verify 在後。✅
- **Spec C（說明搬家）**：提示詞 text 已不含散文；concepts 既有講解保留（未刪）。執行時若發現某 unit concepts 缺了被移除的關鍵說明，於該 Task Step 補一句到對應 concepts.body。✅（已在慣例註明）
- **Spec D（去重複 + 渲染）**：Task 1 移除大師範本注入；Task 4–11 精簡 prompt/promptMac。✅
- **Spec E（verify-site）**：Task 3 改讀 day units、移除 800 字規則、加 build/verify/code-fence 規則、Task 12 移除頂層 units。✅
- **驗收標準 1–5**：Task 13 跑兩支驗證 + 人工抽查；API Key 於 Task 9 移除。✅
