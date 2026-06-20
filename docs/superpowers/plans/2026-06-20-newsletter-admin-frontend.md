# 寄信後台前端 admin.html Implementation Plan（第二段 · 前端）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 survey-backend 同源服務的 `admin.html` 寄信後台頁，消費既有 admin API：金鑰閘門、撰寫(Markdown)、即時預覽、對象篩選與收件數、測試寄送、立即/排程發送、寄送歷史與取消排程。

**Architecture:** 單一自包含 vanilla HTML/JS/CSS 檔，放在 `survey-backend/src/main/resources/static/admin.html`，與後端同源（fetch 用相對路徑、免 CORS）。金鑰存 sessionStorage，所有 `/api/admin/**` 呼叫帶 `X-Admin-Key`。淺色 teal/amber 風格與現有問卷頁一致。單頁垂直區塊、桌面為主。

**Tech Stack:** 原生 HTML/CSS/JS（無框架、無建置）、後端既有 Admin API、Playwright（驗證腳本）。

設計來源：`docs/superpowers/specs/2026-06-20-newsletter-admin-design.md`（§6）。後端 API 已上線（`docs/superpowers/plans/2026-06-20-newsletter-admin-backend.md`）。

---

## 重要慣例
- 與現有問卷頁同模組：`survey-backend/src/main/resources/static/`。後端以 Spring Boot 靜態資源服務，部署後路徑為 `https://springai-survey.zeabur.app/admin.html`。
- JS 需中文函式級註解；頁面 `noindex`。
- git 明確路徑提交；禁止 `git add -A`、`--no-verify`；在 `main`。commit 訊息結尾加 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。
- 後端已提供端點：`GET /api/admin/recipients?role=&interest=`→`{count,sample[]}`；`POST /api/admin/campaign/preview`{subject,markdown}→`{html}`；`POST /api/admin/campaign/test`{subject,markdown,to}→`{providerId}`；`POST /api/admin/campaign/send`{subject,markdown,filter:{role,interest},mode,scheduledAt}→`{campaignId,recipientCount,accepted,failed}`；`GET /api/admin/campaigns`→`Campaign[]`（含 id,subject,mode,status,acceptedCount,failedCount）；`DELETE /api/admin/campaigns/{id}/schedule`→`{cancelled,failed}`。皆需 `X-Admin-Key`，無金鑰回 401。
- role/interest 選項需與問卷表單一致（見下方常數）。

---

## 檔案結構
```
survey-backend/src/main/resources/static/admin.html   新增：寄信後台單頁
survey-backend/scripts/verify-admin.mjs               新增：Playwright 驗證腳本
```

---

## Task 1: 建立 admin.html

**Files:**
- Create: `survey-backend/src/main/resources/static/admin.html`

- [ ] **Step 1: 建立完整檔案**

`survey-backend/src/main/resources/static/admin.html`（完整內容如下，逐字建立）：
```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>寄信後台 · AI 賦能全端開發</title>
<style>
  :root{ --bg:#f7fafc; --fg:#102033; --muted:#5c6b7d; --border:#dfe6ee; --surface:#fff; --surface-2:#eef3f8;
         --accent:#0d9488; --accent-deep:#0f766e; --accent-soft:#d9f3ef; --amber:#f59e0b; --danger:#dc2626;
         --r:12px; --font:system-ui,-apple-system,"Noto Sans TC","Microsoft JhengHei",sans-serif; }
  *{box-sizing:border-box}
  body{margin:0;font-family:var(--font);color:var(--fg);background:var(--bg);line-height:1.7}
  .wrap{max-width:860px;margin:0 auto;padding:32px 20px 80px}
  h1{font-size:1.5rem;margin:0 0 4px}
  .sub{color:var(--muted);margin:0 0 24px}
  .card{background:var(--surface);border:1px solid var(--border);border-radius:var(--r);padding:20px;margin-bottom:20px}
  .card h2{font-size:1.05rem;margin:0 0 14px}
  label{display:block;font-weight:700;margin:12px 0 6px;font-size:.92rem}
  input[type=text],input[type=email],input[type=datetime-local],select,textarea{
    width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:8px;font-size:.98rem;font-family:inherit;background:#fff;color:var(--fg)}
  textarea{min-height:180px;resize:vertical;font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
  .row{display:flex;gap:12px;flex-wrap:wrap}
  .row>div{flex:1;min-width:160px}
  .btn{padding:10px 18px;border:none;border-radius:8px;background:var(--accent);color:#fff;font-weight:800;cursor:pointer;font-size:.96rem}
  .btn:hover{background:var(--accent-deep)} .btn:disabled{opacity:.55;cursor:not-allowed}
  .btn.ghost{background:var(--surface-2);color:var(--accent-deep)}
  .btn.danger{background:var(--danger)}
  .count{font-weight:800;color:var(--accent-deep);font-size:1.1rem}
  iframe.preview{width:100%;height:360px;border:1px solid var(--border);border-radius:8px;background:#fff}
  .msg{margin-top:12px;padding:10px 12px;border-radius:8px;display:none}
  .msg.ok{display:block;background:var(--accent-soft);color:var(--accent-deep)}
  .msg.err{display:block;background:#fde8e8;color:var(--danger)}
  .gate{position:fixed;inset:0;background:rgba(16,32,51,.6);display:flex;align-items:center;justify-content:center;z-index:50}
  .gate .box{background:#fff;border-radius:var(--r);padding:28px;max-width:380px;width:90%}
  table{width:100%;border-collapse:collapse;font-size:.9rem}
  th,td{text-align:left;padding:8px;border-bottom:1px solid var(--border)}
  .pill{display:inline-block;padding:2px 8px;border-radius:999px;font-size:.78rem;font-weight:700}
  .pill.scheduled{background:var(--accent-soft);color:var(--accent-deep)}
  .pill.sent{background:#dcfce7;color:#166534}
  .pill.sending{background:#fff2cc;color:#92580a}
  .pill.failed{background:#fde8e8;color:var(--danger)}
  .pill.cancelled{background:var(--surface-2);color:var(--muted)}
  .hint{color:var(--muted);font-size:.85rem}
  .warn{color:var(--amber);font-weight:700}
</style>
</head>
<body>
<!-- 金鑰閘門：未驗證前覆蓋整頁 -->
<div class="gate" id="gate">
  <div class="box">
    <h2 style="margin-top:0">輸入管理金鑰</h2>
    <p class="hint">需 X-Admin-Key 才能進入寄信後台。金鑰只存於本分頁（sessionStorage）。</p>
    <input type="text" id="gate-key" placeholder="X-Admin-Key" autocomplete="off">
    <div class="msg err" id="gate-msg"></div>
    <button class="btn" id="gate-btn" style="margin-top:14px;width:100%">進入</button>
  </div>
</div>

<div class="wrap" id="app" hidden>
  <h1>寄信後台</h1>
  <p class="sub">撰寫並發送課程電子報。每封信會自動帶上該收件人的退訂連結。</p>

  <div class="card">
    <h2>① 撰寫</h2>
    <label for="subject">主旨</label>
    <input type="text" id="subject" placeholder="電子報主旨">
    <label for="markdown">內文（Markdown）</label>
    <textarea id="markdown" placeholder="# 標題&#10;&#10;支援 **粗體**、清單、[連結](https://...)"></textarea>
    <button class="btn ghost" id="preview-btn" style="margin-top:12px">更新預覽</button>
    <div class="msg err" id="compose-msg"></div>
  </div>

  <div class="card">
    <h2>② 預覽</h2>
    <iframe class="preview" id="preview" title="預覽"></iframe>
  </div>

  <div class="card">
    <h2>③ 發送對象</h2>
    <div class="row">
      <div>
        <label for="role">身分（不限留空）</label>
        <select id="role"><option value="">不限</option></select>
      </div>
      <div>
        <label for="interest">想學主題（不限留空）</label>
        <select id="interest"><option value="">不限</option></select>
      </div>
    </div>
    <p style="margin:14px 0 0">將寄給 <span class="count" id="rcount">—</span> 人　<button class="btn ghost" id="rcount-btn">更新人數</button></p>
    <p class="hint" id="rsample"></p>
    <p class="warn" id="quota-warn" hidden>⚠️ 超過每日 100 封額度，超量部分會失敗。</p>
  </div>

  <div class="card">
    <h2>④ 測試寄送（寄給自己）</h2>
    <div class="row">
      <div><input type="email" id="test-to" placeholder="your@email.com"></div>
      <div style="flex:0"><button class="btn ghost" id="test-btn">寄測試信</button></div>
    </div>
    <div class="msg" id="test-msg"></div>
  </div>

  <div class="card">
    <h2>⑤ 發送</h2>
    <div class="row" style="align-items:center">
      <div style="flex:0">
        <label style="display:inline;font-weight:400"><input type="radio" name="mode" value="now" checked> 立即發送</label>
        <label style="display:inline;font-weight:400;margin-left:14px"><input type="radio" name="mode" value="schedule"> 排程</label>
      </div>
      <div><input type="datetime-local" id="sched" disabled></div>
    </div>
    <button class="btn" id="send-btn" style="margin-top:14px">發送</button>
    <div class="msg" id="send-msg"></div>
  </div>

  <div class="card">
    <h2>⑥ 寄送歷史　<button class="btn ghost" id="hist-btn">重新整理</button></h2>
    <table id="hist"><thead><tr><th>主旨</th><th>模式</th><th>狀態</th><th>成功/失敗</th><th></th></tr></thead><tbody></tbody></table>
  </div>
</div>

<script>
  // 金鑰存 sessionStorage；admin.html 與後端同源，API 用相對路徑
  const KEY = 'survey_admin_key';
  // 與問卷表單一致的選項
  const ROLES = ["學生","應屆畢業生","後端工程師","前端工程師","全端工程師","行動 App 開發","資料／AI 工程師","DevOps／SRE","軟體架構師","技術主管／PM","非本科轉職者","接案／自由工作者","創業者","企業內訓窗口","其他"];
  const INTERESTS = ["RAG 知識庫","Tool Calling","前端整合","Spring 其他模組","AI 輔助程式開發","Spring Security","資料庫","Docker 部署"];

  const $ = s => document.querySelector(s);
  /** 取得目前金鑰 */
  function getKey(){ return sessionStorage.getItem(KEY) || ''; }

  /** 統一 API 呼叫：帶金鑰與 JSON；401 清金鑰回閘門；非 2xx 拋錯 */
  async function api(path, opts={}){
    const res = await fetch(path, {...opts, headers:{'Content-Type':'application/json','X-Admin-Key':getKey(),...(opts.headers||{})}});
    if (res.status === 401){ sessionStorage.removeItem(KEY); showGate('金鑰無效，請重新輸入'); throw new Error('401'); }
    if (!res.ok) throw new Error('HTTP '+res.status);
    const ct = res.headers.get('content-type') || '';
    return ct.includes('application/json') ? res.json() : res.text();
  }

  /** 顯示閘門（可附訊息） */
  function showGate(msg){ $('#app').hidden=true; $('#gate').style.display='flex'; if(msg){ const m=$('#gate-msg'); m.className='msg err'; m.textContent=msg; } }
  /** 顯示主畫面 */
  function showApp(){ $('#gate').style.display='none'; $('#app').hidden=false; }

  // 閘門按鈕：存金鑰 → 試打 recipients 驗證
  $('#gate-btn').onclick = async () => {
    sessionStorage.setItem(KEY, $('#gate-key').value.trim());
    try { await api('/api/admin/recipients'); showApp(); init(); } catch(e){ /* 401 已處理 */ }
  };

  /** 把字串陣列填入 select */
  function fillSelect(id, items){ const el=$(id); items.forEach(v=>{ const o=document.createElement('option'); o.value=v; o.textContent=v; el.appendChild(o); }); }

  let inited=false;
  /** 初始化主畫面事件與資料（僅一次） */
  function init(){
    if(inited) return; inited=true;
    fillSelect('#role', ROLES); fillSelect('#interest', INTERESTS);
    $('#preview-btn').onclick = doPreview;
    $('#rcount-btn').onclick = doCount;
    $('#role').onchange = doCount; $('#interest').onchange = doCount;
    $('#test-btn').onclick = doTest;
    $('#send-btn').onclick = doSend;
    $('#hist-btn').onclick = doHistory;
    document.querySelectorAll('input[name=mode]').forEach(r=> r.onchange=()=>{ $('#sched').disabled = (mode()!=='schedule'); });
    doCount(); doHistory();
  }
  /** 目前發送模式 */
  function mode(){ return document.querySelector('input[name=mode]:checked').value; }

  /** 預覽：呼叫後端渲染，塞進 iframe */
  async function doPreview(){
    const m=$('#compose-msg'); m.className='msg';
    try{ const r=await api('/api/admin/campaign/preview',{method:'POST',body:JSON.stringify({subject:$('#subject').value,markdown:$('#markdown').value})});
      $('#preview').srcdoc = r.html; }
    catch(e){ if(e.message!=='401'){ m.className='msg err'; m.textContent='預覽失敗：'+e.message; } }
  }

  /** 更新收件人數與樣本；>100 顯示額度警告 */
  async function doCount(){
    try{ const q=new URLSearchParams(); if($('#role').value)q.set('role',$('#role').value); if($('#interest').value)q.set('interest',$('#interest').value);
      const r=await api('/api/admin/recipients?'+q.toString());
      $('#rcount').textContent=r.count; $('#rsample').textContent = r.sample.length ? '樣本：'+r.sample.join(', ') : '';
      $('#quota-warn').hidden = r.count<=100; }
    catch(e){ if(e.message!=='401') $('#rcount').textContent='?'; }
  }

  /** 寄測試信給指定信箱 */
  async function doTest(){
    const m=$('#test-msg'); m.className='msg';
    const to=$('#test-to').value.trim(); if(!to){ m.className='msg err'; m.textContent='請填測試信箱'; return; }
    try{ await api('/api/admin/campaign/test',{method:'POST',body:JSON.stringify({subject:$('#subject').value,markdown:$('#markdown').value,to})});
      m.className='msg ok'; m.textContent='測試信已寄給 '+to; }
    catch(e){ if(e.message!=='401'){ m.className='msg err'; m.textContent='測試寄送失敗：'+e.message; } }
  }

  /** 發送：確認人數 → 立即或排程 */
  async function doSend(){
    const m=$('#send-msg'); m.className='msg';
    if(!$('#subject').value.trim()){ m.className='msg err'; m.textContent='請填主旨'; return; }
    const count=$('#rcount').textContent;
    const isSched = mode()==='schedule';
    let scheduledAt=null;
    if(isSched){ const v=$('#sched').value; if(!v){ m.className='msg err'; m.textContent='請選排程時間'; return; } scheduledAt=new Date(v).toISOString(); }
    if(!confirm(`確定要${isSched?'排程':'立即'}發送給 ${count} 人嗎？`)) return;
    $('#send-btn').disabled=true;
    try{ const body={subject:$('#subject').value,markdown:$('#markdown').value,filter:{role:$('#role').value||null,interest:$('#interest').value||null},mode:mode(),scheduledAt};
      const r=await api('/api/admin/campaign/send',{method:'POST',body:JSON.stringify(body)});
      m.className='msg ok'; m.textContent=`已${isSched?'排程':'送出'}：收件 ${r.recipientCount}、成功 ${r.accepted}、失敗 ${r.failed}`;
      doHistory(); }
    catch(e){ if(e.message!=='401'){ m.className='msg err'; m.textContent='發送失敗：'+e.message; } }
    finally{ $('#send-btn').disabled=false; }
  }

  /** 載入寄送歷史；排程中的可取消 */
  async function doHistory(){
    try{ const rows=await api('/api/admin/campaigns'); const tb=$('#hist tbody'); tb.innerHTML='';
      rows.forEach(c=>{ const tr=document.createElement('tr');
        const cancelBtn = c.status==='scheduled' ? `<button class="btn danger" data-cancel="${c.id}">取消排程</button>` : '';
        tr.innerHTML=`<td>${esc(c.subject)}</td><td>${esc(c.mode)}</td><td><span class="pill ${esc(c.status)}">${esc(c.status)}</span></td><td>${c.acceptedCount}/${c.failedCount}</td><td>${cancelBtn}</td>`;
        tb.appendChild(tr); });
      tb.querySelectorAll('[data-cancel]').forEach(b=> b.onclick=()=>doCancel(b.dataset.cancel)); }
    catch(e){ /* 401 已處理；其餘忽略 */ }
  }

  /** 取消某 campaign 排程 */
  async function doCancel(id){
    if(!confirm('確定取消此排程？')) return;
    try{ const r=await api('/api/admin/campaigns/'+id+'/schedule',{method:'DELETE'}); alert(`已取消 ${r.cancelled} 封，失敗 ${r.failed}`); doHistory(); }
    catch(e){ if(e.message!=='401') alert('取消失敗：'+e.message); }
  }

  /** HTML 轉義，避免歷史欄位 XSS */
  function esc(s){ const d=document.createElement('div'); d.textContent = s==null?'':String(s); return d.innerHTML; }

  // 啟動：有金鑰直接驗證進入，否則顯示閘門
  (async ()=>{ if(getKey()){ try{ await api('/api/admin/recipients'); showApp(); init(); }catch(e){ /* 401 已處理 */ } } else { showGate(); } })();
</script>
</body>
</html>
```

- [ ] **Step 2: 本機結構檢查（不需後端）**

用瀏覽器開檔 `survey-backend/src/main/resources/static/admin.html`：應出現「輸入管理金鑰」閘門覆蓋層（主畫面隱藏）。輸入框與「進入」鈕可見。（此時無後端，按進入會因 fetch 失敗停在閘門，屬正常；真正功能於部署後由 Task 2 驗證。）

- [ ] **Step 3: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html
git commit -m "feat(survey-backend): 寄信後台前端 admin.html（金鑰閘門+撰寫/預覽/篩選/發送/歷史）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/resources/static/admin.html
```

---

## Task 2: 部署 + Playwright 驗證腳本

**Files:**
- Create: `survey-backend/scripts/verify-admin.mjs`

- [ ] **Step 1: push 部署**

```bash
git push origin main
```
Expected: Zeabur 重建；`https://springai-survey.zeabur.app/admin.html` 可開。

- [ ] **Step 2: 建立 Playwright 驗證腳本**

`survey-backend/scripts/verify-admin.mjs`（可重跑；驗證金鑰閘門→進入→預覽→收件數，不實際發送以免寄出真信）：
```javascript
// 寄信後台 admin.html 端到端驗證腳本（不實際發送）
// 用法：$env:ADMIN_API_KEY="<金鑰>"; node survey-backend/scripts/verify-admin.mjs
// 需求：npx playwright（首次會自動下載 chromium）
import { chromium } from 'playwright';

const BASE = process.env.ADMIN_BASE || 'https://springai-survey.zeabur.app';
const KEY = process.env.ADMIN_API_KEY;
if (!KEY) { console.error('請先設定環境變數 ADMIN_API_KEY'); process.exit(1); }

const browser = await chromium.launch();
const page = await browser.newPage();
const fail = (m) => { console.error('FAIL:', m); process.exitCode = 1; };

try {
  // 1. 開頁 → 應出現金鑰閘門
  await page.goto(`${BASE}/admin.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#gate', { state: 'visible' });
  if (await page.locator('#app').isVisible()) fail('未驗證前主畫面不應顯示');
  console.log('OK 金鑰閘門出現');

  // 2. 輸入金鑰進入 → 主畫面顯示
  await page.fill('#gate-key', KEY);
  await page.click('#gate-btn');
  await page.waitForSelector('#app', { state: 'visible', timeout: 15000 });
  console.log('OK 金鑰正確，進入主畫面');

  // 3. 收件數載入（數字或 0）
  await page.waitForFunction(() => /\d/.test(document.querySelector('#rcount')?.textContent || ''), null, { timeout: 15000 });
  console.log('OK 收件數載入：', await page.locator('#rcount').textContent());

  // 4. 撰寫 + 預覽 → iframe 應有渲染內容
  await page.fill('#subject', '驗證用主旨');
  await page.fill('#markdown', '# Hello\n\nverify body');
  await page.click('#preview-btn');
  await page.waitForFunction(() => {
    const f = document.querySelector('#preview');
    return f && f.srcdoc && f.srcdoc.includes('Hello');
  }, null, { timeout: 15000 });
  console.log('OK 預覽渲染成功');

  // 5. 截圖留存
  await page.screenshot({ path: 'survey-backend/scripts/admin-verify.png', fullPage: true });
  console.log('OK 截圖 survey-backend/scripts/admin-verify.png');

  console.log('\n全部通過 ✅（未實際發送）');
} catch (e) {
  fail(e.message);
} finally {
  await browser.close();
}
```

- [ ] **Step 3: 執行驗證腳本**

```powershell
$env:ADMIN_API_KEY="<線上 ADMIN_API_KEY>"; node survey-backend/scripts/verify-admin.mjs
```
Expected: 終端輸出 5 個 OK 與「全部通過 ✅」，並產生 `admin-verify.png`。
（若 `node` 缺 playwright：先 `npx playwright install chromium` 或在已裝 playwright 的環境執行；腳本本身可重跑。）

- [ ] **Step 4: Commit**

```bash
git add survey-backend/scripts/verify-admin.mjs
git commit -m "test(survey-backend): admin.html Playwright 驗證腳本

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/scripts/verify-admin.mjs
```

---

## Task 3: 線上人工驗收

**Files:**（無；人工 + 真實寄送）

- [ ] **Step 1: 完整流程人工驗收**

開 `https://springai-survey.zeabur.app/admin.html`，用線上金鑰進入，依序確認：
1. 撰寫主旨 + Markdown → 「更新預覽」→ iframe 顯示套外框後的內容（含退訂連結頁腳）。
2. 切換 role/interest → 「將寄給 N 人」即時更新；無資料時為 0。
3. 「寄測試信」給自己 → 收到信、排版正確、退訂連結可點且能退訂。
4. 排程一封（未來時間）→ 歷史出現該筆（scheduled）→「取消排程」→ 狀態轉 cancelled。
5. （可選）對 1 位真實收件人「立即發送」→ 對方收到、歷史顯示 sent。

- [ ] **Step 2: 清理驗收測試資料**

驗收若建立測試問卷/ campaign，比照後端段方式清理（排程用取消端點；測試問卷與 campaign 用 psql 依 email/id 刪除），確認 `GET /api/admin/recipients` 與 `GET /api/admin/campaigns` 回乾淨狀態。

---

## Self-Review 對照
- **金鑰閘門（sessionStorage、401 重新要求）**：Task 1 gate + api() 401 處理；Task 2 驗證閘門。✅
- **撰寫 Markdown + 伺服器端預覽（iframe）**：Task 1 doPreview。✅
- **對象篩選（role/interest 單選）+ 即時收件數 + >100 額度警告**：Task 1 doCount + quota-warn。✅
- **測試寄送給自己**：Task 1 doTest。✅
- **立即 / 排程（datetime-local，未來）+ 確認對話框顯示人數**：Task 1 doSend。✅
- **寄送歷史 + 取消排程（僅 scheduled 顯示按鈕）**：Task 1 doHistory/doCancel。✅
- **淺色 teal/amber 與問卷頁一致、單頁垂直、桌面為主、noindex**：Task 1 CSS/meta。✅
- **同源相對路徑（免 CORS）**：Task 1 api() 用相對路徑。✅
- **XSS 防護**：歷史欄位以 esc() 轉義；預覽用 iframe srcdoc（管理者可信內容）。✅
- **role/interest 選項與問卷一致**：Task 1 ROLES/INTERESTS 常數。✅
- **可重跑瀏覽器自動化腳本（符合 CLAUDE.md）**：Task 2 verify-admin.mjs。✅
- **YAGNI**：無框架、無建置、無帳號系統、無 webhook。✅
```
