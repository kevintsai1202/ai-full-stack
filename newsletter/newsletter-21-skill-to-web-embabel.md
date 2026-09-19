# 電子報第 021 期範本：把 Agent 的「技能包」搬上網頁，中間到底要補什麼？（鐵人賽 Day 31 導讀）

> **用途**：單一主題——鐵人賽 30 天完賽後的補充篇（Day 31）導讀：
> 回答「技能（Skill）只能在 CLI 裡用嗎？」這個一直沒回答的問題。以作者自己的台灣法律技能包 law-powers 為實驗對象，整包搬進 Spring Boot 4.1 + Embabel 1.5.1 網站，
> 整理搬家過程中最值得分享的五件事：① 技能本體不用改寫、② 敘述要變成不可跳過的骨架、③ 流程要能停下來等人、④ 把「請務必」變成程式擋得住的規則、⑤ 技能完全沒寫的治理層。結尾帶出 WebMCP 把能力還給 Agent、但送出永遠要人按的迴圈。
> **使用方式**：將下方「內文（Markdown）」複製到 admin 後台，先更新預覽並寄測試信，確認後再正式寄送。
> 系統會自動處理品牌外框與取消訂閱連結，內文不需要自行加入。
> 內文包含 `<!--paywall-->` 分隔行：之前是免費區，之後是受限區（登入後可解鎖閱讀）。
>
> **前置脈絡**：004 期預告了 Embabel 鐵人賽 30+1 天系列，本期即為那個「+1」的導讀。與 TUNNEL 系列（017–020）無先後依賴，可獨立寄送。
> **技能套件**：文末加入作者的鐵人賽技能套件 https://github.com/kevintsai1202/ai-agent-dev-guide （四個技能：入口路由、Embabel 後端、json-render 前端、WebMCP），附 Claude Code plugin 與 skills CLI 兩種安裝指令。
> **主視覺**：`newsletter/newsletter-21-cover.png`（16:9）**已生成（2026-09-08）**，提示詞見下方「封面提示詞」一節；寄送前上傳媒體庫並登記 `assets/uploaded-urls.txt`。
> **素材來源**：示範網站 https://law-graph-webmcp.zeabur.app/ ；完整文章在鐵人賽 Day 31（**網址寄送前補上**）。
> 建議 2 張 16:9 示意圖（`021-a-skill-to-web-pipeline.png`：CLI 技能 → withLocalSkill → withReference → 網頁服務；`021-b-must-to-code-guard.png`：LLM 產出 → GraphRules.apply() 純函式擋規則），**尚未生成**，提示詞見下方「示意圖提示詞」一節；生成後上傳媒體庫並替換文中 TODO 圖片 URL；若來不及，刪除兩張圖片行即可寄送。
>
> 文章中段以「工商時間」卡片（`<!--promo-->` 標記）宣傳 Hahow 課程，
> **是否附優惠碼與期限需在寄送前確認後填入**。

---

## 建議主旨（三選一）

1. `技能包只能在 CLI 裡用？我把整包法律技能搬上網頁，中間補了這五層`
2. `技能寫「請務必」，網站要「擋得住」：Agent 技能上線的落差清單`
3. `鐵人賽 Day 31：從 Claude Code 技能到 Spring Boot 網站，程式碼一行都不用改寫`

**推薦**：`技能包只能在 CLI 裡用？我把整包法律技能搬上網頁，中間補了這五層`

## 預覽文字（preheader）

> 技能本體不用改寫，但敘述要變骨架、流程要能等人、「務必」要變程式、治理要補齊。搬家清單一次給你。

---

## 封面提示詞（imagegen，附 `newsletter-20-cover.png` 作風格參考圖）

沿用 017–020 期的系列視覺：淺藍灰漸層底、深藍期數膠囊、超大主標、左右兩張白色圓角卡片、青綠主色、底部流程列。生成尺寸 16:9（2752×1536 或 1536×1024 皆可），存為 `newsletter/newsletter-21-cover.png`。

```text
請參考附圖的版面與風格，為技術電子報生成一張 16:9 的封面資訊圖（繁體中文），保持同一系列的視覺語言：
淺藍灰漸層背景、乾淨的扁平向量風格、白色圓角卡片、深藍（#0f172a）標題文字、青綠（#0d9488）作為主要強調色、少量橘色與紅色作為次要提示色。文字必須清晰可讀、字型一致、無錯字、無浮水印、無多餘裝飾。

【頂部】
- 置中一顆深藍色膠囊，白字「第 021 期」，左右各一條細線延伸。
- 超大粗體主標一行：「把 Agent 技能包搬上網頁，中間要補什麼？」
- 主標下方一行灰色副標：「技能本體不用改寫，但敘述要變骨架、務必要變程式、治理要補齊」

【左側白色卡片】
- 卡片頂端一顆深藍標籤膠囊：「搬家路徑｜技能本體不改寫」
- 卡片標題：「CLI 技能直接進 prompt」
- 一條由左到右的水平流程，四個白底小方塊、青綠虛線箭頭串接：
  「Claude Code 技能目錄」→「withLocalSkill()」→「withReference()」→「網頁服務」
  第一格用資料夾圖示、第二與第三格用等寬字體、第四格用瀏覽器視窗圖示。
- 流程下方一顆淺綠色提示條：「同一個 repo，CLI 與網頁兩邊不會走鐘」
- 卡片底部一顆淺藍色提示條：「10 個技能只載入 5 個：每一份都佔 prompt 空間」

【右側白色卡片】
- 卡片頂端一顆深藍標籤膠囊：「不可跳過的骨架｜擋得住的規則」
- 卡片標題：「@Action 型別串接 ＋ 純函式後處理」
- 一條垂直流程，四個帶圓點的白底長條，由上而下：
  「① brainstorm → BrainstormResult」
  「② askUser：WaitFor 等人（零 token）」（這一條加一個小小的暫停圖示）
  「③ research → ResearchResult」
  「④ analyze 需要 ResearchResult：GOAP 自動排序」
- 流程右側或下方一顆紅底白字警示條：「技能寫『請務必引用真實法條』→ 模型沒照做，程式擋得住嗎？」
- 警示條下方一顆淺綠色方塊，等寬字體：「GraphRules.apply()：條號不在檢索結果 → 整個節點移除，並寫入 notes」

【中央】
- 兩張卡片之間放一個青綠色的盾牌圖示，盾牌內是一個小小的技能文件圖示，象徵「技能」被程式規則保護。

【底部流程列】
- 五顆白色圓角膠囊，用灰色箭頭串接：
  「① 技能本體」→「② 骨架」→「③ 等人」→「④ 擋規則」→「⑤ 治理」
  其中「④ 擋規則」與「⑤ 治理」兩顆改為深藍底、青綠描邊並微微發光，表示這期最容易漏掉的兩層。
- 流程列右下角一個小小的灰色註記：「WebMCP 22 個工具｜送出永遠要人按」

整體構圖：頂部標題區約占 25%，中間兩張卡片約占 55%，底部流程列約占 20%。留白充足，不要塞滿。所有中文與英文文字都要正確拼寫，程式碼片段使用等寬字體。
```

**生成後檢查**：① 期數為「第 021 期」；② 主標與副標無錯字；③ `withLocalSkill()`、`withReference()`、`GraphRules.apply()`、`WaitFor` 四個識別字拼寫正確；④ 底部五顆膠囊順序與編號正確；⑤ 無浮水印。有錯字時優先重生，不要用修圖補字。

## 示意圖提示詞（imagegen，附 `newsletter-21-cover.png` 作風格參考圖）

兩張都是 16:9 內文插圖，風格與封面同系列但**不放期數膠囊、不放主標**，畫面只有一個主題，文字量比封面少。生成尺寸 1536×864 或 2752×1548，分別存為 `assets/021-a-skill-to-web-pipeline.png`、`assets/021-b-must-to-code-guard.png`，上傳媒體庫後替換內文第 ① 節與第 ④ 節的 TODO 圖片 URL。

### 圖 A｜`021-a-skill-to-web-pipeline.png`（放在 ① 技能本體其實不用改寫 之前）

```text
請參考附圖的視覺風格，為技術電子報生成一張 16:9 的內文示意圖（繁體中文），主題只有一個：「CLI 技能包一行不改，直接搬上網頁」。
淺藍灰漸層背景、乾淨的扁平向量風格、白色圓角卡片、深藍（#0f172a）文字、青綠（#0d9488）作為主要強調色、少量淺綠與淺藍提示色。不要期數膠囊、不要大標題、不要浮水印、不要多餘裝飾。文字必須清晰可讀、無錯字，程式識別字用等寬字體。

【主體：一條由左到右的水平流程，占畫面中央約 60% 高度】
四個白色圓角卡片，用青綠色實線箭頭串接：
1. 「Claude Code 技能目錄」——卡片內畫一個資料夾圖示，資料夾上有一張小小的 SKILL.md 文件，資料夾旁標註「law-powers」。
2. 「withLocalSkill()」——等寬字體，卡片內畫一個「載入」的小圖示（箭頭指向方塊）。
3. 「withReference()」——等寬字體，卡片內畫一張對話泡泡，泡泡裡塞著一份小文件，象徵技能被掛進 prompt。
4. 「網頁服務」——卡片內畫一個瀏覽器視窗圖示，視窗內是一張簡化的關係圖（幾個圓點用線連起來）。

【流程上方】
一條淺綠色圓角提示條，橫跨第 1 與第 4 卡片之間，文字：「同一個 repo：CLI 與網頁兩邊不會走鐘」。提示條左右兩端各畫一個小圖示：左邊終端機視窗、右邊瀏覽器視窗，兩者中間一條雙向箭頭。

【流程下方】
一條淺藍色圓角提示條：「10 個技能只載入 5 個：每一份都佔 prompt 空間」。提示條左側畫十個小文件圖示排成一列，其中五個實心青綠、五個灰色描邊。

【角落】
右下角一行小小的灰字：「embabel-agent-skills 直接讀 Claude Code 格式」

整體構圖：主流程置中，上下提示條各占約 15%，四周留白充足。不要加入任何未列出的文字。
```

**生成後檢查**：① 四個卡片順序與文字正確；② `withLocalSkill()`、`withReference()`、`embabel-agent-skills`、`law-powers` 拼寫正確；③ 十個文件圖示是 5 實心＋5 描邊；④ 沒有期數、沒有主標、沒有浮水印。

### 圖 B｜`021-b-must-to-code-guard.png`（放在 ④ 的三條規則之後）

```text
請參考附圖的視覺風格，為技術電子報生成一張 16:9 的內文示意圖（繁體中文），主題只有一個：「技能裡的『請務必』，在網頁上必須變成程式擋得住的規則」。
淺藍灰漸層背景、乾淨的扁平向量風格、白色圓角卡片、深藍（#0f172a）文字、青綠（#0d9488）主要強調色、紅色（#dc2626）只用在「被擋下」的元素。不要期數膠囊、不要大標題、不要浮水印。文字清晰可讀、無錯字，程式識別字用等寬字體。

【左側：技能文件】
一張白色卡片，畫成一份文件的樣子，頂端標籤「SKILL.md」。文件內容只有一行醒目的引號文字：「請務必引用真實存在的法條」。文件右上角一個小小的灰色註記：「CLI 靠 Agent 自律」。

【中央：三段式流程，由左到右】
1. 一個淺藍色圓角方塊：「LLM 產出圖譜」，方塊內畫一張小關係圖：五個圓點節點，其中一個節點標「§999」並用紅色描邊。
2. 一個青綠色的盾牌形狀，盾牌內等寬字體白字：「GraphRules.apply()」，盾牌下方小灰字：「純函式後處理」。從方塊 1 到盾牌一條實線箭頭。
3. 一個淺綠色圓角方塊：「乾淨的圖譜」，方塊內同一張關係圖但只剩四個圓點，被移除的那個位置畫一個淡淡的虛線空位。從盾牌到方塊 3 一條實線箭頭。

【盾牌下方：三條規則，白底長條，各帶一個小圖示】
- 「條號不在檢索結果 → 整個節點移除」（圖示：紅色叉）
- 「要件該當性一律以涵攝結果覆寫」（圖示：青綠色勾）
- 「被移除的寫進 notes，不默默吃掉」（圖示：一張小筆記）

【右下角】
一顆深藍底白字的圓角提示條：「如果模型沒照做，我的程式擋得住嗎？」

整體構圖：左側文件約占 20%，中央流程約占 50%，右側乾淨圖譜與提示條約占 30%。留白充足，不要加入任何未列出的文字。
```

**生成後檢查**：① 左側引號句一字不差；② `GraphRules.apply()`、`SKILL.md` 拼寫正確；③ 紅色只出現在被移除的節點與叉號；④ 三條規則順序與內文一致；⑤ 沒有期數、沒有主標、沒有浮水印。

---

## 內文（Markdown，直接複製貼上）

```markdown
# 把 Agent 的「技能包」搬上網頁，中間到底要補什麼？

嗨，我是凱文大叔。

寫完 30 天鐵人賽之後，我又補了第 31 篇。因為有個問題一直沒回答：

**技能（Skill）只能在 CLI 裡用嗎？不會用 CLI 的人怎麼辦？**

我拿自己的台灣法律技能包 law-powers 做了實驗，把它整包搬進一個 Spring Boot 4.1 + Embabel 1.5.1 的網站，跑在這裡：

👉 [law-graph-webmcp.zeabur.app](https://law-graph-webmcp.zeabur.app/)

貼上案情，網站會照技能的流程做：腦力激盪 → 追問 → 檢索法條判決 → 要件涵攝 → 抗辯評估 → 起草書狀 → 畫出 3D 法律關係圖。

順帶一提，這個網站本身也是用技能蓋出來的：後端的 Embabel 寫法、前端把能力開給 Agent 的 WebMCP 寫法，都來自我在鐵人賽整理的技能套件 **ai-agent-dev-guide**。文末會告訴你怎麼一行指令裝進 Claude Code。

以下是搬家過程中，我覺得最值得分享的幾件事。

![CLI 技能搬上網頁的路徑：技能目錄 → withLocalSkill() → withReference() → 網頁服務，技能包仍是原本那個 repo](https://springai-media.zeabur.app/newsletter-media/images/TODO-021-skill-to-web-pipeline.png)

## ① 技能本體其實不用改寫

這是最少人知道的一招：`embabel-agent-skills` 可以直接讀 Claude Code 格式的技能目錄，變成能掛進 prompt 的 reference。

```java
// 直接載入 Claude Code 格式的技能目錄，不需要改寫成別的格式
Skills skills = new Skills("law-powers", ..., new DefaultDirectorySkillDefinitionLoader(false))
        .withLocalSkill("skills/legal-research");

llm(context).withReference(skills)          // ← 技能包在這裡進 prompt
            .createObject(prompt, BrainstormResult.class);
```

路徑就是：CLI 技能 → `withLocalSkill()` → `withReference()` → 網頁服務。

技能包還是原本那個 repo，還能在 Claude Code 裡用，兩邊不會走鐘。

順帶一提：我的技能包有 10 個技能，網站只載入 5 個。**技能不是載滿就好，每一份都佔 prompt 空間**，跟工具白名單是同一個道理。

<!--promo-->

**📣 工商時間**

這期示範的法律關係圖網站，是用 Embabel 框架開發的系統，細節都在鐵人賽 30＋1 天的文章裡。但能看懂它、審查它、改動它，靠的不是哪一個框架，是基本功。

《**AI 賦能全端開發：從零打造企業級智慧應用**》教的就是這套基本功：用 Spring Boot + React 建好後端 API、資料庫、認證授權與前端介面，再加上 Spring AI、RAG 檢索、tool calling 與 MCP，親手完成一套企業級 AI CRM。

基本功打穩之後，換成 Embabel 也好、換成別的框架也好，你都能讓 AI 用不同的工具做出你需要的功能，交出一套完整的系統，而不是只會跟著某一個框架的範例走。

👉 [前往 Hahow 課程頁](https://hahow.in/cr/ai-full-stack)

<!--/promo-->

<!--paywall-->

## ② 技能寫的是「敘述」，網頁要的是「不可跳過的骨架」

技能說「先腦力激盪，再檢索，再涵攝」。

在 CLI 裡，這是 Agent 自己決定要照做；在網頁上，使用者可能拿到一個跳過檢索的結果，那就完了。

所以我把每個步驟寫成一個 `@Action`，用 Java record 的型別串起來。`analyze` 要 `ResearchResult`，而 `ResearchResult` 只有 `research` 產得出來——Embabel 的 GOAP 會自己排出順序，程式裡沒有任何一行 `if (step == 3)`。

**技能的章節標題，通常就是你的 Action 清單。**

## ③ 流程要能停下來等人

法律案件第一次貼進來的案情，幾乎不可能夠用。有沒有簽書面契約？對方是自然人還是公司？這些會直接改變結論。

Embabel 一行解決：

```java
@Action
public UserAnswers askUser(BrainstormResult brainstorm) {
    // 沒有問題就直接回空物件往下走，不為了對稱硬停一次
    if (brainstorm.questions().isEmpty()) return new UserAnswers(List.of());
    // 有問題才暫停流程，等使用者作答後再繼續
    return WaitFor.awaitable(new QuestionsAwaitable(brainstorm.questions()));
}
```

兩個細節：這個 Action **完全不呼叫 LLM**（零 token）；沒問題就直接回空物件往下衝，不為了對稱硬停一次。

## ④ 最容易漏掉的一層：把技能裡的「請務必」變成程式擋得住的規則

技能文件寫「請務必引用真實存在的法條」。

這句話在 CLI 靠 Agent 自律；在公開網站上，它必須是程式。

所以建圖那步 LLM 產出之後，一定會再過一層純函式 `GraphRules.apply()`：

- 法條節點的條號必須真的出現在檢索結果裡，否則整個節點移除。
- 要件是否該當，一律以涵攝結果覆寫，不採信建圖那步的說法。
- 被移除的還會寫進 notes，不默默吃掉。

![把技能裡的「請務必」變成程式擋得住的規則：LLM 產出圖譜後，經 GraphRules.apply() 純函式過濾，條號不在檢索結果者移除、要件該當性以涵攝結果覆寫、移除紀錄寫入 notes](https://springai-media.zeabur.app/newsletter-media/images/TODO-021-must-to-code-guard.png)

👉 搬家時請對技能裡每一句「務必／禁止／只能」問一次：**如果模型沒照做，我的程式擋得住嗎？** 擋不住的，就補一段後處理。

## ⑤ 還有一層技能完全沒寫：治理

因為 CLI 是你自己的機器。上線就全部要補：

- 每日 token 預算
- 每人每日配額
- 登入身分
- 用量統計
- 個資告知與刪除
- 授權排除條款

這層不做完，不要公開網址。

## 最後一個有趣的迴圈

網站再透過 WebMCP 把 22 個工具暴露給 ChatGPT／Chrome Agent，讓 Agent 也能操作這個頁面。

但我刻意**沒有做 submitQuestions 這個工具**——Agent 只能把建議答案填進欄位，**送出永遠要人按**。

技能從 Agent 手上搬到網頁給人用，網頁又把能力還給 Agent，只是這次在人的監督之下。

## 搬家前先自問的五句話

把這期壓成一張清單，下次要把任何技能搬上網頁時拿出來對一次：

| 層 | 技能裡長什麼樣 | 網頁上必須變成什麼 |
|---|---|---|
| ① 技能本體 | Claude Code 格式目錄 | 原封不動，用 `withReference()` 掛進 prompt；只載入用得到的 |
| ② 流程 | 章節敘述「先 A 再 B」 | 每章一個 `@Action`，用型別串起順序 |
| ③ 追問 | 「資訊不足時請追問」 | 零 token 的 `WaitFor` Action，沒問題就直接往下 |
| ④ 規則 | 「請務必／禁止／只能」 | LLM 產出後的純函式後處理，擋不住的補一段 |
| ⑤ 治理 | （完全沒寫） | 預算、配額、身分、用量、個資、授權——做完才公開 |

## 蓋這個網站用的技能套件，也開源了

鐵人賽 30 天寫完後，我把整個系列整理成一套 Claude Code 技能，放在 GitHub：

👉 [github.com/kevintsai1202/ai-agent-dev-guide](https://github.com/kevintsai1202/ai-agent-dev-guide)

裡面有四個技能，互相交叉引用：

| 技能 | 負責哪一層 | 這期用在哪 |
|---|---|---|
| `ai-agent-dev-guide` | 入口／路由 | 不確定該用哪個技能時從這裡進；含「值不值得用 Agent 框架」判準 |
| `embabel-agent-backend` | 後端（Embabel） | ②③④ 的 `@Action`、GOAP、`WaitFor`、`@Condition` 寫法 |
| `json-render-ui` | 前端（不綁後端） | LLM 產出 UI 規格、SSE 漸進渲染 |
| `webmcp-development-guide` | 對外給 Agent（選用） | 「最後一個迴圈」那 22 個 WebMCP 工具 |

裝法只要兩行，在 Claude Code 裡輸入：

```text
/plugin marketplace add kevintsai1202/ai-agent-dev-guide
/plugin install ai-agent-dev-guide@ai-agent-dev-guide
```

或者用 skills CLI 一次裝進所有支援的 Agent：

```bash
npx skills add kevintsai1202/ai-agent-dev-guide
```

基準版本是 Spring Boot 4.1 + Embabel 1.5.1 + Spring AI 2.0。還在 Spring Boot 3.5 的專案請留在 Embabel 1.0 那條線，技能裡的版本相容章節有寫清楚兩條線不能混。

完整文章（含四項「值不值得用 Agent 框架」的判準、`@Condition` 踩坑實錄）在鐵人賽 Day 31：

👉 [鐵人賽 Day 31 全文](TODO-鐵人賽-Day31-網址)

—— 凱文大叔

---

延伸閱讀：

- [示範網站：law-graph-webmcp](https://law-graph-webmcp.zeabur.app/)
- [技能套件：ai-agent-dev-guide（GitHub）](https://github.com/kevintsai1202/ai-agent-dev-guide)
- [Embabel Agent Framework（GitHub）](https://github.com/embabel/embabel-agent)
- [004 期：鐵人賽預告——用 Embabel 讓 Java 也能寫出企業級 AI Agent](TODO-004期讀者頁網址)
```

---

## 寄送提醒

- **付費分段**：`<!--paywall-->` 切在「② 技能寫的是『敘述』，網頁要的是『不可跳過的骨架』」之前；免費區含開場、示範網址、① 與工商卡，所有收件人（含 Email 摺疊版）都看得到。發布時必須使用 `PREMIUM` 並設定正數 `credit_cost`。
- `<!--paywall-->`、`<!--promo-->`、`<!--/promo-->` 三個標記都必須**整行單獨存在**，貼上後注意不要被編輯器夾入多餘字元。
- **封面**：`newsletter-21-cover.png` 已生成（2026-09-08），寄送前上傳媒體庫、登記 `assets/uploaded-urls.txt`，並在後台設定期數封面。
- **示意圖**：兩張依「示意圖提示詞」一節生成，存 `assets/021-a-*.png`、`assets/021-b-*.png`，上傳後替換內文 TODO URL。
- **TODO 必填三處**：① 鐵人賽 Day 31 全文網址（內文結尾與延伸閱讀）；② 004 期讀者頁網址（延伸閱讀，可直接刪除該列）；③ 兩張示意圖 URL（尚未繪製，來不及就刪除兩行圖片）。寄送前 `grep TODO` 確認歸零。
- **程式碼片段口徑**：兩段 Java 摘自作者已上線的 law-graph-webmcp 專案，本範本未在本 repo 內重新編譯。已查證 Embabel **1.5.1** 的 `embabel-agent-skills` 含 `com.embabel.agent.skills.Skills` 與 `support.DefaultDirectorySkillDefinitionLoader`，`embabel-agent-api` 含 `core.hitl.WaitFor` / `Awaitable`（Maven Central jar 清單，2026-09-08）。第一段的 `...` 是刻意省略的建構子參數，寄送前請對照原專案決定是補齊還是保留省略；`QuestionsAwaitable`、`GraphRules` 為專案自訂類別，非框架 API。
- **技能套件段落**：四個技能名稱、兩種安裝指令與版本基準均依 2026-09-08 的 repo README 抄錄（`/plugin marketplace add` → `/plugin install ai-agent-dev-guide@ai-agent-dev-guide`；`npx skills add kevintsai1202/ai-agent-dev-guide`）。寄送前實際在乾淨環境跑一次 plugin 安裝確認成功；若 repo 新增第五個技能或改名，同步更新表格。004 期寫的是「兩個技能」，本期為四個，屬正常演進，不必回改 004。
- **版本敘述**：Spring Boot 4.1 + Embabel 1.5.1 為撰稿時（2026-09）的組合；跨月寄送請重新確認 Embabel 最新 release，避免讀者照抄到過時版本。
- **示範網站可用性**：寄送前實際打開 https://law-graph-webmcp.zeabur.app/ 走一次「貼案情 → 追問」流程，確認服務在線、配額未用盡。文中提到「22 個工具」「10 個技能載入 5 個」為撰稿時數字，若專案有調整請同步修改。
- **工商卡片**：本期卡片未放優惠碼，寄送前決定是否加碼；若加碼，**務必到 Hahow 後台確認優惠碼與折數**並實際走一次結帳。
- **粗體與全形標點**：`**` 緊貼全形括號／引號時 CommonMark 不會生效，慣例是把全形標點放在粗體外（本文已依此撰寫，改稿時請維持）。
- 先寄測試信，確認標題階層、表格、清單、粗體與 inline code 在手機及桌面信箱中正常顯示；表格在窄螢幕會橫向捲動，屬正常。
