-- V22__coupon_campaign.sql
-- 課程優惠券寄送系統：優惠券活動主表。
-- 設計依據 docs/superpowers/specs/2026-08-04-coupon-campaign-design.md §4

CREATE TABLE coupon_campaign (
    id            BIGSERIAL PRIMARY KEY,
    course_name   VARCHAR(150) NOT NULL,
    pitch         TEXT NOT NULL,
    course_url    VARCHAR(1000) NOT NULL,
    coupon_code   VARCHAR(100) NOT NULL,
    expires_at    DATE,
    form_key      VARCHAR(100) NOT NULL,
    answer_filter JSONB NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at       TIMESTAMPTZ,
    sent_count    INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_coupon_status CHECK (status IN ('DRAFT', 'SENT')),
    CONSTRAINT ck_coupon_course_url CHECK (course_url LIKE 'https://%')
);
