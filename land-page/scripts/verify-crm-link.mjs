// CRM 線上體驗連結驗證：分別啟動 land-page 與 survey 的本地靜態伺服器，
// 斷言頁面內含指向測試 CRM (https://ai-crm.springai.world) 的連結，
// 且都以新分頁開啟 (target=_blank) 並帶 rel=noopener。
// 可重跑：改版後執行此腳本即可逐項檢查連結是否仍在且正確。
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = path.dirname(fileURLToPath(import.meta.url));
const landRoot = path.resolve(here, "..");                       // land-page 根目錄
const surveyRoot = path.resolve(here, "..", "..", "survey-backend", "src", "main", "resources", "static"); // survey 靜態檔目錄
const require = createRequire(path.join(landRoot, "..", "teaching-site", "package.json"));
const playwright = require("playwright");

const CRM_URL = "https://ai-crm.springai.world";                  // 測試 CRM 目標網址
const types = { ".html": "text/html; charset=utf-8", ".js": "application/javascript", ".css": "text/css", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".svg": "image/svg+xml", ".webp": "image/webp", ".ico": "image/x-icon" };

// 啟動一個僅供驗證用的極簡靜態伺服器，回傳 server 實例
function startServer(root, port) {
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

// 驗證單一頁面：CRM 連結至少 minCount 個，且每個都正確設定 target/rel
async function checkPage(browser, root, port, label, minCount) {
  const server = await startServer(root, port);
  try {
    const page = await browser.newPage();
    await page.goto(`http://127.0.0.1:${port}/`, { waitUntil: "domcontentloaded" });
    const links = page.locator(`a[href="${CRM_URL}"]`);
    const count = await links.count();
    if (count < minCount) throw new Error(`${label}：CRM 連結數量 ${count} < 預期 ${minCount}`);
    for (let i = 0; i < count; i++) {
      const target = await links.nth(i).getAttribute("target");
      const rel = await links.nth(i).getAttribute("rel");
      if (target !== "_blank") throw new Error(`${label}：第 ${i + 1} 個 CRM 連結未設定 target=_blank`);
      if (!rel || !rel.includes("noopener")) throw new Error(`${label}：第 ${i + 1} 個 CRM 連結未設定 rel=noopener`);
    }
    console.log(`✓ ${label}：找到 ${count} 個 CRM 連結，皆為新分頁開啟且帶 noopener`);
  } finally {
    server.close();
  }
}

async function main() {
  const browser = await playwright.chromium.launch();
  try {
    await checkPage(browser, landRoot, 5192, "land-page", 2);   // 導覽列 + showcase CTA
    await checkPage(browser, surveyRoot, 5193, "survey", 1);    // 成果展示區 CTA
    console.log("✓ 全部通過：land-page 與 survey 皆已正確放入 CRM 體驗連結");
  } finally {
    await browser.close();
  }
}

main().catch((err) => { console.error("✗ 驗證失敗：", err.message); process.exit(1); });
