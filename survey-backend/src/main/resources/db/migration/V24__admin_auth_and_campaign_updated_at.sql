-- admin 登入用途隔離：既有資料一律視為讀者登入
ALTER TABLE login_token ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'reader';

-- 文章內容最後修改時間（僅記錄時間，不做修改歷史）
ALTER TABLE campaign ADD COLUMN updated_at TIMESTAMPTZ;
