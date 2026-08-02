-- 付費牆標記的產品語意統一為「需點數解鎖」：既有 BASIC 付費牆升級為 PREMIUM。
-- 解鎖點數優先沿用目前全域設定；設定不存在或不是正整數時安全退回 10 點。
WITH premium_cost AS (
    SELECT COALESCE(
        (SELECT CASE
            WHEN value ~ '^[0-9]{1,5}$' AND value::INT BETWEEN 1 AND 10000 THEN value::INT
            ELSE NULL
         END
           FROM app_setting
          WHERE setting_key = 'credit.premium_cost'),
        10) AS cost
)
UPDATE campaign AS c
   SET tier = 'PREMIUM',
       credit_cost = premium_cost.cost
  FROM premium_cost
 WHERE c.tier = 'BASIC'
   AND c.markdown ~ E'(^|\\r?\\n)[ \\t]*<!--paywall-->[ \\t]*(\\r?\\n|$)';

-- 資料庫層最後一道防線：未來不論從 UI、API 或人工 SQL 寫入，BASIC 都不得帶付費牆。
ALTER TABLE campaign ADD CONSTRAINT ck_campaign_paywall_requires_premium
  CHECK (tier = 'PREMIUM'
      OR markdown !~ E'(^|\\r?\\n)[ \\t]*<!--paywall-->[ \\t]*(\\r?\\n|$)');
