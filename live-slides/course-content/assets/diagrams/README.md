# 流程圖資產

這些流程圖採「SVG 來源 → Playwright PNG」產線：

```powershell
node ..\scripts\generate-diagrams.mjs
node ..\scripts\render-diagrams.mjs
```

- `.svg` 是可維護來源，文字與節點可直接修改。
- `.png` 是投影片、文件與不支援 SVG 的環境使用的輸出。
- 圖中的形狀有固定語意：圓角矩形＝Action、膠囊＝狀態／資料、菱形＝判斷、六邊形＝交付目標。
- PNG 由實際瀏覽器解碼後截圖，不用 ImageMagick，也不依賴 AI 生圖文字。

