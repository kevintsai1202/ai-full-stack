import { createRequire } from 'node:module';
import { readdir, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// 從 teaching-site 共用既有 Playwright 安裝，避免新增第二份 node_modules。
const require = createRequire(new URL('../../../teaching-site/package.json', import.meta.url));
const { chromium } = require('playwright');
const diagramsDir = fileURLToPath(new URL('../assets/diagrams/', import.meta.url));

/** 將每一個 SVG 以瀏覽器實際解碼後截圖成 PNG，保留中文字型與向量排版效果。 */
async function renderSvgToPng(browser, svgPath, pngPath) {
  const svg = await readFile(svgPath, 'utf8');
  const page = await browser.newPage({ viewport: { width: 1400, height: 620 }, deviceScaleFactor: 2 });
  await page.setContent(`<!doctype html><html><body style="margin:0;background:#0d1728"><img id="diagram" src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}" /></body></html>`);
  await page.locator('#diagram').waitFor({ state: 'visible' });
  await page.locator('#diagram').screenshot({ path: pngPath, type: 'png' });
  await page.close();
}

/** 批次轉換目錄中的 SVG，讓新增流程圖只需要重跑一個命令。 */
async function main() {
  const browser = await chromium.launch();
  try {
    const files = (await readdir(diagramsDir)).filter((file) => file.endsWith('.svg')).sort();
    for (const file of files) {
      const stem = file.slice(0, -4);
      await renderSvgToPng(browser, resolve(diagramsDir, file), resolve(diagramsDir, `${stem}.png`));
      console.log(`rendered ${stem}.png`);
    }
  } finally {
    await browser.close();
  }
}

await main();
