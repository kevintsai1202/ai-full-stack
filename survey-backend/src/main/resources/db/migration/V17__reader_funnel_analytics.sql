-- Reader 第一方匿名漏斗：只保存隨機訪客識別碼與頁面／文章脈絡，不保存 Email、IP 或 User-Agent。
CREATE TABLE reader_funnel_event (
    id           BIGSERIAL   PRIMARY KEY,
    visitor_key  VARCHAR(64) NOT NULL,
    event_name   VARCHAR(40) NOT NULL,
    page_path    VARCHAR(240),
    article_slug VARCHAR(80),
    event_day    DATE        NOT NULL DEFAULT CURRENT_DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reader_funnel_event_name CHECK (event_name IN (
        'READER_PAGE_VIEW', 'ARTICLE_VIEW', 'SUBSCRIPTION_HOME_VIEW',
        'SUBSCRIPTION_CTA_CLICK', 'SUBSCRIBE_ATTEMPT', 'SUBSCRIBE_SUCCESS',
        'SUBSCRIBE_ERROR', 'UNLOCK_CLICK', 'UNLOCK_SUCCESS',
        'UNLOCK_INSUFFICIENT', 'UNLOCK_ERROR'
    )),
    CONSTRAINT uq_reader_funnel_daily UNIQUE NULLS NOT DISTINCT
        (visitor_key, event_name, article_slug, event_day)
);

CREATE INDEX idx_reader_funnel_event_time
    ON reader_funnel_event (event_name, created_at DESC);
CREATE INDEX idx_reader_funnel_article
    ON reader_funnel_event (article_slug, event_name, created_at DESC);
