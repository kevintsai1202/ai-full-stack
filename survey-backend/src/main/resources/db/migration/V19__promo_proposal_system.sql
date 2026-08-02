-- V19__promo_proposal_system.sql
-- 工商時間提案系統：提案、版位（電子報×提案關聯）、點擊紀錄，及帳本擴充。
-- 設計依據 docs/superpowers/specs/2026-08-02-promo-proposal-system-design.md §3

-- 工商提案：讀者提交、管理員審核；unit_cost 是申請當下單價快照（退點以此計算）
CREATE TABLE promo_proposal (
    id              BIGSERIAL PRIMARY KEY,
    reader_id       BIGINT NOT NULL REFERENCES reader(id),
    contact_name    VARCHAR(100) NOT NULL,
    contact_email   VARCHAR(255) NOT NULL,
    title           VARCHAR(150) NOT NULL,
    body_text       TEXT NOT NULL,
    link_text       VARCHAR(100) NOT NULL,
    link_url        VARCHAR(1000) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_note     TEXT,
    reviewed_at     TIMESTAMPTZ,
    placement_quota INT NOT NULL,
    placement_used  INT NOT NULL DEFAULT 0,
    unit_cost       INT NOT NULL,
    pricing_type    VARCHAR(20) NOT NULL DEFAULT 'FREE',
    payment_status  VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_promo_quota CHECK (placement_quota BETWEEN 1 AND 3),
    CONSTRAINT ck_promo_used CHECK (placement_used >= 0 AND placement_used <= placement_quota),
    CONSTRAINT ck_promo_link_https CHECK (link_url LIKE 'https://%')
);
CREATE INDEX idx_promo_proposal_reader ON promo_proposal(reader_id);
CREATE INDEX idx_promo_proposal_status ON promo_proposal(status);

-- 版位：campaign_id 建立時為 NULL（編輯器插入時 Campaign 列尚不存在），對帳時綁定
CREATE TABLE promo_placement (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT REFERENCES campaign(id),
    proposal_id  BIGINT NOT NULL REFERENCES promo_proposal(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    committed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 同一期同提案最多一個版位；未綁定的 DRAFT 不受限
CREATE UNIQUE INDEX uq_promo_placement_campaign_proposal
    ON promo_placement(campaign_id, proposal_id) WHERE campaign_id IS NOT NULL;
CREATE INDEX idx_promo_placement_proposal ON promo_placement(proposal_id);

-- 點擊紀錄：append-only，彙總於查詢時計算
CREATE TABLE promo_click (
    id            BIGSERIAL PRIMARY KEY,
    placement_id  BIGINT NOT NULL REFERENCES promo_placement(id),
    channel       VARCHAR(10) NOT NULL,
    identity_type VARCHAR(10) NOT NULL,
    identity_key  VARCHAR(255),
    clicked_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_promo_click_placement ON promo_click(placement_id);

-- 帳本擴充：退點冪等判斷需要「這筆退點屬於哪個提案」
ALTER TABLE credit_txn ADD COLUMN promo_proposal_id BIGINT REFERENCES promo_proposal(id);
