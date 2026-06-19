-- 寄送記錄：每次寄信（含失敗）寫一筆，供稽核與後續投遞狀態追蹤
CREATE TABLE email_log (
    id                  BIGSERIAL PRIMARY KEY,
    recipient           TEXT        NOT NULL,              -- 收件人 email
    subject             TEXT,                              -- 主旨
    type                TEXT,                              -- 信件類型（本段固定 welcome）
    provider_message_id TEXT,                              -- ZSend 回傳 id（no-op 時為 noop）
    status              TEXT        NOT NULL,              -- sent / failed
    error               TEXT,                              -- 失敗時錯誤摘要
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now() -- 寄送時間
);

-- 依收件人查寄送歷程
CREATE INDEX idx_email_log_recipient ON email_log (recipient);
