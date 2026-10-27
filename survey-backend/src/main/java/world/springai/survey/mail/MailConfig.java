package world.springai.survey.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 寄信實作選擇（app.mail.provider）：
 * <ul>
 *   <li>{@code zsend}（預設）：有 ZSend 金鑰用 ZSendMailSender，否則 fallback 成 NoopMailSender</li>
 *   <li>{@code ses}：用 {@link SesMailSender}（bean 由 {@link SesMailConfiguration} 建立）</li>
 * </ul>
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * 依 app.mail.provider 與金鑰設定決定實際的寄信實作，
     * 並一律以 {@link QuotaAwareMailSender} 包一層。
     *
     * <p>包裝的理由見 {@link QuotaAwareMailSender} 的類別註解：額度快取的失效
     * 必須綁在「寄信成功」這個事實上，而不是綁在「呼叫端記得多寫一行」。
     * Noop 實作也一併包——本機／測試環境的行為要與線上一致，
     * 否則「忘了讓快取失效」這類問題只會在線上才浮現。</p>
     *
     * <p>provider 打錯字（不是 zsend 也不是 ses）直接啟動失敗：靜默退回 ZSend
     * 會讓人以為已經換到 SES，實際上還卡在每天 100 封。</p>
     */
    @Bean
    public MailSender mailSender(RestClient.Builder builder,
                                 MailQuotaService mailQuotaService,
                                 @Value("${app.mail.provider:zsend}") String provider,
                                 @Value("${app.mail.api-key:}") String apiKey,
                                 @Value("${app.mail.from}") String from,
                                 @Value("${app.mail.reply-to:}") String replyTo,
                                 ObjectProvider<SesMailSender> sesMailSender) {
        MailSender delegate;
        String normalized = provider == null ? "zsend" : provider.trim();
        if (MailQuotaService.PROVIDER_SES.equalsIgnoreCase(normalized)) {
            delegate = sesMailSender.getIfAvailable();
            if (delegate == null) {
                throw new IllegalStateException("MAIL_PROVIDER=ses，但 SES 寄信實作沒有建立");
            }
            log.info("寄信服務：Amazon SES");
        } else if ("zsend".equalsIgnoreCase(normalized)) {
            if (!StringUtils.hasText(apiKey)) {
                log.warn("未設定 app.mail.api-key，啟用 NoopMailSender（不會真正寄信）");
                delegate = new NoopMailSender();
            } else {
                delegate = new ZSendMailSender(builder, apiKey, from, replyTo);
            }
        } else {
            throw new IllegalStateException("MAIL_PROVIDER 只接受 zsend 或 ses，目前是：" + provider);
        }
        return new QuotaAwareMailSender(delegate, mailQuotaService);
    }
}
