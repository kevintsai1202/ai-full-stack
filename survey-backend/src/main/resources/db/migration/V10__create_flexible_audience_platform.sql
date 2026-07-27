-- 彈性受眾資料底座：人物、同意、來源身分、活動、Fact、表單 schema 與匯入稽核。
-- 舊 survey_response 仍保留作為相容層；本 migration 只讀回填，不刪除或改寫既有列。

CREATE TABLE audience_person (
    id               BIGSERIAL PRIMARY KEY,
    email            TEXT        NOT NULL,
    email_normalized TEXT        NOT NULL,
    display_name     TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audience_person_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_audience_person_email_normalized
        CHECK (email_normalized = lower(btrim(email_normalized)) AND email_normalized <> '')
);

CREATE TABLE audience_consent (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT      NOT NULL REFERENCES audience_person(id) ON DELETE CASCADE,
    channel         TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    source_key      TEXT        NOT NULL,
    consent_version TEXT,
    evidence        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audience_consent_status CHECK (status IN ('PENDING', 'CONFIRMED', 'UNSUBSCRIBED'))
);
CREATE INDEX idx_audience_consent_person_channel_time
    ON audience_consent (person_id, channel, occurred_at DESC, id DESC);

CREATE TABLE audience_identity (
    id            BIGSERIAL PRIMARY KEY,
    person_id     BIGINT      NOT NULL REFERENCES audience_person(id) ON DELETE CASCADE,
    source_key    TEXT        NOT NULL,
    external_type TEXT        NOT NULL,
    external_id   TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audience_identity_external UNIQUE (source_key, external_type, external_id)
);
CREATE INDEX idx_audience_identity_person ON audience_identity (person_id);

CREATE TABLE audience_record (
    id                 BIGSERIAL PRIMARY KEY,
    person_id          BIGINT      NOT NULL REFERENCES audience_person(id) ON DELETE CASCADE,
    source_key         TEXT        NOT NULL,
    record_type        TEXT        NOT NULL,
    schema_key         TEXT,
    external_record_id TEXT        NOT NULL,
    occurred_at        TIMESTAMPTZ NOT NULL,
    raw_data           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    summary_data       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    payload_hash       TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audience_record_external
        UNIQUE (source_key, record_type, external_record_id)
);
CREATE INDEX idx_audience_record_person_time
    ON audience_record (person_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audience_record_schema ON audience_record (schema_key, occurred_at DESC);
CREATE INDEX idx_audience_record_raw_gin ON audience_record USING GIN (raw_data);

CREATE TABLE audience_fact (
    id             BIGSERIAL PRIMARY KEY,
    person_id      BIGINT      NOT NULL REFERENCES audience_person(id) ON DELETE CASCADE,
    record_id      BIGINT REFERENCES audience_record(id) ON DELETE CASCADE,
    fact_key       TEXT        NOT NULL,
    value_text     TEXT,
    value_number   NUMERIC,
    value_boolean  BOOLEAN,
    value_time     TIMESTAMPTZ,
    source_key     TEXT        NOT NULL,
    observed_at    TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audience_fact_has_value CHECK (
        value_text IS NOT NULL OR value_number IS NOT NULL
        OR value_boolean IS NOT NULL OR value_time IS NOT NULL
    )
);
CREATE INDEX idx_audience_fact_key_text ON audience_fact (fact_key, value_text, person_id);
CREATE INDEX idx_audience_fact_key_number ON audience_fact (fact_key, value_number, person_id);
CREATE INDEX idx_audience_fact_person_time ON audience_fact (person_id, observed_at DESC);

CREATE TABLE form_definition (
    id                       BIGSERIAL PRIMARY KEY,
    form_key                 TEXT        NOT NULL,
    version                  INTEGER     NOT NULL,
    title                    TEXT        NOT NULL,
    status                   TEXT        NOT NULL DEFAULT 'DRAFT',
    public_analytics_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_form_definition_key_version UNIQUE (form_key, version),
    CONSTRAINT ck_form_definition_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE form_field (
    id                  BIGSERIAL PRIMARY KEY,
    form_definition_id  BIGINT  NOT NULL REFERENCES form_definition(id) ON DELETE CASCADE,
    field_key           TEXT    NOT NULL,
    label               TEXT    NOT NULL,
    field_type          TEXT    NOT NULL,
    required            BOOLEAN NOT NULL DEFAULT FALSE,
    options             JSONB   NOT NULL DEFAULT '[]'::jsonb,
    analytics_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    analytics_view      TEXT,
    filterable          BOOLEAN NOT NULL DEFAULT TRUE,
    sensitive           BOOLEAN NOT NULL DEFAULT FALSE,
    public_analytics    BOOLEAN NOT NULL DEFAULT FALSE,
    display_order       INTEGER NOT NULL DEFAULT 0,
    fact_key            TEXT,
    CONSTRAINT uq_form_field_key UNIQUE (form_definition_id, field_key),
    CONSTRAINT ck_form_field_type CHECK (
        field_type IN ('select', 'multi_select', 'boolean', 'rating', 'number',
                       'short_text', 'long_text', 'date', 'email')
    )
);
CREATE INDEX idx_form_field_definition_order
    ON form_field (form_definition_id, display_order, id);

CREATE TABLE import_profile (
    id          BIGSERIAL PRIMARY KEY,
    profile_key TEXT        NOT NULL UNIQUE,
    label       TEXT        NOT NULL,
    source_key  TEXT        NOT NULL,
    import_type TEXT        NOT NULL,
    mapping     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE import_batch (
    id              BIGSERIAL PRIMARY KEY,
    source_key      TEXT        NOT NULL,
    import_type     TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    cursor_value    TEXT,
    people_created  INTEGER     NOT NULL DEFAULT 0,
    people_merged   INTEGER     NOT NULL DEFAULT 0,
    records_created INTEGER     NOT NULL DEFAULT 0,
    records_updated INTEGER     NOT NULL DEFAULT 0,
    unchanged_count INTEGER     NOT NULL DEFAULT 0,
    invalid_count   INTEGER     NOT NULL DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT ck_import_batch_status CHECK (status IN ('PREVIEW', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE import_item (
    id                 BIGSERIAL PRIMARY KEY,
    batch_id           BIGINT      NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE,
    external_record_id TEXT,
    person_id          BIGINT REFERENCES audience_person(id) ON DELETE SET NULL,
    status             TEXT        NOT NULL,
    error_code         TEXT,
    error_message      TEXT,
    payload_hash       TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_item_status CHECK (
        status IN ('CREATED', 'MERGED', 'UPDATED', 'UNCHANGED', 'INVALID', 'FAILED')
    )
);
CREATE INDEX idx_import_item_batch_status ON import_item (batch_id, status);

-- 批次選取與操作使用固定快照；Execute 不重新解讀篩選條件。
CREATE TABLE audience_selection_snapshot (
    id           UUID PRIMARY KEY,
    action       TEXT        NOT NULL,
    request_data JSONB       NOT NULL,
    targeted     INTEGER     NOT NULL,
    eligible     INTEGER     NOT NULL,
    skipped      INTEGER     NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audience_selection_target (
    snapshot_id UUID   NOT NULL REFERENCES audience_selection_snapshot(id) ON DELETE CASCADE,
    person_id   BIGINT NOT NULL REFERENCES audience_person(id) ON DELETE CASCADE,
    eligibility TEXT   NOT NULL,
    reason      TEXT,
    PRIMARY KEY (snapshot_id, person_id)
);

CREATE TABLE audience_bulk_operation (
    id              UUID PRIMARY KEY,
    snapshot_id     UUID        NOT NULL REFERENCES audience_selection_snapshot(id),
    idempotency_key TEXT        NOT NULL UNIQUE,
    action          TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    targeted        INTEGER     NOT NULL,
    succeeded       INTEGER     NOT NULL DEFAULT 0,
    failed          INTEGER     NOT NULL DEFAULT 0,
    skipped         INTEGER     NOT NULL DEFAULT 0,
    result_data     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE TABLE audience_segment (
    id          BIGSERIAL PRIMARY KEY,
    segment_key TEXT        NOT NULL UNIQUE,
    label       TEXT        NOT NULL,
    filters     JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE integration_sync_cursor (
    source_key   TEXT PRIMARY KEY,
    cursor_value TEXT,
    last_status  TEXT,
    last_batch_id BIGINT REFERENCES import_batch(id) ON DELETE SET NULL,
    last_synced_at TIMESTAMPTZ
);

CREATE TABLE audience_data_request (
    id           UUID PRIMARY KEY,
    person_id    BIGINT REFERENCES audience_person(id) ON DELETE SET NULL,
    email_normalized TEXT NOT NULL,
    request_type TEXT NOT NULL,
    status       TEXT NOT NULL,
    result_data  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

-- 退訂後要求刪除個資時仍保留不可逆 Email hash，避免後續匯入又把該人加入行銷名單。
CREATE TABLE audience_suppression (
    email_hash TEXT PRIMARY KEY,
    reason     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE campaign ADD COLUMN filter_json JSONB;
ALTER TABLE campaign ADD COLUMN saved_segment_id BIGINT REFERENCES audience_segment(id) ON DELETE SET NULL;

-- 既有問卷 schema：公開統計只開放目前原本就公開的 role、interest、status。
INSERT INTO form_definition (
    form_key, version, title, status, public_analytics_enabled
) VALUES (
    'fullstack-course-interest', 1, 'AI 全端課程興趣問卷', 'PUBLISHED', TRUE
);

INSERT INTO form_field (
    form_definition_id, field_key, label, field_type, required,
    analytics_enabled, analytics_view, filterable, sensitive,
    public_analytics, display_order, fact_key
)
SELECT id, field_key, label, field_type, required, analytics_enabled,
       analytics_view, filterable, sensitive, public_analytics, display_order, fact_key
FROM form_definition
CROSS JOIN (VALUES
    ('role',                '身分／職業',       'select',       FALSE, TRUE, 'bar',  TRUE,  FALSE, TRUE,  10, 'profile.role'),
    ('experience',          '後端開發經驗',     'select',       FALSE, TRUE, 'bar',  TRUE,  FALSE, FALSE, 20, 'profile.backend_experience'),
    ('frontendExperience',  '前端開發經驗',     'select',       FALSE, TRUE, 'bar',  TRUE,  FALSE, FALSE, 30, 'profile.frontend_experience'),
    ('interest',            '想學主題',         'multi_select', FALSE, TRUE, 'bar',  TRUE,  FALSE, TRUE,  40, 'profile.interest'),
    ('budget',              '學習預算',         'select',       FALSE, TRUE, 'bar',  TRUE,  FALSE, FALSE, 50, 'profile.budget'),
    ('status',              '目前狀態',         'select',       FALSE, TRUE, 'bar',  TRUE,  FALSE, TRUE,  60, 'survey.status'),
    ('goals',               '學習目標',         'long_text',    FALSE, TRUE, 'list', TRUE,  FALSE, FALSE, 70, 'survey.goals'),
    ('pain_points',         '目前痛點',         'multi_select', FALSE, TRUE, 'bar',  TRUE,  FALSE, FALSE, 80, 'survey.pain_points')
) AS seed(
    field_key, label, field_type, required, analytics_enabled,
    analytics_view, filterable, sensitive, public_analytics, display_order, fact_key
)
WHERE form_key = 'fullstack-course-interest' AND version = 1;

-- 每個正規化 Email 只建立一個人物，名稱採最新一筆非空值。
INSERT INTO audience_person (
    email, email_normalized, display_name, created_at, updated_at
)
SELECT
    (array_agg(btrim(email) ORDER BY created_at DESC, id DESC))[1],
    lower(btrim(email)),
    (array_remove(array_agg(NULLIF(btrim(name), '') ORDER BY created_at DESC, id DESC), NULL))[1],
    min(created_at),
    max(created_at)
FROM survey_response
WHERE btrim(email) <> ''
GROUP BY lower(btrim(email));

-- 同意狀態以「退訂優先、其次已確認、否則待確認」回填；任何匯入不得覆蓋退訂。
INSERT INTO audience_consent (
    person_id, channel, status, source_key, consent_version, evidence, occurred_at
)
SELECT
    p.id,
    'EMAIL',
    CASE
        WHEN bool_or(s.unsubscribed) THEN 'UNSUBSCRIBED'
        WHEN bool_or(s.consent) THEN 'CONFIRMED'
        ELSE 'PENDING'
    END,
    'legacy-backfill',
    NULL,
    jsonb_build_object('surveyResponseIds', jsonb_agg(s.id ORDER BY s.id)),
    max(COALESCE(s.last_engaged_at, s.created_at))
FROM audience_person p
JOIN survey_response s ON lower(btrim(s.email)) = p.email_normalized
GROUP BY p.id;

-- 各來源人物身分以正規化 Email 作外部鍵，提供來源徽章與可追溯關係。
INSERT INTO audience_identity (
    person_id, source_key, external_type, external_id, created_at, updated_at
)
SELECT
    p.id,
    s.source,
    'email',
    p.email_normalized,
    min(s.created_at),
    max(s.created_at)
FROM audience_person p
JOIN survey_response s ON lower(btrim(s.email)) = p.email_normalized
GROUP BY p.id, p.email_normalized, s.source;

-- 每一筆舊 survey_response 都保留成獨立活動，不因 Email 合併而遺失歷史問卷。
INSERT INTO audience_record (
    person_id, source_key, record_type, schema_key, external_record_id,
    occurred_at, raw_data, summary_data, payload_hash, created_at, updated_at
)
SELECT
    p.id,
    s.source,
    'survey_submission',
    'fullstack-course-interest@1',
    'survey_response:' || s.id,
    s.created_at,
    jsonb_build_object(
        'legacySurveyResponseId', s.id,
        'answers', COALESCE(s.answers, '{}'::jsonb)
            || jsonb_strip_nulls(jsonb_build_object(
                'role', s.role,
                'experience', s.experience,
                'frontendExperience', s.frontend_experience,
                'interest', s.interest,
                'budget', s.budget
            )),
        'utm', COALESCE(s.utm, '{}'::jsonb)
    ),
    jsonb_strip_nulls(jsonb_build_object(
        'formTitle', 'AI 全端課程興趣問卷',
        'name', s.name
    )),
    md5(to_jsonb(s)::text),
    s.created_at,
    s.created_at
FROM survey_response s
JOIN audience_person p ON p.email_normalized = lower(btrim(s.email));

-- 固定舊欄位轉為可搜尋 Fact。
INSERT INTO audience_fact (
    person_id, record_id, fact_key, value_text, source_key, observed_at
)
SELECT r.person_id, r.id, facts.fact_key, facts.fact_value, r.source_key, r.occurred_at
FROM audience_record r
JOIN survey_response s ON r.external_record_id = 'survey_response:' || s.id
CROSS JOIN LATERAL (VALUES
    ('profile.role', NULLIF(s.role, '')),
    ('profile.backend_experience', NULLIF(s.experience, '')),
    ('profile.frontend_experience', NULLIF(s.frontend_experience, '')),
    ('profile.budget', NULLIF(s.budget, ''))
) AS facts(fact_key, fact_value)
WHERE facts.fact_value IS NOT NULL;

-- 舊複選主題拆成多筆 Fact，供分眾與 facet 使用。
INSERT INTO audience_fact (
    person_id, record_id, fact_key, value_text, source_key, observed_at
)
SELECT r.person_id, r.id, 'profile.interest', value, r.source_key, r.occurred_at
FROM audience_record r
JOIN survey_response s ON r.external_record_id = 'survey_response:' || s.id
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(s.interest, '[]'::jsonb)) AS value;

-- answers 內的一般題目自動成為 Fact；底線開頭為系統欄位，不進分眾。
INSERT INTO audience_fact (
    person_id, record_id, fact_key, value_text, source_key, observed_at
)
SELECT
    r.person_id,
    r.id,
    'survey.' || answer.key,
    CASE
        WHEN jsonb_typeof(answer.value) = 'string' THEN answer.value #>> '{}'
        ELSE answer.value::text
    END,
    r.source_key,
    r.occurred_at
FROM audience_record r
JOIN survey_response s ON r.external_record_id = 'survey_response:' || s.id
CROSS JOIN LATERAL jsonb_each(COALESCE(s.answers, '{}'::jsonb)) AS answer
WHERE answer.key NOT LIKE '\_%' ESCAPE '\'
  AND answer.value <> 'null'::jsonb;
