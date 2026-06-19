-- 電子報發送批次：每次發送一筆，供歷史檢視與取消排程
CREATE TABLE campaign (
    id              BIGSERIAL PRIMARY KEY,
    subject         TEXT        NOT NULL,
    markdown        TEXT        NOT NULL,
    body_html       TEXT,
    filter_role     TEXT,
    filter_interest TEXT,
    mode            TEXT        NOT NULL,
    scheduled_at    TIMESTAMPTZ,
    recipient_count INT         NOT NULL DEFAULT 0,
    accepted_count  INT         NOT NULL DEFAULT 0,
    failed_count    INT         NOT NULL DEFAULT 0,
    status          TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- email_log 關聯到 campaign（歡迎信為 null）
ALTER TABLE email_log ADD COLUMN campaign_id BIGINT;
CREATE INDEX idx_email_log_campaign ON email_log (campaign_id);
