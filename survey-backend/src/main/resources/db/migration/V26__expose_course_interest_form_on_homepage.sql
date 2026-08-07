-- 課程興趣問卷於讀者首頁曝光（方案1）：讀者從首頁「問卷調查」清單進入
-- /r/survey/fullstack-course-interest，走電子報通道填答即可獲得問卷點數，
-- 且數據與 survey.springai.world 課程頁匯流於同一個 schema key（fullstack-course-interest@1）。
-- V25 加欄位時刻意預設 false（既有問卷不突然曝光），此處是對這一份問卷的明確產品決定；
-- 後台仍可隨時經 PUT /api/admin/forms/{formKey}/homepage 調整。
UPDATE form_definition
   SET homepage_visible = TRUE,
       homepage_order = 1,
       updated_at = now()
 WHERE form_key = 'fullstack-course-interest';
