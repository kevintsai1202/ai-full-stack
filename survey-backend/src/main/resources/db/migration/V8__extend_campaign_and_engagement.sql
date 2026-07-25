-- 擴充 campaign 為「可在網頁上閱讀的文章」，並為名單加上參與度時間戳。
-- 全部 additive：新欄位皆可為 NULL 或帶 DEFAULT，既有列無需改寫即維持有效。

-- 內容分級與網頁閱讀所需欄位
ALTER TABLE campaign ADD COLUMN tier             TEXT    NOT NULL DEFAULT 'BASIC'; -- BASIC / PREMIUM
ALTER TABLE campaign ADD COLUMN credit_cost      INT     NOT NULL DEFAULT 0;       -- PREMIUM 解鎖所需點數
ALTER TABLE campaign ADD COLUMN slug             TEXT;                             -- 網頁網址片段
ALTER TABLE campaign ADD COLUMN published_at     TIMESTAMPTZ;                      -- 非 NULL 才出現在 archive
ALTER TABLE campaign ADD COLUMN vip_full_in_mail BOOLEAN NOT NULL DEFAULT FALSE;   -- VIP 是否在信件收全文（階段 D 才使用）
ALTER TABLE campaign ADD COLUMN filter_levels    TEXT    NOT NULL DEFAULT 'active';-- 寄送的參與度級別（階段 F 才使用）

-- slug 唯一（允許多筆 NULL：尚未設定 slug 的舊 campaign 不進 archive）
CREATE UNIQUE INDEX uq_campaign_slug ON campaign (slug) WHERE slug IS NOT NULL;

-- 防呆：標為 PREMIUM 卻沒有解鎖成本，等同免費卻顯示為付費內容
ALTER TABLE campaign ADD CONSTRAINT ck_campaign_premium_cost
  CHECK (tier <> 'PREMIUM' OR credit_cost > 0);

-- 參與度時間戳。放在名單中心而非 reader，因為從未登入過的殭屍地址沒有 reader 列，
-- 但正是最需要被 sunset 判定的對象（階段 F）。
ALTER TABLE survey_response ADD COLUMN last_engaged_at TIMESTAMPTZ;
CREATE INDEX idx_survey_response_engaged ON survey_response (last_engaged_at);

-- 【必要 backfill】既有訂閱者在此之前沒有參與度追蹤，last_engaged_at 全為 NULL。
-- 若不回填，他們的「已寄期數」可能早已超過階段 F 的淘汰門檻（12 期），
-- 依分級規則會被判為 sunset —— 功能上線當天所有老訂閱者整批停收電子報。
-- 資料沒少但收不到信，且要到下次發送才顯現，極難察覺。
-- 以 migration 執行時間作為起算點，讓既有訂閱者一律從 active 開始。
UPDATE survey_response
   SET last_engaged_at = now()
 WHERE consent = TRUE AND unsubscribed = FALSE;

-- 未確認訂閱者（如已匯入的 exam 名單）刻意不回填，保持 NULL：
-- 他們從未被寄過電子報（邀請信 type='invite' 不計入已寄期數），
-- 「已寄期數 < 沉睡門檻」條件成立，仍會被判為 active。回填反而造出假的參與紀錄。

-- 參數初始值（spec §9.2）。ON CONFLICT 讓此 migration 可安全重跑。
INSERT INTO app_setting (setting_key, value) VALUES
  ('credit.signup_grant',        '300'),
  ('credit.premium_cost',        '10'),
  ('credit.referral_reward',     '100'),
  ('vip.default_days',           '365'),
  ('engagement.dormant_after_campaigns', '6'),
  ('engagement.sunset_after_campaigns',  '12'),
  ('engagement.active_days',     '90'),
  ('engagement.sunset_days',     '365')
ON CONFLICT (setting_key) DO NOTHING;
