# 手機版直式宣傳圖

這個資料夾存放 `land-page/index.html` 在 `max-width: 560px` 以下使用的直式素材。桌機版仍使用 `assets/promo/*.png` 的 16:9 橫式圖，手機版透過 `<picture><source media="(max-width: 560px)">` 切換到這批 9:16 圖。

## 檔案對應

| 手機圖 | 桌機圖 | 用途 |
|---|---|---|
| `tech-stack-map-mobile.png` | `../tech-stack-map.png` | 企業級 AI 全端技術棧 |
| `feature-tool-calling-mobile.png` | `../feature-tool-calling.png` | Tool Calling 工程邊界 |
| `feature-rag-knowledge-mobile.png` | `../feature-rag-knowledge.png` | RAG 知識庫流程 |
| `market-advantage-mobile.png` | `../market-advantage.png` | AI 工程師市場優勢 |
| `career-boost-mobile.png` | `../career-boost.png` | 職涯加速 |
| `audience-fit-mobile.png` | `../audience-fit.png` | 適合對象 |
| `curriculum-roadmap-mobile.png` | `../curriculum-roadmap.png` | 8 大單元路線圖 |
| `demo-day-flow-mobile.png` | `../demo-day-flow.png` | Demo Day 驗收流程 |
| `course-comparison-mobile.png` | `../course-comparison.png` | 課程比較 |
| `learning-workspace-mobile.png` | `../learning-workspace.png` | 線上實作工作台 |

## 生成規格

- 工具：Codex 內建 `image_gen`
- 比例：9:16 portrait
- 解析度：941 x 1672
- 風格：clean Taiwanese edtech / SaaS course marketing，teal + amber accents
- 限制：避免過密小字、避免 logo / watermark / fake brand marks，中文字只放大標題與必要標籤

## Prompt 共通前綴

```text
Use case: ads-marketing or scientific-educational
Asset type: mobile portrait landing-page illustration, 9:16
Scene/backdrop: premium online course promo card, clean light background with teal and amber accents
Style/medium: crisp vector-like raster infographic, modern Taiwanese edtech marketing
Composition/framing: portrait 9:16, large readable modules, generous spacing, optimized for a 390px-wide mobile screen
Constraints: Traditional Chinese text must be accurate if present; avoid dense tiny labels; no logos; no watermark; no fake brand marks
```
