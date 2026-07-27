-- 補齊公開問卷選項，讓讀者頁可完全依 schema 產生欄位。
-- 套用至所有版本，建立新草稿時複製的選項也會保持完整。
UPDATE form_field
SET options = CASE field_key
    WHEN 'role' THEN '[
      "學生","應屆畢業生","後端工程師","前端工程師","全端工程師",
      "行動 App 開發","資料／AI 工程師","DevOps／SRE","軟體架構師",
      "技術主管／PM","非本科轉職者","接案／自由工作者","創業者",
      "企業內訓窗口","其他"
    ]'::jsonb
    WHEN 'experience' THEN '["沒有經驗","半年內","1-3 年","3 年以上"]'::jsonb
    WHEN 'frontendExperience' THEN '["沒有經驗","半年內","1-3 年","3 年以上"]'::jsonb
    WHEN 'interest' THEN '[
      "RAG 知識庫","Tool Calling","前端整合","Spring 其他模組",
      "AI 輔助程式開發","Spring Security","資料庫","Docker 部署"
    ]'::jsonb
    WHEN 'budget' THEN '["4000 以下","4000-5000","5000-6000","6000 以上"]'::jsonb
    WHEN 'status' THEN '[
      "在職工程師，想技能升級","想轉職全端／AI 工程師","在公司推動 AI 轉型",
      "應屆／畢業生求職中","學生","熟練 AI 工具但沒有開發經驗","其他"
    ]'::jsonb
    WHEN 'goals' THEN '[
      "加薪・薪資溢價","轉職進入 AI 領域","累積可展示的實戰作品集",
      "技能升級、不被 AI 取代","帶領團隊／公司導入 AI"
    ]'::jsonb
    WHEN 'pain_points' THEN '[
      "跟不上 AI 浪潮","薪資成長停滯","技能被 AI 取代","轉職卡關",
      "缺乏可展示的實戰專案"
    ]'::jsonb
    ELSE options
END,
field_type = CASE
    WHEN field_key = 'goals' THEN 'multi_select'
    ELSE field_type
END
WHERE field_key IN (
  'role','experience','frontendExperience','interest',
  'budget','status','goals','pain_points'
);

-- 舊固定頁面原本已有自由填寫建議；納入 schema 後才能在切換新版時繼續保存。
INSERT INTO form_field (
    form_definition_id, field_key, label, field_type, required, options,
    analytics_enabled, analytics_view, filterable, sensitive,
    public_analytics, display_order, fact_key
)
SELECT
    definition.id, 'suggestion', '有什麼建議，或還想多學的技術？',
    'long_text', FALSE, '[]'::jsonb,
    TRUE, 'list', FALSE, FALSE, FALSE, 90, 'survey.suggestion'
FROM form_definition definition
WHERE definition.form_key = 'fullstack-course-interest'
  AND NOT EXISTS (
    SELECT 1
    FROM form_field field
    WHERE field.form_definition_id = definition.id
      AND field.field_key = 'suggestion'
  );
