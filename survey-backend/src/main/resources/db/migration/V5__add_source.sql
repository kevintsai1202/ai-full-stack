-- 名單來源欄位：區分問卷填寫（survey_form）與外部匯入（如線上測驗 exam）
ALTER TABLE survey_response ADD COLUMN source TEXT NOT NULL DEFAULT 'survey_form';
