/**
 * 擷取 GOAP/Embabel 概念 SVG 為 PNG（補充缺漏用）
 */
import { chromium } from "playwright";
import { fileURLToPath } from "url";
import path from "path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outputPath = path.resolve(__dirname, "..", "assets", "illustrations", "concept-agent-goap.png");

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto("http://127.0.0.1:5500/teaching-site/index.html", { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);

  // 用模糊比對找到包含 GOAP 或 Embabel 的 Tab
  const clicked = await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('[data-action="concept-tab"]'));
    const tab = tabs.find((t) => t.textContent.includes("GOAP") || t.textContent.includes("Embabel"));
    if (tab) { tab.click(); return tab.textContent.trim(); }
    return null;
  });

  console.log("找到的 Tab:", clicked);

  if (clicked) {
    await page.waitForTimeout(500);
    const svg = await page.$("svg.concept-svg-illustration");
    if (svg) {
      await svg.screenshot({ path: outputPath, type: "png" });
      console.log("✅ 已儲存: concept-agent-goap.png");
    } else {
      console.log("⚠️ 找不到 SVG 元素");
    }
  } else {
    console.log("⚠️ 找不到 GOAP/Embabel Tab");
  }

  await browser.close();
})();
