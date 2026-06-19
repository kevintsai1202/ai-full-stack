package world.springai.survey;

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

    /** 依 app.mail.api-key 是否設定，決定注入哪個 MailSender */
    @Bean
    public MailSender mailSender(RestClient.Builder builder,
                                 @Value("${app.mail.api-key:}") String apiKey,
                                 @Value("${app.mail.from}") String from,
                                 @Value("${app.mail.reply-to:}") String replyTo) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("未設定 app.mail.api-key，啟用 NoopMailSender（不會真正寄信）");
            return new NoopMailSender();
        }
        return new ZSendMailSender(builder, apiKey, from, replyTo);
    }
}
