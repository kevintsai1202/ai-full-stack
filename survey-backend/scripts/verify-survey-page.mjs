// 問卷頁視覺驗證腳本：啟動本地靜態伺服器渲染 survey 頁，截 desktop / mobile 圖，
// 並檢查 land-page 外引圖片是否全部成功載入（避免再出現「沒圖片」狀況）。
// 用途：改版後可重跑此腳本逐項檢查，失敗時保留截圖供比對。
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const staticDir = path.join(root, "src", "main", "resources", "static");
const shotDir = path.join(root, "data", "survey-render");
const port = 5188;

// 從 teaching-site 借用已安裝的 playwright，避免在 survey-backend 重複安裝
const require = createRequire(path.join(root, "..", "teaching-site", "package.json"));
const playwright = require("playwright");

// 極簡靜態檔案伺服器：只服務 static 目錄下的檔案
function startServer() {
  const types = { ".html": "text/html; charset=utf-8", ".js": "application/javascript", ".png": "image/png", ".css": "text/css" };
  const server = http.createServer(async (req, res) => {
    const rel = decodeURIComponent(req.url.split("?")[0]);
    const file = path.join(staticDir, rel === "/" ? "index.html" : rel);
    try {
      const buf = await fs.readFile(file);
      res.writeHead(200, { "Content-Type": types[path.extname(file)] || "application/octet-stream" });
      res.end(buf);
    } catch {
      res.writeHead(404); res.end("not found");
    }
  });
  return new Promise((resolve) => server.listen(port, () => resolve(server)));
}

async function main() {
  await fs.mkdir(shotDir, { recursive: true });
  const server = await startServer();
  const browser = await playwright.chromium.launch();
  const failedImages = [];
  try {
    const profiles = [
      { id: "desktop", viewport: { width: 1280, height: 1600 } },
      { id: "mobile", viewport: { width: 390, height: 1700 } }
    ];
    // 模擬統計資料：本機純靜態沒有後端 API，攔截 /api/survey/stats 注入假資料，
    // 讓截圖能呈現右側圖表的實際渲染效果（正式環境由 survey-backend 提供真實資料）。
    const mockStats = {
      total: 137,
      interest: [
        { label: "RAG 知識庫", count: 98 }, { label: "Tool Calling", count: 81 },
        { label: "AI 輔助程式開發", count: 73 }, { label: "前端整合", count: 52 },
        { label: "Spring Security", count: 44 }, { label: "資料庫", count: 39 },
        { label: "Docker 部署", count: 31 }, { label: "Spring 其他模組", count: 22 }
      ],
      status: [
        { label: "在職工程師，想技能升級", count: 61 }, { label: "想轉職全端／AI 工程師", count: 38 },
        { label: "在公司推動 AI 轉型", count: 19 }, { label: "熟練 AI 工具但沒有開發經驗", count: 11 },
        { label: "學生", count: 8 }
      ],
      role: [
        { label: "後端工程師", count: 49 }, { label: "全端工程師", count: 28 },
        { label: "前端工程師", count: 21 }, { label: "資料／AI 工程師", count: 17 },
        { label: "非本科轉職者", count: 12 }, { label: "技術主管／PM", count: 10 }
      ]
    };

    for (const p of profiles) {
      const page = await browser.newPage({ viewport: p.viewport });
      // 監看圖片回應狀態，找出載入失敗的外引圖
      page.on("response", (resp) => {
        const u = resp.url();
        if (/\.(png|jpe?g|webp|svg)$/i.test(u) && resp.status() >= 400) failedImages.push(`${resp.status()} ${u}`);
      });
      await page.route("**/api/survey/stats", (route) =>
        route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(mockStats) }));
      await page.goto(`http://127.0.0.1:${port}/`, { waitUntil: "networkidle" });
      await page.waitForTimeout(900); // 等長條圖寬度動畫完成再截圖
      await page.screenshot({ path: path.join(shotDir, `survey-${p.id}.png`), fullPage: true });
      console.log(`✓ 截圖完成: survey-${p.id}.png`);
      await page.close();
    }
  } finally {
    await browser.close();
    server.close();
  }
  if (failedImages.length) {
    console.log("✗ 有圖片載入失敗:\n  " + [...new Set(failedImages)].join("\n  "));
    process.exit(1);
  }
  console.log("✓ 所有圖片載入成功");
}

main().catch((e) => { console.error(e); process.exit(1); });
