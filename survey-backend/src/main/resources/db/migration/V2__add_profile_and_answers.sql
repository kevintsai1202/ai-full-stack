-- 擴充問卷：新增前端經驗欄位與行銷導向問題答案（彈性 jsonb）
ALTER TABLE survey_response ADD COLUMN frontend_experience TEXT;            -- 前端經驗區間
ALTER TABLE survey_response ADD COLUMN answers JSONB;                        -- 行銷題答案（痛點/急迫度/想學的系統知識…）
