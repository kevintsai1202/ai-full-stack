// 講義預覽截圖腳本：啟動本地靜態伺服器，開啟指定講義的預覽 Modal 並輸出 PNG 截圖。
// 用途：驗證 materials/*.md 在網頁預覽中的排版（表格、標題、引言區塊），可重複執行。
// 用法：node scripts/capture-material.mjs "<講義名稱（不含副檔名）>" [輸出檔名]
//   例：node scripts/capture-material.mjs "CRM_角色權限矩陣與RBAC實作規格" material-rbac.png
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const matName = process.argv[2] || "CRM_角色權限矩陣與RBAC實作規格";  // 目標講義名稱
const outName = process.argv[3] || "material-capture.png";            // 輸出截圖檔名

/** 極簡靜態檔案伺服器：只服務 teaching-site 目錄下的檔案 */
function createServer() {
  const mime = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".png": "image/png", ".webp": "image/webp", ".md": "text/markdown; charset=utf-8" };
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
await new Promise((resolve) => server.listen(5176, "127.0.0.1", resolve));
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 1400 } });
  await page.goto("http://127.0.0.1:5176/", { waitUntil: "networkidle" });
  // 素材總覽區的講義連結帶有 data-action="preview-material"，點擊後開啟預覽 Modal
  await page.locator(`a[data-material-name="${matName}"]`).first().click();
  const modal = page.locator(".modal-card");
  await modal.waitFor({ timeout: 10000 });
  await page.waitForFunction(() => !document.querySelector(".modal-loading"), null, { timeout: 10000 });
  const outPath = path.join(root, "..", "output", outName);
  await fs.mkdir(path.dirname(outPath), { recursive: true });
  await modal.screenshot({ path: outPath });
  // 結構檢查：確認 Markdown 表格有被渲染成 <table>，而不是留成純文字
  const tables = await modal.locator("table.content-table").count();
  const rows = await modal.locator("table.content-table tbody tr").count();
  console.log(`OK 截圖已輸出：${outPath}（表格 ${tables} 個 / 資料列 ${rows} 列）`);
} finally {
  await browser.close();
  server.close();
}
