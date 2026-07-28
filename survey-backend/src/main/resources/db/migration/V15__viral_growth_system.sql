-- 病毒成長系統：漏斗、轉換、里程碑、活動與人工審核。
-- 全部為 additive migration；不刪除、不改寫既有訂閱資料。

-- 前版設計已要求下一支允許 migration 時補上樂觀鎖，避免日後新增整列 save()
-- 時靜默覆蓋 consent / unsubscribed。預設 0 不改變任何既有列的業務語意。
ALTER TABLE survey_response ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE referral_click (
    id              BIGSERIAL   PRIMARY KEY,
    referrer_id     BIGINT      NOT NULL REFERENCES reader(id),
    referral_code   TEXT        NOT NULL,
    source_slug     TEXT,
    visitor_key     TEXT        NOT NULL,
    click_day       DATE        NOT NULL DEFAULT CURRENT_DATE,
    clicked_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_referral_click_daily UNIQUE NULLS NOT DISTINCT
        (referral_code, source_slug, visitor_key, click_day)
);
CREATE INDEX idx_referral_click_time ON referral_click (clicked_at DESC);
CREATE INDEX idx_referral_click_source ON referral_click (source_slug, clicked_at DESC);

CREATE TABLE referral_conversion (
    id                         BIGSERIAL   PRIMARY KEY,
    invitee_email_normalized   TEXT        NOT NULL UNIQUE,
    referrer_id                BIGINT      NOT NULL REFERENCES reader(id),
    referral_code              TEXT        NOT NULL,
    source_slug                TEXT,
    status                     TEXT        NOT NULL DEFAULT 'SUBMITTED',
    risk_score                 INT         NOT NULL DEFAULT 0,
    risk_reasons               TEXT,
    base_reward                INT         NOT NULL DEFAULT 0,
    multiplier                 INT         NOT NULL DEFAULT 1,
    referrer_reward            INT         NOT NULL DEFAULT 0,
    invitee_reward             INT         NOT NULL DEFAULT 0,
    invitee_reward_granted     BOOLEAN     NOT NULL DEFAULT FALSE,
    submitted_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at               TIMESTAMPTZ,
    reviewed_at                TIMESTAMPTZ,
    reviewed_by                TEXT,
    review_note                TEXT,
    CONSTRAINT ck_referral_conversion_status CHECK
        (status IN ('SUBMITTED','PENDING_REVIEW','APPROVED','REJECTED')),
    CONSTRAINT ck_referral_multiplier CHECK (multiplier BETWEEN 1 AND 3)
);
CREATE INDEX idx_referral_conversion_referrer
    ON referral_conversion (referrer_id, confirmed_at DESC);
CREATE INDEX idx_referral_conversion_review
    ON referral_conversion (status, confirmed_at DESC);
CREATE INDEX idx_referral_conversion_source
    ON referral_conversion (source_slug, submitted_at DESC);

CREATE TABLE referral_badge (
    id              BIGSERIAL   PRIMARY KEY,
    reader_id       BIGINT      NOT NULL REFERENCES reader(id),
    milestone       INT         NOT NULL,
    badge_code      TEXT        NOT NULL,
    badge_name      TEXT        NOT NULL,
    bonus_credits   INT         NOT NULL DEFAULT 0,
    awarded_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_referral_badge UNIQUE (reader_id, milestone),
    CONSTRAINT ck_referral_badge_milestone CHECK (milestone IN (3, 5, 10))
);

CREATE TABLE referral_campaign (
    id              BIGSERIAL   PRIMARY KEY,
    name            TEXT        NOT NULL,
    article_slug    TEXT,
    tag_slug        TEXT,
    multiplier      INT         NOT NULL,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_referral_campaign_multiplier CHECK (multiplier BETWEEN 2 AND 3),
    CONSTRAINT ck_referral_campaign_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_referral_campaign_scope CHECK
        (article_slug IS NOT NULL OR tag_slug IS NOT NULL)
);
CREATE INDEX idx_referral_campaign_active
    ON referral_campaign (active, starts_at, ends_at);

-- 雙邊獎勵與里程碑各自有資料庫層冪等鍵。
CREATE UNIQUE INDEX uq_credit_txn_invitee_reward
    ON credit_txn (reader_id, note)
    WHERE reason = 'REFERRAL_INVITEE';
CREATE UNIQUE INDEX uq_credit_txn_milestone
    ON credit_txn (reader_id, note)
    WHERE reason = 'REFERRAL_MILESTONE';
