# Hahow 課程內容圖獨立素材包

本資料夾保存 Hahow 售課用途的「有字版」課程內容圖。這批素材不會接回 `land-page/index.html`，僅作為獨立宣傳圖與預覽頁使用。

## 製作方式

1. 使用 Codex 內建 `image_gen`（image2）產生無文字背景圖。
2. 將背景圖複製為 `bg-*.png`，保留原始生成檔不刪除。
3. 執行 `land-page/scripts/generate-hahow-image2-course-content.mjs`，用本地 HTML/CSS 疊加固定中文文案。
4. 執行 `land-page/scripts/convert-to-webp.mjs`，產出同名 WebP。

## 最終輸出

| 檔名 | 主題 |
|---|---|
| `cover-wide-title.png` | 長方形封面圖，僅含大標與小標 |
| `cover-square-title.png` | 方形封面圖，僅含大標與小標 |
| `01-ai-crm-project-course.png` | 課程主軸：做出一套 AI CRM |
| `02-eight-unit-roadmap-course.png` | 課程內容：8 大單元章章相連 |
| `03-rag-tool-calling-course.png` | AI 落地：RAG + Tool Calling |
| `04-demo-day-outcome-course.png` | 結訓成果：Demo Day 跑給別人看 |
| `banner-01.png` ~ `banner-08.png` | 課程內容章節橫幅長條文字圖 |
| `preview.html` | 獨立預覽頁，不接回正式 landing page |

## 文案原則

- 文字短句化，符合售課頁快速掃讀。
- 中文由本地渲染，不交給 image2 直接畫字，避免錯字與不可維護。
- 視覺參考 Hahow 類型售課頁常見重點：課程主軸、章節內容、AI 技術價值、完課成果。
- 直式圖下方補充 3 條具體內容，避免留白過多。
- 封面圖只放大小標，不放 Hahow 字樣、不放技術 chips、不放其他說明文字。
