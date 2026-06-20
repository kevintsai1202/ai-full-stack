// ============================================================
// 擷取最新 AI CRM 的畫面、圖表局部與操作影片（可重跑）
// 來源：本機 AI CRM（預設 http://127.0.0.1:5173，可用 CRM_URL 覆寫）
// 產出：先寫到 scratchpad 的 staging 目錄供審查，確認後再轉 webp 回填 assets
//   - dashboard-full.png      整頁儀表板
//   - dashboard-viewport.png  首屏儀表板（給 hahow/ai-crm-dashboard 用）
//   - pipeline-funnel.png     銷售漏斗 局部
//   - monthly-forecast.png    月度營收 局部
//   - risk-breakdown.png      客戶風險結構 局部
//   - chart-drilldown-open.png 圖表 drilldown 展開
//   - ai-chat-response.png    AI 助理回應
//   - ai-crm-operation-flow.webm 操作流程影片（登入→儀表板→drilldown→客戶→AI）
// 相依：playwright（沿用 teaching-site/node_modules）
// 執行：node land-page/scripts/capture-ai-crm.mjs
// ============================================================
import path from "node:path";
import fs from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = path.dirname(fileURLToPath(import.meta.url));
const landRoot = path.resolve(here, "..");
const require = createRequire(path.join(landRoot, "..", "teaching-site", "package.json"));
const { chromium } = require("playwright");

const BASE = process.env.CRM_URL || "http://127.0.0.1:5173";
const OUT = process.env.OUT_DIR || path.resolve(here, "_crm-capture"); // staging 目錄
const VIDEO_DIR = path.join(OUT, "_video");

/** 安全執行單一擷取步驟：失敗只記錄、不中斷整體流程 */
async function step(label, fn) {
  try {
    await fn();
    console.log(`  ✓ ${label}`);
  } catch (e) {
    console.log(`  ✗ ${label} — ${String(e.message).slice(0, 120)}`);
  }
}

async function main() {
  await fs.mkdir(OUT, { recursive: true });
  await fs.mkdir(VIDEO_DIR, { recursive: true });

  const browser = await chromium.launch();
  // 1440x960 桌面視窗 + 錄影（操作流程影片）
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    deviceScaleFactor: 2, // 2x 高解析度截圖
    recordVideo: { dir: VIDEO_DIR, size: { width: 1440, height: 960 } },
  });
  const page = await context.newPage();

  // ── 登入：seed 帳號已預填，直接點「登入」 ──
  await page.goto(BASE, { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "登入", exact: true }).click();
  await page.waitForURL("**/dashboard", { timeout: 15000 });
  // 等待儀表板 widget 與圖表渲染完成
  await page.waitForSelector("article.panel", { timeout: 15000 });
  await page.waitForTimeout(3500);

  // ── 整頁 + 首屏 ──
  await step("dashboard-full.png", () =>
    page.screenshot({ path: path.join(OUT, "dashboard-full.png"), fullPage: true }));
  await step("dashboard-viewport.png", () =>
    page.screenshot({ path: path.join(OUT, "dashboard-viewport.png"), fullPage: false }));

  // ── 圖表局部：以標題定位 widget 卡片，並「依實際內容裁切」去除尾端空白 ──
  // （dashboard grid 給卡片固定高度，線圖等內容較矮時卡片下半會留白，單獨裁切會比例跑掉）
  const cards = [
    ["銷售漏斗", "pipeline-funnel.png"],
    ["月度營收", "monthly-forecast.png"],
    ["客戶風險結構", "risk-breakdown.png"],
  ];
  for (const [title, file] of cards) {
    await step(file, async () => {
      const card = page.locator("article.panel", { has: page.locator("h3", { hasText: title }) }).first();
      await card.scrollIntoViewIfNeeded();
      await page.waitForTimeout(400);
      // 計算卡片內「實際內容底部」：取所有葉節點 / svg / canvas 的最低可見底緣
      const clip = await card.evaluate((el) => {
        const cr = el.getBoundingClientRect();
        let maxBottom = cr.top;
        el.querySelectorAll("*").forEach((n) => {
          const r = n.getBoundingClientRect();
          const isLeaf = n.children.length === 0 || n.tagName === "svg" || n.tagName === "CANVAS";
          if (isLeaf && r.width > 0 && r.height > 0 && r.bottom <= cr.bottom + 1 && r.bottom > maxBottom) {
            maxBottom = r.bottom;
          }
        });
        const pad = 18;
        const height = Math.min(cr.height, Math.ceil(maxBottom - cr.top) + pad);
        // clip 為整頁座標系，需加上捲動位移
        return { x: Math.round(cr.left + window.scrollX), y: Math.round(cr.top + window.scrollY), width: Math.round(cr.width), height };
      });
      await page.screenshot({ path: path.join(OUT, file), clip });
    });
  }

  // ── 圖表 drilldown：點銷售漏斗的「提案」階段，截展開後的商機明細 ──
  await step("chart-drilldown-open.png", async () => {
    const card = page.locator("article.panel", { has: page.locator("h3", { hasText: "銷售漏斗" }) }).first();
    await card.scrollIntoViewIfNeeded();
    await page.waitForTimeout(400);
    // 漏斗各階段是真實 button（如「提案 48 筆 1.2億」），點擊展開該階段商機明細
    await card.getByRole("button").filter({ hasText: /提案|資格評估/ }).first().click({ force: true });
    await page.waitForTimeout(1800);
    // drilldown 多以 modal / dialog / drawer 呈現；抓得到就截，否則截全頁
    const modal = page.locator('[role="dialog"], .modal, [class*="drilldown"], [class*="drawer"], [class*="detail"]').first();
    if (await modal.count() && await modal.isVisible().catch(() => false)) {
      await modal.screenshot({ path: path.join(OUT, "chart-drilldown-open.png") });
    } else {
      await page.screenshot({ path: path.join(OUT, "chart-drilldown-open.png"), fullPage: false });
    }
    await page.keyboard.press("Escape").catch(() => {});
    await page.waitForTimeout(500);
  });

  // ── 客戶工作台 + AI 助理回應 ──
  await step("ai-chat-response.png", async () => {
    await page.goto(`${BASE}/customers`, { waitUntil: "networkidle" });
    await page.waitForTimeout(2500);
    // 開啟 AI 助理對話抽屜
    await page.getByRole("button", { name: /詢問 AI 助理/ }).first().click();
    await page.waitForTimeout(1500);
    // 在對話輸入框輸入問題並送出，觸發實際串流回應
    const chatInput = page.getByPlaceholder(/輸入問題/).first();
    await chatInput.fill("這個客戶目前的風險與下一步跟進建議是什麼？");
    await chatInput.press("Enter");
    // 等 AI 串流回應內容出現（最多 30 秒），再多等讓內容長一點
    await page.waitForTimeout(2000);
    await page.waitForFunction(() => {
      const t = document.body.innerText;
      return /建議|分析|風險|商機|跟進|摘要|根據/.test(t);
    }, { timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(7000);
    await page.screenshot({ path: path.join(OUT, "ai-chat-response.png"), fullPage: false });
  });

  // ── 收尾：關閉 context 以寫出影片 ──
  await context.close();
  await browser.close();

  // 影片重新命名為固定檔名
  const vids = (await fs.readdir(VIDEO_DIR)).filter((f) => f.endsWith(".webm"));
  if (vids.length) {
    await fs.rename(path.join(VIDEO_DIR, vids[0]), path.join(OUT, "ai-crm-operation-flow.webm"));
    console.log("  ✓ ai-crm-operation-flow.webm");
  } else {
    console.log("  ✗ 影片未產生");
  }

  console.log(`\n完成。輸出在：${OUT}`);
}

main().catch((e) => { console.error("擷取失敗：", e); process.exit(1); });
