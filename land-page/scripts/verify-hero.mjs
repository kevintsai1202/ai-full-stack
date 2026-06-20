// land-page hero 視覺驗證：啟動本地靜態伺服器，截 hero 區塊圖，
// 並斷言技術棧 chips 含 Spring AI 與 React（避免再漏掉招牌技術）。
// 可重跑：改版後執行此腳本即可逐項檢查。
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const shotDir = path.join(root, "screenshots", "hero-verify");
const port = 5191;
const require = createRequire(path.join(root, "..", "teaching-site", "package.json"));
const playwright = require("playwright");

const types = { ".html": "text/html; charset=utf-8", ".js": "application/javascript", ".css": "text/css", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".svg": "image/svg+xml", ".webp": "image/webp", ".ico": "image/x-icon" };

function startServer() {
  const server = http.createServer(async (req, res) => {
    const rel = decodeURIComponent(req.url.split("?")[0]);
    const file = path.join(root, rel === "/" ? "index.html" : rel);
    try {
      const buf = await fs.readFile(file);
      res.writeHead(200, { "Content-Type": types[path.extname(file)] || "application/octet-stream" });
      res.end(buf);
    } catch { res.writeHead(404); res.end("not found"); }
  });
  return new Promise((resolve) => server.listen(port, "127.0.0.1", () => resolve(server)));
}

async function main() {
  await fs.mkdir(shotDir, { recursive: true });
  const server = await startServer();
  const browser = await playwright.chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(`http://127.0.0.1:${port}/`, { waitUntil: "networkidle" });
    await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(shotDir, "hero.png") });
    // 斷言 hero 技術棧含 Spring AI 與 React
    const text = await page.locator("body").innerText();
    for (const must of ["Spring AI", "React"]) {
      if (!text.includes(must)) throw new Error(`hero 缺少技術棧：${must}`);
    }
    console.log("✓ hero 截圖完成，且含 Spring AI / React");
  } finally {
    await browser.close();
    server.close();
  }
}
main().catch((e) => { console.error(e); process.exit(1); });
