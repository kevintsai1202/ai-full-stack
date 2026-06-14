import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import playwright from "playwright";

// 取得當前專案根目錄
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

// 設定 Artifact 輸出目錄，指向當前 conversation 歷史的儲存路徑
const artifactsDir = "C:\\Users\\kevintsai\\.gemini\\antigravity-ide\\brain\\232eba6b-d5a1-4927-a611-020b42a07d4b";
const defaultUrl = "http://127.0.0.1:5173/";

/**
 * 核心驗證任務的設定檔，定義每個 SVG 對應的單元、觀念標題與輸出的截圖檔案名稱。
 * @type {Array<{unitId: string, tabText: string, filename: string}>}
 */
const svgVerificationTargets = [
  { unitId: "day1-u1", tabText: "環境準備重點", filename: "svg_concept_1_env_prep.png" },
  { unitId: "day1-u2", tabText: "Spring MVC 的核心：請求如何流動", filename: "svg_concept_2_mvc_flow.png" },
  { unitId: "day1-u2", tabText: "什麼是 REST API", filename: "svg_concept_3_rest_api.png" },
  { unitId: "day1-u3", tabText: "JPA 解決了什麼問題", filename: "svg_concept_4_jpa_mapping.png" },
  { unitId: "day1-u3", tabText: "Flyway 的角色", filename: "svg_concept_5_flyway_strategy.png" },
  { unitId: "day1-u4", tabText: "AOP 概念圖解", filename: "svg_concept_6_aop_concept.png" },
  { unitId: "day1-u4", tabText: "後端安全設定範例 (SecurityConfig.java)", filename: "svg_concept_7_security_chain.png" },
  { unitId: "day2-u5", tabText: "開發端代理與後端 API 串接 (Vite Proxy)", filename: "svg_concept_8_vite_proxy.png" },
  { unitId: "day2-u6", tabText: "串流輸出為什麼重要", filename: "svg_concept_9_sse_stream.png" },
  { unitId: "day2-u8", tabText: "Embabel 智慧 Agent、GOAP 演算法與 Blackboard 機制", filename: "svg_concept_10_agent_goap.png" },
  { unitId: "day2-u7", tabText: "RAG 的基本想法", filename: "svg_concept_11_rag_flow.png" },
  { unitId: "day2-u7", tabText: "ETL 三步驟：文件到向量庫", filename: "svg_concept_12_etl_pipeline.png" }
];

/**
 * 主要執行函式：啟動瀏覽器，連線至開發伺服器，依序點擊並截圖檢驗 SVG。
 */
async function main() {
  console.log("正在啟動 Playwright 進行 SVG 渲染驗證...");
  
  // 啟動 headless 瀏覽器
  const browser = await playwright.chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: "zh-TW"
  });
  const page = await context.newPage();

  try {
    // 進入網頁並等待載入完成
    console.log(`正在連線至 ${defaultUrl} ...`);
    await page.goto(defaultUrl, { waitUntil: "networkidle" });
    
    // 點擊「展開全部」按鈕，確保所有手風琴均展開
    console.log("展開全部手風琴單元...");
    const expandBtn = page.locator("#expandAll");
    await expandBtn.click();
    
    // 強制在瀏覽器中將所有 details 設為 open=true，防止 HTML details 未能完全展開
    await page.evaluate(() => {
      document.querySelectorAll("details").forEach((el) => {
        el.open = true;
      });
    });
    
    // 等待動畫或 React 狀態更新
    await page.waitForTimeout(500);

    // 確保 artifacts 目錄存在
    await fs.mkdir(artifactsDir, { recursive: true });

    // 逐一尋訪目標 SVG 並進行驗證
    for (const target of svgVerificationTargets) {
      console.log(`\n======================================================`);
      console.log(`正在檢驗觀念: "${target.tabText}" (單元: ${target.unitId})`);

      // 1. 定位該單元區塊
      const unitSection = page.locator(`#${target.unitId}`);
      await unitSection.scrollIntoViewIfNeeded();
      await page.waitForTimeout(200);

      // 2. 在該單元中找到對應的 Tab 按鈕並點擊
      const tabBtn = unitSection.locator(`.concept-tab-btn:has-text("${target.tabText}")`);
      const btnCount = await tabBtn.count();
      
      if (btnCount === 0) {
        throw new Error(`找不到標題為 "${target.tabText}" 的概念 Tab 按鈕！`);
      }
      
      await tabBtn.first().click();
      await page.waitForTimeout(350); // 等待 React 重新渲染動畫

      // 3. 定位視覺容器 (.concept-visual-col) 與內部的 svg/img 元素
      const visualCol = unitSection.locator(".concept-visual-col");
      const mediaElement = visualCol.locator("svg, img");

      // 確保視覺容器與媒體元素存在
      await visualCol.waitFor({ state: "visible", timeout: 5000 });
      await mediaElement.first().waitFor({ state: "attached", timeout: 5000 });

      // 4. 進行高度與裁剪 Bug 檢查 (防止 overflow 裁剪問題)
      const layoutStats = await visualCol.evaluate((container) => {
        const rect = container.getBoundingClientRect();
        const media = container.querySelector("svg, img");
        const mediaRect = media ? media.getBoundingClientRect() : null;
        
        // 取得容器與媒體的幾何關係
        const isClipped = mediaRect ? (mediaRect.bottom > rect.bottom || mediaRect.right > rect.right) : false;
        
        return {
          containerHeight: rect.height,
          containerWidth: rect.width,
          mediaHeight: mediaRect ? mediaRect.height : 0,
          isClipped: isClipped,
          overflowStyle: window.getComputedStyle(container).overflow
        };
      });

      console.log(`- 容器尺寸: ${layoutStats.containerWidth}x${layoutStats.containerHeight} px`);
      console.log(`- 媒體高度: ${layoutStats.mediaHeight} px`);
      console.log(`- 是否遭裁剪: ${layoutStats.isClipped ? "是 (⚠️有元件溢出裁切風險!)" : "否 (正常)"}`);
      console.log(`- 溢出樣式 (overflow): ${layoutStats.overflowStyle}`);

      // 5. 進行局部截圖，儲存至 artifacts 資料夾下
      const screenshotPath = path.join(artifactsDir, target.filename);
      await visualCol.screenshot({ path: screenshotPath });
      console.log(`- 截圖已儲存: ${screenshotPath}`);

      // 防禦性斷言，確保高度至少為 240px 且未被壓縮裁剪
      assert.ok(layoutStats.containerHeight >= 240, `視覺容器高度過低 (${layoutStats.containerHeight}px)，可能仍有裁剪風險！`);
      assert.equal(layoutStats.isClipped, false, `元素大小超出視覺容器邊界，出現裁剪現象！`);
    }

    console.log("\n======================================================");
    console.log("🎉 所有 12 個核心概念的 SVG 圖解已全數通過 Playwright 佈局與裁剪驗證！");

  } catch (error) {
    console.error("❌ 驗證過程中發生錯誤:", error);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
}

main();
