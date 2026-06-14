import fs from "node:fs/promises";
import path from "node:path";
import playwright from "playwright";

/** SVG 目錄與 viewport 設定 */
const ASSETS_DIR = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  "..",
  "assets",
  "illustrations"
);
const VIEWBOX = { w: 1200, h: 675 };
const SCALE = 2; // 2x 輸出，解析度 2400x1350

/**
 * 將單一 SVG 檔案渲染為 PNG
 */
async function convertSvgToPng(page, svgPath, pngPath) {
  const svgContent = await fs.readFile(svgPath, "utf-8");

  // 建立獨立 HTML 頁面嵌入 SVG，確保正確縮放
  const html = `<!DOCTYPE html>
<html><body style="margin:0;background:transparent;">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${VIEWBOX.w} ${VIEWBOX.h}"
     width="${VIEWBOX.w}" height="${VIEWBOX.h}">
  ${svgContent.replace(/<svg[^>]*>|<\/svg>/gi, "")}
</svg>
</body></html>`;

  // 設定 body 內容並等待渲染
  await page.setContent(html, { waitUntil: "networkidle" });

  // 定位 SVG 元素並截圖
  const svgEl = page.locator("svg");
  await svgEl.waitFor({ state: "visible" });

  // 截圖並儲存
  await svgEl.screenshot({ path: pngPath });
}

async function main() {
  // 啟動 Playwright 瀏覽器
  const browser = await playwright.chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: VIEWBOX.w, height: VIEWBOX.h },
    deviceScaleFactor: SCALE,
  });

  // 取得所有 SVG 檔案
  const files = await fs.readdir(ASSETS_DIR);
  const svgFiles = files
    .filter((f) => f.endsWith(".svg"))
    .sort();

  let success = 0;
  let fail = 0;

  for (const svgFile of svgFiles) {
    const svgPath = path.join(ASSETS_DIR, svgFile);
    const pngFile = svgFile.replace(/\.svg$/i, ".png");
    const pngPath = path.join(ASSETS_DIR, pngFile);

    try {
      await convertSvgToPng(page, svgPath, pngPath);
      console.log(`OK: ${svgFile} → ${pngFile}`);
      success++;
    } catch (err) {
      console.error(`ERR: ${svgFile} → ${err.message}`);
      fail++;
    }
  }

  await browser.close();

  console.log(`\n轉換完成：${success} 成功、${fail} 失敗`);
  if (fail > 0) process.exitCode = 1;
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
