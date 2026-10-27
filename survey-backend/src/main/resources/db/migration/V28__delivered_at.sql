-- SES 送達事件：記錄收件端郵件伺服器收下信件的時間（第一次送達）。
-- 刻意不新增狀態值：email_log.status='sent'、campaign_recipient.status='SENT' 被邀請信去重、優惠券去重、
-- 群發統計與讀者收信數等流程使用，送達另以獨立欄位記錄，既有判斷完全不受影響。
ALTER TABLE email_log ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE campaign_recipient ADD COLUMN delivered_at TIMESTAMPTZ;

-- 退信／送達事件以 SES MessageId 反查佇列參照（ses-mail-spring-boot-starter 0.3.0）
CREATE INDEX IF NOT EXISTS idx_scheduled_mail_message_id ON scheduled_mail (provider_message_id);
