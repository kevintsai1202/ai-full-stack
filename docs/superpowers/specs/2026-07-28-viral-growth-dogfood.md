# 病毒成長系統瀏覽器驗收紀錄

驗收日期：2026-07-28  
環境：本機 PostgreSQL 18、Spring Boot JAR、Chromium 1280px／390px

## 驗收結果

- 一般邀請連結 `/r/?ref=...` 與文章分享連結 `/r/news/{slug}?ref=...` 都會寫入每日唯一點擊；公開 API 對有效、無效推薦碼均回相同的 `204`。
- 文章底部會顯示低干擾的「分享後賺點」，可關閉或捲動至分享工具。
- Facebook、Instagram、Threads、複製貼文及限動圖卡操作皆可見且排列整齊。
- 動態圖卡可輸出 Open Graph 1200×630 與限時動態 1080×1920 PNG。
- 「我的邀請」會顯示進行中的加碼活動、分享範本、邀請成效與 3／5／10 人里程碑。
- Admin「病毒成長」可顯示點擊、填表、確認、熱門來源、待人工審核與限時活動。
- 實際建立文章限定 ×3 活動成功；實際拒絕可疑邀請後，狀態改為 `REJECTED` 且不發點。
- 1280px 桌機與 390px 手機均無水平溢出。

## 驗收時發現並修正

### DOGFOOD-001：分享漏斗 KPI 桌機版未對齊

- 嚴重度：Medium
- 區域：Admin／病毒成長
- 現象：五個 KPI 使用原本四欄版型，第五張卡片掉到第二列，數值與標籤也在同一行。
- 修正：病毒成長改為桌機五欄、平板兩欄、手機一欄；標籤與數值改為上下層級。
- 驗證：五張卡片的 `top` 相同、寬度相同，且頁面無水平溢出。

## 畫面證據

- `artifacts/viral-growth-dogfood/home.png`
- `artifacts/viral-growth-dogfood/article-share.png`
- `artifacts/viral-growth-dogfood/article-mobile.png`
- `artifacts/viral-growth-dogfood/invite.png`
- `artifacts/viral-growth-dogfood/invite-milestones.png`
- `artifacts/viral-growth-dogfood/story-card.png`
- `artifacts/viral-growth-dogfood/admin-growth-fixed.png`
