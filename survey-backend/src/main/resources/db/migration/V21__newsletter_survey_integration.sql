-- V21__newsletter_survey_integration.sql
-- 電子報問卷整合：一鍵投票表、信中題指定欄、填答發點防重發。
-- 設計依據 docs/superpowers/specs/2026-08-03-newsletter-survey-integration-design.md §4

-- 信中一鍵投票（含讀者頁快投）
CREATE TABLE survey_vote (
    id            BIGSERIAL PRIMARY KEY,
    form_key      VARCHAR(100) NOT NULL,
    field_key     VARCHAR(100) NOT NULL,
    option_value  TEXT NOT NULL,
    campaign_id   BIGINT REFERENCES campaign(id),
    channel       VARCHAR(10) NOT NULL,
    identity_type VARCHAR(10) NOT NULL,
    identity_key  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_survey_vote_channel CHECK (channel IN ('EMAIL', 'WEB')),
    CONSTRAINT ck_survey_vote_identity CHECK (identity_type IN ('RECIPIENT', 'READER', 'ANON'))
);
-- 具名一人一票（跨期同問卷仍一票，後投 upsert 覆蓋）；匿名不受限
CREATE UNIQUE INDEX uq_survey_vote_identity
    ON survey_vote (form_key, identity_type, identity_key)
    WHERE identity_type <> 'ANON';
CREATE INDEX idx_survey_vote_form ON survey_vote (form_key, campaign_id);

-- 信中一鍵題指定（必須是該版本的 select 單選欄位，應用層驗證）
ALTER TABLE form_definition ADD COLUMN email_vote_field_key TEXT;

-- 填答發點：每人每問卷一次（partial unique 防併發重發，比照 uq_credit_txn_promo_refund）
ALTER TABLE credit_txn ADD COLUMN survey_form_key VARCHAR(100);
CREATE UNIQUE INDEX uq_credit_txn_survey_reward
    ON credit_txn (reader_id, survey_form_key)
    WHERE reason = 'SURVEY_REWARD';
