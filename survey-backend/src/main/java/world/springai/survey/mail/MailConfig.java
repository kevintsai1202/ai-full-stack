package world.springai.survey.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 寄信實作選擇：有 ZSend 金鑰用 ZSendMailSender，否則 fallback 成 NoopMailSender */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * 依 app.mail.api-key 是否設定，決定實際的寄信實作，
     * 並一律以 {@link QuotaAwareMailSender} 包一層。
     *
     * <p>包裝的理由見 {@link QuotaAwareMailSender} 的類別註解：額度快取的失效
     * 必須綁在「寄信成功」這個事實上，而不是綁在「呼叫端記得多寫一行」。
     * Noop 實作也一併包——本機／測試環境的行為要與線上一致，
     * 否則「忘了讓快取失效」這類問題只會在線上才浮現。</p>
     */
    @Bean
    public MailSender mailSender(RestClient.Builder builder,
                                 MailQuotaService mailQuotaService,
                                 @Value("${app.mail.api-key:}") String apiKey,
                                 @Value("${app.mail.from}") String from,
                                 @Value("${app.mail.reply-to:}") String replyTo) {
        MailSender delegate;
        if (!StringUtils.hasText(apiKey)) {
            log.warn("未設定 app.mail.api-key，啟用 NoopMailSender（不會真正寄信）");
            delegate = new NoopMailSender();
        } else {
            delegate = new ZSendMailSender(builder, apiKey, from, replyTo);
        }
        return new QuotaAwareMailSender(delegate, mailQuotaService);
    }
}
