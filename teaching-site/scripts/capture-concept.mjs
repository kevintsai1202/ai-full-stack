// 概念區塊截圖腳本：啟動本地靜態伺服器，找出指定標題的概念卡片並輸出 PNG 截圖。
// 用途：單獨檢視某個 concept 的渲染結果（表格、程式碼區塊、清單），可重複執行。
// 用法：node scripts/capture-concept.mjs "<concept 標題>" [輸出檔名]
//   例：node scripts/capture-concept.mjs "CRM 角色與權限模型" u4-rbac.png
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const heading = process.argv[2] || "CRM 角色與權限模型";           // 目標概念的 heading 文字
const outName = process.argv[3] || "concept-capture.png";          // 輸出截圖檔名

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

const server = createServer();
await new Promise((resolve) => server.listen(5175, "127.0.0.1", resolve));
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await page.goto("http://127.0.0.1:5175/", { waitUntil: "networkidle" });
  // 概念標題以 .concept-heading 呈現，往上取其所屬的 article.concept 作為截圖範圍
  const target = page.locator("article.concept", { has: page.locator(`.concept-heading:text-is("${heading}")`) }).first();
  await target.waitFor({ timeout: 10000 });
  await target.scrollIntoViewIfNeeded();
  const outPath = path.join(root, "..", "output", outName);
  await fs.mkdir(path.dirname(outPath), { recursive: true });
  await target.screenshot({ path: outPath });
  // 附帶輸出結構檢查結果，方便在 CI 或終端直接確認表格有被渲染成 <table>
  const tables = await target.locator("table.content-table").count();
  const rows = await target.locator("table.content-table tbody tr").count();
  console.log(`OK 截圖已輸出：${outPath}（表格 ${tables} 個 / 資料列 ${rows} 列）`);
} finally {
  await browser.close();
  server.close();
}
