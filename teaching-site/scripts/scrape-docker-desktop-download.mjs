// 用途：擷取 Docker Desktop 官方下載頁面截圖，供第三章「安裝 Docker Desktop」小節使用。
// 執行方式：node scripts/scrape-docker-desktop-download.mjs
// 輸出：assets/illustrations/u3-4-docker-desktop-download.png
// 備註：僅擷取視窗可見範圍（非整頁），避免圖片過長；若官網改版導致版面跑掉，可重跑本腳本更新截圖。

import { chromium } from "playwright";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const outputPath = path.join(root, "assets", "illustrations", "u3-4-docker-desktop-download.png");
const targetUrl = "https://www.docker.com/products/docker-desktop/";

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(targetUrl, { waitUntil: "networkidle" });
  // 官網會顯示 cookie 同意橫幅蓋住畫面下緣，需等它出現並關閉後再截圖
  const cookieButton = page.getByRole("button", { name: /accept all cookies|同意/i }).first();
  await cookieButton.waitFor({ state: "visible", timeout: 5000 }).catch(() => {});
  await cookieButton.click().catch(() => {});
  await page.waitForTimeout(500);
  await page.screenshot({ path: outputPath, fullPage: false });
  await browser.close();
  console.log(`已儲存截圖：${outputPath}`);
}

main().catch((err) => {
  console.error("擷取截圖失敗：", err);
  process.exit(1);
});
