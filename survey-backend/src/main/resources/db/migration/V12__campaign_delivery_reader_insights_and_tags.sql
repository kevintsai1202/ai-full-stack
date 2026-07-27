-- 電子報內容與寄送批次拆分、逐收件人狀態、文章標籤。
-- campaign 繼續代表一篇可發布文章；campaign_batch 才代表一次實際寄送。

CREATE TABLE campaign_batch (
    id              BIGSERIAL   PRIMARY KEY,
    campaign_id     BIGINT      NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    mode            TEXT        NOT NULL, -- now / schedule / legacy
    scheduled_at    TIMESTAMPTZ,
    status          TEXT        NOT NULL, -- sending / scheduled / sent / partial / failed / cancelled
    requested_count INT         NOT NULL DEFAULT 0,
    accepted_count  INT         NOT NULL DEFAULT 0,
    failed_count    INT         NOT NULL DEFAULT 0,
    skipped_count   INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX idx_campaign_batch_campaign ON campaign_batch (campaign_id, created_at DESC);
CREATE INDEX idx_campaign_batch_status ON campaign_batch (status, scheduled_at);

CREATE TABLE campaign_recipient (
    id                  BIGSERIAL   PRIMARY KEY,
    campaign_id         BIGINT      NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    person_id           BIGINT      REFERENCES audience_person(id) ON DELETE SET NULL,
    batch_id            BIGINT      REFERENCES campaign_batch(id) ON DELETE SET NULL,
    email               TEXT        NOT NULL,
    email_normalized    TEXT        NOT NULL,
    status              TEXT        NOT NULL, -- SENDING / SCHEDULED / SENT / FAILED / CANCELLED
    provider_message_id TEXT,
    error               TEXT,
    scheduled_at        TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_campaign_recipient UNIQUE (campaign_id, email_normalized)
);
CREATE INDEX idx_campaign_recipient_campaign_status
    ON campaign_recipient (campaign_id, status, email_normalized);
CREATE INDEX idx_campaign_recipient_person
    ON campaign_recipient (person_id, sent_at DESC);
CREATE INDEX idx_campaign_recipient_batch ON campaign_recipient (batch_id);

ALTER TABLE email_log ADD COLUMN batch_id BIGINT REFERENCES campaign_batch(id) ON DELETE SET NULL;
CREATE INDEX idx_email_log_batch ON email_log (batch_id);
CREATE INDEX idx_email_log_recipient_lower ON email_log (lower(recipient), created_at DESC);

-- 將既有寄送型 campaign 視為第一個 legacy batch。
INSERT INTO campaign_batch (
    campaign_id, mode, scheduled_at, status,
    requested_count, accepted_count, failed_count, completed_at, created_at
)
SELECT
    c.id,
    COALESCE(c.mode, 'legacy'),
    c.scheduled_at,
    c.status,
    c.recipient_count,
    c.accepted_count,
    c.failed_count,
    CASE WHEN c.status IN ('sent', 'failed', 'cancelled') THEN c.created_at ELSE NULL END,
    c.created_at
FROM campaign c
WHERE c.mode <> 'publish'
  AND EXISTS (SELECT 1 FROM email_log e WHERE e.campaign_id = c.id);

UPDATE email_log e
   SET batch_id = b.id
  FROM campaign_batch b
 WHERE b.campaign_id = e.campaign_id
   AND e.batch_id IS NULL;

-- 每篇文章與每個 email 只保留最新寄送狀態，作為日後補寄的防重複基線。
INSERT INTO campaign_recipient (
    campaign_id, person_id, batch_id, email, email_normalized, status,
    provider_message_id, error, scheduled_at, sent_at, created_at, updated_at
)
SELECT DISTINCT ON (e.campaign_id, lower(trim(e.recipient)))
    e.campaign_id,
    p.id,
    e.batch_id,
    e.recipient,
    lower(trim(e.recipient)),
    CASE e.status
        WHEN 'sent' THEN 'SENT'
        WHEN 'scheduled' THEN 'SCHEDULED'
        WHEN 'cancelled' THEN 'CANCELLED'
        ELSE 'FAILED'
    END,
    e.provider_message_id,
    e.error,
    CASE WHEN e.status = 'scheduled' THEN c.scheduled_at ELSE NULL END,
    CASE WHEN e.status = 'sent' THEN e.created_at ELSE NULL END,
    e.created_at,
    e.created_at
FROM email_log e
JOIN campaign c ON c.id = e.campaign_id
LEFT JOIN audience_person p ON p.email_normalized = lower(trim(e.recipient))
WHERE e.campaign_id IS NOT NULL
  AND e.type = 'campaign'
ORDER BY e.campaign_id, lower(trim(e.recipient)), e.created_at DESC, e.id DESC
ON CONFLICT (campaign_id, email_normalized) DO NOTHING;

-- 文章封面 Emoji 與正規化 hashtag。
ALTER TABLE campaign ADD COLUMN cover_emoji TEXT;

CREATE TABLE content_tag (
    id              BIGSERIAL   PRIMARY KEY,
    name            TEXT        NOT NULL,
    normalized_key  TEXT        NOT NULL UNIQUE,
    slug            TEXT        NOT NULL UNIQUE,
    preset          BOOLEAN     NOT NULL DEFAULT FALSE,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order      INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campaign_tag (
    campaign_id BIGINT NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    tag_id      BIGINT NOT NULL REFERENCES content_tag(id) ON DELETE CASCADE,
    PRIMARY KEY (campaign_id, tag_id)
);
CREATE INDEX idx_campaign_tag_tag ON campaign_tag (tag_id, campaign_id);

INSERT INTO content_tag (name, normalized_key, slug, preset, sort_order) VALUES
    ('AI', 'ai', 'ai', TRUE, 10),
    ('AI Agent', 'ai agent', 'ai-agent', TRUE, 20),
    ('RAG', 'rag', 'rag', TRUE, 30),
    ('Spring Boot', 'spring boot', 'spring-boot', TRUE, 40),
    ('全端開發', '全端開發', 'full-stack', TRUE, 50),
    ('電子報經營', '電子報經營', 'newsletter', TRUE, 60)
ON CONFLICT (normalized_key) DO NOTHING;
