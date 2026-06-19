-- 問卷回應表：儲存 landing page 收集到的問卷與名單
CREATE TABLE survey_response (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT        NOT NULL,
    name          TEXT,
    role          TEXT,
    experience    TEXT,
    interest      JSONB,
    budget        TEXT,
    utm           JSONB,
    consent       BOOLEAN     NOT NULL,
    unsubscribed  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_survey_response_email ON survey_response (email);
