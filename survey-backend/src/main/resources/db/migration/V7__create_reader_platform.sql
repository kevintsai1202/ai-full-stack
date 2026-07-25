-- 讀者端平台核心資料表。全部為新增，不觸碰任何既有表的既有資料。
-- 注意：email_open（階段 E）與 media_asset（階段 D）刻意不在此建立，
-- 各階段自帶 migration，避免建立當下用不到的表。

-- 可調參數：點數與門檻類參數存 DB，讓後台改完立即生效（不必重新部署）
CREATE TABLE app_setting (
    setting_key TEXT        PRIMARY KEY,
    value       TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 讀者帳戶：以 email 對應名單中心，1:1 但刻意不與 survey_response 合併。
-- survey_response 管「同意與來源」，reader 管「帳戶與點數」。
-- 不變式：reader 存在不代表已確認訂閱，訂閱狀態一律查 survey_response。
CREATE TABLE reader (
    id             BIGSERIAL   PRIMARY KEY,
    email          TEXT        NOT NULL UNIQUE,          -- 一律正規化為小寫
    tier           TEXT        NOT NULL DEFAULT 'FREE',  -- FREE / VIP
    vip_expires_at TIMESTAMPTZ,                          -- NULL 表無限期（僅 tier=VIP 時有意義）
    credits        INT         NOT NULL DEFAULT 0,       -- 目前餘額，為 credit_txn 的物化總和
    referral_code  TEXT        NOT NULL UNIQUE,          -- 個人邀請碼
    referred_by    BIGINT,                               -- 推薦人 reader.id
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reader_referral_code ON reader (referral_code);

-- 點數帳本：只增不改不刪，reader.credits 永遠可由此重算稽核
CREATE TABLE credit_txn (
    id          BIGSERIAL   PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES reader(id),
    delta       INT         NOT NULL,   -- 正數加點、負數扣點
    reason      TEXT        NOT NULL,   -- SIGNUP_GRANT / REFERRAL / READ / ADMIN_GRANT
    campaign_id BIGINT,                 -- reason=READ 時的文章
    note        TEXT,                   -- ADMIN_GRANT 時的說明
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_txn_reader ON credit_txn (reader_id, created_at DESC);

-- 已解鎖文章。UNIQUE 同時是併發防線與「同一篇不重複扣點」的保證
CREATE TABLE article_access (
    id          BIGSERIAL   PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES reader(id),
    campaign_id BIGINT      NOT NULL,
    cost        INT         NOT NULL,   -- 當時實扣點數（0 表 VIP 或 BASIC 免費通行）
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_access UNIQUE (reader_id, campaign_id)
);

-- magic link 一次性登入 token。刻意不用無狀態 HMAC：登入必須可到期、可作廢，
-- 而 UnsubscribeTokenService 的簽章沒有到期概念（那對退訂連結是特性）。
-- 只存 SHA-256 雜湊，明文 token 僅出現在寄出的信裡。
CREATE TABLE login_token (
    id         BIGSERIAL   PRIMARY KEY,
    token_hash TEXT        NOT NULL UNIQUE,
    email      TEXT        NOT NULL,   -- 一律正規化為小寫
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,            -- 非 NULL 即已使用，不可重用
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_token_email ON login_token (email, created_at DESC);
