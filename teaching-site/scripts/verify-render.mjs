import assert from "node:assert/strict";
import { execFile, spawn } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import playwright from "playwright";

// 取得當前專案根目錄
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const screenshotDir = path.join(root, "data", "render-verify");
const defaultUrl = "http://127.0.0.1:5173/";
const url = process.env.URL || defaultUrl;

// 定義不同裝置的解析度規格
const profiles = [
  { id: "desktop", viewport: { width: 1440, height: 1000 } },
  { id: "mobile", viewport: { width: 390, height: 844 } }
];

/**
 * 等待本地靜態檔案伺服器可用。
 */
async function waitForServer(targetUrl, timeoutMs = 20000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const response = await fetch(targetUrl);
      if (response.ok) return;
    } catch {
      // 伺服器尚未完成啟動時會連線失敗，稍後重試。
    }
    await new Promise((resolve) => setTimeout(resolve, 350));
  }
  throw new Error(`Server did not become ready: ${targetUrl}`);
}

/**
 * 若呼叫端沒有指定 URL，測試腳本自行啟動靜態檔案伺服器。
 */
async function withServer(callback) {
  if (process.env.URL) {
    await callback();
    return;
  }

  const command = process.platform === "win32" ? "cmd.exe" : "python3";
  const args = process.platform === "win32"
    ? ["/d", "/s", "/c", "python -m http.server 5173"]
    : ["-m", "http.server", "5173"];
  const server = spawn(command, args, {
    cwd: root,
    stdio: "pipe"
  });

  try {
    await waitForServer(url);
    await callback();
  } finally {
    if (process.platform === "win32") {
      await new Promise((resolve) => {
        execFile("taskkill", ["/PID", String(server.pid), "/T", "/F"], () => resolve());
      });
    } else {
      server.kill("SIGTERM");
    }
  }
}

/**
 * 驗證單一解析度下的網頁載入、互動、圖片成功加載與 progress 持久化。
 */
async function verifyProfile(browser, profile) {
  const context = await browser.newContext({ viewport: profile.viewport, locale: "zh-TW" });
  const page = await context.newPage();
  const errors = [];

  // 監聽頁面錯誤與 console 錯誤
  page.on("pageerror", (error) => errors.push(`[pageerror] ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(`[console.error] ${message.text()}`);
  });

  // 造訪網頁並等待單元渲染完成
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForSelector(".unit", { timeout: 10000 });

  // 1. 驗證課程標題是否存在且正確
  const title = await page.locator("#courseTitle").innerText();
  assert.match(title, /AI 賦能全端開發/, `${profile.id}: 課程標題未正確渲染`);

  // 2. 驗證單元數量是否為 8 個
  const unitCount = await page.locator(".unit").count();
  assert.equal(unitCount, 8, `${profile.id}: 渲染的單元數量錯誤 (預期為 8)`);

  // 3. 驗證所有頁面上的圖片皆加載成功 (寬度大於 0 且載入完成)
  const images = await page.locator("img");
  const imgCount = await images.count();
  for (let i = 0; i < imgCount; i++) {
    const isLoaded = await images.nth(i).evaluate((img) => img.complete && img.naturalWidth > 0);
    assert.equal(isLoaded, true, `${profile.id}: 圖片載入失敗 - ${await images.nth(i).getAttribute("src")}`);
  }

  // 4. 驗證無水平溢出 (RWD 切版正確性)
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth
  }));
  assert.equal(overflow.scrollWidth <= overflow.innerWidth + 1, true, `${profile.id}: 發生水平溢出 ${overflow.scrollWidth} > ${overflow.innerWidth}`);

  // 5. 驗證搜尋功能 (搜尋 Embabel 應至少有一個單元符合)
  await page.locator("#searchInput").fill("Embabel");
  const filteredUnits = await page.locator(".unit").count();
  assert.equal(filteredUnits >= 1, true, `${profile.id}: 搜尋 Embabel 沒有找到任何結果`);
  await page.locator("#searchInput").fill("");

  // 6. 驗證學習進度勾選是否成功保存至 localStorage (Reload 後應仍勾選)
  await page.locator(".check").first().click();
  await page.reload({ waitUntil: "networkidle" });
  await page.waitForSelector(".unit", { timeout: 10000 });
  const done = await page.locator(".check.done").first().count();
  assert.equal(done > 0, true, `${profile.id}: 進度勾選狀態在重新整理後丟失`);

  // 7. 驗證複製提示詞按鈕的功能 (複製成功後文字應變更為「已複製」)
  await page.locator("[data-action='copy-prompt']").first().click();
  await page.waitForFunction(() => document.querySelector("[data-action='copy-prompt']")?.textContent === "已複製", null, { timeout: 5000 });
  await page.evaluate(() => window.scrollTo(0, 0));

  // 8. 建立截圖目錄並儲存畫面以利人工稽核
  await fs.mkdir(screenshotDir, { recursive: true });
  await page.screenshot({ path: path.join(screenshotDir, `${profile.id}-home.png`), fullPage: false });

  // 確保無執行階段 JavaScript 錯誤
  assert.equal(errors.length, 0, `${profile.id}: 發生 console 錯誤:\n${errors.join("\n")}`);
  await context.close();
}

/**
 * 主程序：對桌面版與行動版解析度分別執行測試。
 */
async function main() {
  await withServer(async () => {
    const browser = await playwright.chromium.launch({ headless: true });
    try {
      for (const profile of profiles) {
        await verifyProfile(browser, profile);
      }
      console.log(`OK: rendered site verified at ${url}`);
    } finally {
      await browser.close();
    }
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
