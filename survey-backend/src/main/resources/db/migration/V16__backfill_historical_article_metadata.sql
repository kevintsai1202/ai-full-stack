-- 補齊正式環境既有電子報的公開文章資料。
-- 所有更新都以 campaign id、主旨與 sent 狀態三重比對，避免其他環境剛好使用相同 id 時誤改資料。

-- 這兩封信早於「寄送時自動發布」功能，因此雖已寄送成功，slug 與 published_at 仍為 NULL。
UPDATE campaign
   SET slug = 'survey-newsletter-system-lessons-20260724',
       published_at = created_at
 WHERE id = 4
   AND subject = '我自己寫了一套問卷＋電子報系統，這是我學到的事'
   AND status = 'sent'
   AND slug IS NULL
   AND published_at IS NULL;

UPDATE campaign
   SET slug = 'rag-law-powers-ai-verification-20260726',
       published_at = created_at
 WHERE id = 8
   AND subject = 'RAG的應用範例'
   AND status = 'sent'
   AND slug IS NULL
   AND published_at IS NULL;

-- 為六篇有效歷史文章分配不同且固定的 Emoji；重新整理頁面時不會隨機跳動。
UPDATE campaign c
   SET cover_emoji = metadata.cover_emoji
  FROM (VALUES
      (2::BIGINT, '【駕馭 AI 的全端實戰養成班】正式開課了！專屬優惠券送你', '🎓'),
      (4::BIGINT, '我自己寫了一套問卷＋電子報系統，這是我學到的事', '📬'),
      (5::BIGINT, '為什麼我開始寫電子報？影片來不及講的那些事', '✍️'),
      (6::BIGINT, '【鐵人賽完賽攻略】不只寫滿 30 天！教你用 AI 變出技術書的 6 個實戰心法', '📚'),
      (8::BIGINT, 'RAG的應用範例', '⚖️'),
      (9::BIGINT, '早鳥優惠只剩兩天！全新電子報系統也更新了', '🚀')
  ) AS metadata(campaign_id, subject, cover_emoji)
 WHERE c.id = metadata.campaign_id
   AND c.subject = metadata.subject
   AND c.status = 'sent'
   AND c.cover_emoji IS NULL;

-- 使用 V12 已建立的預設 hashtag；只新增缺少的關聯，不覆蓋日後由後台新增的標籤。
WITH assignments(campaign_id, subject, normalized_key) AS (
    VALUES
      (2::BIGINT, '【駕馭 AI 的全端實戰養成班】正式開課了！專屬優惠券送你', 'ai'),
      (2::BIGINT, '【駕馭 AI 的全端實戰養成班】正式開課了！專屬優惠券送你', 'spring boot'),
      (2::BIGINT, '【駕馭 AI 的全端實戰養成班】正式開課了！專屬優惠券送你', '全端開發'),
      (4::BIGINT, '我自己寫了一套問卷＋電子報系統，這是我學到的事', '全端開發'),
      (4::BIGINT, '我自己寫了一套問卷＋電子報系統，這是我學到的事', '電子報經營'),
      (5::BIGINT, '為什麼我開始寫電子報？影片來不及講的那些事', 'ai'),
      (5::BIGINT, '為什麼我開始寫電子報？影片來不及講的那些事', '電子報經營'),
      (6::BIGINT, '【鐵人賽完賽攻略】不只寫滿 30 天！教你用 AI 變出技術書的 6 個實戰心法', 'ai'),
      (6::BIGINT, '【鐵人賽完賽攻略】不只寫滿 30 天！教你用 AI 變出技術書的 6 個實戰心法', '全端開發'),
      (8::BIGINT, 'RAG的應用範例', 'ai'),
      (8::BIGINT, 'RAG的應用範例', 'ai agent'),
      (8::BIGINT, 'RAG的應用範例', 'rag'),
      (9::BIGINT, '早鳥優惠只剩兩天！全新電子報系統也更新了', 'ai'),
      (9::BIGINT, '早鳥優惠只剩兩天！全新電子報系統也更新了', 'spring boot'),
      (9::BIGINT, '早鳥優惠只剩兩天！全新電子報系統也更新了', '全端開發'),
      (9::BIGINT, '早鳥優惠只剩兩天！全新電子報系統也更新了', '電子報經營')
)
INSERT INTO campaign_tag (campaign_id, tag_id)
SELECT c.id, t.id
  FROM assignments a
  JOIN campaign c
    ON c.id = a.campaign_id
   AND c.subject = a.subject
   AND c.status = 'sent'
  JOIN content_tag t ON t.normalized_key = a.normalized_key
ON CONFLICT DO NOTHING;
