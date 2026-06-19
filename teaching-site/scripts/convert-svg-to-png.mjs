/**
 * 將教學網站中行內 SVG 概念圖轉換為 PNG 檔案。
 * 使用 Playwright 開啟頁面，逐一擷取每個 SVG 圖解並存為 PNG。
 *
 * 使用方式：node scripts/convert-svg-to-png.mjs [baseUrl]
 * 預設 baseUrl 為 http://127.0.0.1:5500/teaching-site/index.html
 */
import { chromium } from "playwright";
import { fileURLToPath } from "url";
import path from "path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ILLUSTRATIONS_DIR = path.resolve(__dirname, "..", "assets", "illustrations");

/** SVG 概念標題 → PNG 檔名對應表 */
const CONCEPT_MAP = [
  { unit: "u1", heading: "環境準備重點", filename: "concept-environment-prep.png" },
  { unit: "u2", heading: "Spring MVC 的核心：請求如何流動", filename: "concept-spring-mvc-flow.png" },
  { unit: "u2", heading: "什麼是 REST API", filename: "concept-rest-api.png" },
  { unit: "u3", heading: "JPA 解決了什麼問題", filename: "concept-jpa-mapping.png" },
  { unit: "u3", heading: "Flyway 的角色", filename: "concept-flyway-strategy.png" },
  { unit: "u4", heading: "AOP 概念圖解", filename: "concept-aop.png" },
  { unit: "u4", heading: "後端安全設定範例 (SecurityConfig.java)", filename: "concept-security-chain.png" },
  { unit: "u5", heading: "開發端代理與後端 API 串接 (Vite Proxy)", filename: "concept-vite-proxy.png" },
  { unit: "u6", heading: "串流輸出為什麼重要", filename: "concept-sse-stream.png" },
  { unit: "u8", heading: "Embabel 智慧 Agent、GOAP 演算法與 Blackboard 機制", filename: "concept-agent-goap.png" },
];

const baseUrl = process.argv[2] || "http://127.0.0.1:5500/teaching-site/index.html";

(async () => {
  console.log("🚀 開始轉換 SVG → PNG");
  console.log(`📍 目標頁面: ${baseUrl}`);
  console.log(`📂 輸出目錄: ${ILLUSTRATIONS_DIR}\n`);

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto(baseUrl, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);

  let success = 0;
  let skipped = 0;

  for (const item of CONCEPT_MAP) {
    console.log(`🔍 處理: ${item.heading} → ${item.filename}`);

    try {
      // 步驟 1：點選對應的觀念 Tab
      const tabClicked = await page.evaluate((heading) => {
        const tabs = Array.from(document.querySelectorAll('[data-action="concept-tab"]'));
        const tab = tabs.find((t) => t.textContent.trim() === heading);
        if (tab) {
          tab.click();
          return true;
        }
        return false;
      }, item.heading);

      if (!tabClicked) {
        console.log(`  ⚠️ 找不到 Tab: "${item.heading}"，跳過`);
        skipped++;
        continue;
      }

      await page.waitForTimeout(500);

      // 步驟 2：找到 SVG 元素並截圖
      const svgElement = await page.$("svg.concept-svg-illustration");
      if (!svgElement) {
        console.log(`  ⚠️ 找不到 SVG 元素，跳過`);
        skipped++;
        continue;
      }

      const outputPath = path.join(ILLUSTRATIONS_DIR, item.filename);
      await svgElement.screenshot({ path: outputPath, type: "png" });
      console.log(`  ✅ 已儲存: ${item.filename}`);
      success++;
    } catch (err) {
      console.log(`  ❌ 錯誤: ${err.message}`);
      skipped++;
    }
  }

  await browser.close();
  console.log(`\n🏁 完成！成功 ${success} 個，跳過 ${skipped} 個`);
  console.log(`📂 PNG 檔案存放於: ${ILLUSTRATIONS_DIR}`);
})();
