// 單元卡片截圖腳本：啟動本地靜態伺服器，捲動到指定單元並輸出 PNG 截圖。
// 用途：視覺驗證單元渲染結果（例如解鎖徽章、插圖、版面），可重複執行。
// 用法：node scripts/capture-unit.mjs [單元錨點id] [輸出檔名]
//   例：node scripts/capture-unit.mjs day2-u9 u9-capture.png
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const anchor = process.argv[2] || "day2-u9";      // 目標單元的錨點 id（day.id-unit.id）
const outName = process.argv[3] || `${anchor}-capture.png`; // 輸出截圖檔名

/** 極簡靜態檔案伺服器：只服務 teaching-site 目錄下的檔案 */
function createServer() {
  const mime = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".png": "image/png" };
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

const server = createServer();
await new Promise((resolve) => server.listen(5174, "127.0.0.1", resolve));
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await page.goto(`http://127.0.0.1:5174/#${anchor}`, { waitUntil: "networkidle" });
  const card = page.locator(`#${anchor}`);
  await card.waitFor({ timeout: 10000 });
  await card.scrollIntoViewIfNeeded();
  const outPath = path.join(root, "..", "output", outName);
  await fs.mkdir(path.dirname(outPath), { recursive: true });
  await card.screenshot({ path: outPath });
  console.log(`OK 截圖已輸出：${outPath}`);
} finally {
  await browser.close();
  server.close();
}
