// 概念附件驗證腳本：檢查每個 concept.attachments 指到的講義檔存在、可下載，且點擊標題能開啟預覽 Modal。
// 用途：course-data.js 掛上新附件後快速回歸，避免檔名打錯或路徑在子路徑部署下失效。
// 用法：node scripts/verify-attachments.mjs
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

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

// 讀出所有掛了附件的概念（course-data.js 是 window.COURSE = {...} 的 JS 物件字面值）
const sandbox = { window: {} };
vm.runInNewContext(await fs.readFile(path.join(root, "course-data.js"), "utf8"), sandbox);
const course = sandbox.window.COURSE;
const units = [course.day1, course.day2, course.day3].flatMap((d) => d.units);
const targets = units.flatMap((u) => (u.concepts || []).filter((c) => c.attachments?.length).map((c) => ({ unit: u.id, concept: c })));

if (targets.length === 0) { console.log("沒有任何概念掛上附件，略過。"); process.exit(0); }

const server = createServer();
await new Promise((resolve) => server.listen(5179, "127.0.0.1", resolve));
const browser = await chromium.launch();
let failed = 0;
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await page.goto("http://127.0.0.1:5179/", { waitUntil: "networkidle" });

  for (const { unit, concept } of targets) {
    for (const att of concept.attachments) {
      const file = `materials/${att.name}.${(att.type || "MD").toLowerCase()}`;
      // ① 檔案要真的抓得到（相對路徑，子路徑部署也才不會失效）
      const status = await page.evaluate((f) => fetch(f).then((r) => r.status).catch(() => 0), file);
      // ② 附件列要出現在該概念卡片內，且點擊標題能開出預覽 Modal
      const card = page.locator("article.concept", { has: page.locator(`.concept-heading:text-is("${concept.heading}")`) }).first();
      const link = card.locator(`.attachment-row a[data-material-name="${att.name}"]`);
      const hasRow = (await link.count()) > 0;
      let previewOk = false;
      if (hasRow) {
        await link.first().click();
        await page.waitForFunction(() => document.querySelector(".modal-card") && !document.querySelector(".modal-loading"), null, { timeout: 10000 }).catch(() => {});
        previewOk = (await page.locator(".modal-body h1").count()) > 0;
        await page.locator('[data-action="close-modal"]').first().click();
      }
      const ok = status === 200 && hasRow && previewOk;
      if (!ok) failed++;
      console.log(`${ok ? "OK  " : "FAIL"} ${unit} / ${concept.heading} → ${file}（HTTP ${status}、附件列 ${hasRow ? "有" : "無"}、預覽 ${previewOk ? "正常" : "失敗"}）`);
    }
  }
} finally {
  await browser.close();
  server.close();
}
if (failed > 0) { console.error(`FAIL：${failed} 個附件有問題`); process.exit(1); }
console.log(`PASS：${targets.length} 個概念的附件皆可預覽與下載`);
