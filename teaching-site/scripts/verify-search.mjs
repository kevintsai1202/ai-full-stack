// 搜尋功能驗證腳本：啟動本地靜態伺服器，在搜尋框輸入關鍵字，檢查單元卡片是否被正確篩選。
// 用途：重現／回歸驗證「搜尋沒作用」問題，可重複執行。
// 用法：node scripts/verify-search.mjs [關鍵字]
//   例：node scripts/verify-search.mjs JWT
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const keyword = process.argv[2] || "JWT"; // 測試用搜尋關鍵字

/** 極簡靜態檔案伺服器：只服務 teaching-site 目錄下的檔案 */
function createServer() {
  const mime = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".png": "image/png", ".webp": "image/webp" };
  return http.createServer(async (req, res) => {
    const urlPath = decodeURIComponent(new URL(req.url, "http://localhost").pathname);
    const filePath = path.join(root, urlPath === "/" ? "index.html" : urlPath);
    try {
      const body = await fs.readFile(filePath);
      res.writeHead(200, { "Content-Type": mime[path.extname(filePath)] || "application/octet-stream" });
      res.end(body);
    } catch {
      res.writeHead(404); res.end("not found");
    }
  });
}

/** 統計目前頁面上單元卡片的顯示狀態，以及搜尋回饋是否落在第一屏可見範圍 */
async function countUnits(page) {
  return page.evaluate(() => {
    const cards = [...document.querySelectorAll("article.unit")];
    const summary = document.getElementById("searchSummary");
    const summaryRect = summary?.getBoundingClientRect();
    return {
      total: cards.length,
      hiddenClass: cards.filter((c) => c.classList.contains("hidden-by-search")).length,
      visible: cards.filter((c) => c.getBoundingClientRect().height > 0).length,
      searchValue: document.getElementById("searchInput")?.value ?? null,
      // 搜尋摘要必須存在且位於視窗內（使用者不捲動就能看到）
      summaryText: summary?.textContent?.trim() ?? null,
      summaryInViewport: !!summaryRect && summaryRect.top >= 0 && summaryRect.bottom <= window.innerHeight,
      // 搜尋時非單元區塊（總覽、藍圖、測驗等）應被隱藏
      nonUnitSections: document.querySelectorAll("#overview, #feature-roadmap, #shared-case, #materials-overview, #quiz, #superpowers").length,
      dayBlocks: document.querySelectorAll(".day-block").length,
    };
  });
}

const server = createServer();
await new Promise((resolve) => server.listen(5175, "127.0.0.1", resolve));
const browser = await chromium.launch();
const errors = []; // 收集瀏覽器端錯誤
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  page.on("pageerror", (err) => errors.push(`pageerror: ${err.message}`));
  page.on("console", (msg) => { if (msg.type() === "error") errors.push(`console: ${msg.text()}`); });
  await page.goto("http://127.0.0.1:5175/", { waitUntil: "networkidle" });

  const before = await countUnits(page);
  console.log("輸入前：", before);

  const input = page.locator("#searchInput");
  await input.waitFor({ timeout: 10000 });
  await input.click();
  await page.keyboard.type(keyword, { delay: 30 });
  await page.waitForTimeout(400);

  const after = await countUnits(page);
  console.log(`輸入「${keyword}」後：`, after);
  console.log("瀏覽器錯誤：", errors.length ? errors : "無");

  const filtered = after.total > 0 && after.hiddenClass > 0 && after.visible < after.total && after.searchValue === keyword;
  const feedback = after.summaryInViewport && after.nonUnitSections === 0;
  console.log(filtered ? "OK 單元篩選有效" : "FAIL 搜尋沒有篩選單元");
  console.log(feedback ? "OK 第一屏可見搜尋回饋" : "FAIL 搜尋後第一屏沒有可見變化（摘要不在視窗內或非單元區塊未隱藏）");

  // 無結果情境：應顯示「找不到」訊息且不留下空的日程區塊
  await input.fill("");
  await input.click();
  await page.keyboard.type("zzzz沒有這個關鍵字", { delay: 10 });
  await page.waitForTimeout(300);
  const none = await countUnits(page);
  console.log("無結果情境：", none);
  const noneOk = none.visible === 0 && none.dayBlocks === 0 && (none.summaryText || "").includes("找不到");
  console.log(noneOk ? "OK 無結果有提示" : "FAIL 無結果時沒有提示或殘留空日程區塊");

  // 清除搜尋後應完整還原
  await input.fill("");
  await page.waitForTimeout(300);
  const restored = await countUnits(page);
  const restoredOk = restored.visible === before.total && restored.nonUnitSections === 6 && !restored.summaryText;
  console.log(restoredOk ? "OK 清除後完整還原" : "FAIL 清除搜尋後未還原");

  process.exitCode = filtered && feedback && noneOk && restoredOk ? 0 : 1;
} finally {
  await browser.close();
  server.close();
}
