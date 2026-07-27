-- Magic Link 公開端點的來源 IP 與全域節流紀錄。
-- 只保存 SHA-256 雜湊，不保存原始 IP；保留時間由 LoginAbuseGuard 控制。
CREATE TABLE login_request_attempt (
    id         BIGSERIAL   PRIMARY KEY,
    ip_hash    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_request_attempt_ip_time
    ON login_request_attempt (ip_hash, created_at DESC);
CREATE INDEX idx_login_request_attempt_time
    ON login_request_attempt (created_at DESC);
