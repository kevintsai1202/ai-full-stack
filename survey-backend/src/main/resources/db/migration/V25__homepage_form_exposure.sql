-- 首頁問卷曝光：預設 false，既有問卷（含 verify-* 測試問卷與 vote-* 信中一鍵題）不會突然曝光給讀者
ALTER TABLE form_definition ADD COLUMN homepage_visible BOOLEAN NOT NULL DEFAULT false;
-- 首頁排序：NULL 表示未指定，讀者端排在最後（避免新勾選的問卷因忘了填順序而消失在清單中間）
ALTER TABLE form_definition ADD COLUMN homepage_order INT;
