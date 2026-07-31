# Hahow Image2 直式宣傳圖

本資料夾保存以 Codex 內建 `image_gen`（使用者稱 image2）產生的 Hahow 課程宣傳直式圖。這組素材主打「少字、少錯字風險」，圖片本身不放中文文案，正式說明文字放在 HTML caption 與 alt。

## 檔案對應

| 檔名 | 用途 |
|---|---|
| `01-ai-crm-product-hero.png` | AI CRM 完整產品與全端技術棧主視覺 |
| `02-eight-unit-journey.png` | 8 單元學習旅程視覺，僅用 1-8 數字與圖示 |
| `03-rag-tool-calling-visual.png` | RAG、Tool Calling、CRM 資料接回 AI 的概念視覺 |
| `04-demo-day-showcase.png` | Demo Day 結訓展示與作品集情境 |

## 生成規格

- 比例：9:16 portrait
- 風格：clean Taiwanese edtech / SaaS course marketing
- 色系：teal + amber accents
- 文字限制：不放中文，不放可讀 UI 小字，不放 logo / watermark / fake brand marks
- 網頁使用：搭配 `.webp` 優先載入，PNG 作為 fallback

## Prompt 摘要

共通約束：

```text
Use case: ads-marketing
Asset type: Hahow course promotion vertical image, 9:16 portrait
Style/medium: crisp vector-like raster illustration, modern SaaS course marketing
Constraints: no logos, no watermark, no brand marks, no dense tiny labels
Text: no readable text, no Chinese characters, use icons and abstract UI lines only
```

四張主題：

1. AI CRM product workspace：laptop dashboard、backend service blocks、React UI cards、secure login、vector database。
2. Eight-part learning journey：setup、API、database、security、React UI、AI chat、RAG knowledge、demo day。
3. RAG + Tool Calling：AI assistant connected to CRM dashboard、document cards、database、tool/service blocks。
4. Demo Day outcome：finished project presentation、dashboard screen、portfolio artifacts、AI assistant bubble。
