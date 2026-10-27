package world.springai.survey.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Clock;
import java.time.Duration;

/**
 * Amazon SES 相關 bean，只在 {@code app.mail.provider=ses} 時建立。
 *
 * <p>預設（zsend）時這裡的 bean 一個都不存在：派送器不會排程、不需要 AWS 金鑰，
 * 既有環境與所有 Spring 測試的行為不變。</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "ses")
public class SesMailConfiguration {

    /**
     * SES v2 客戶端：沿用媒體儲存的 URLConnection client 與逾時設定。
     * 啟用時 fail-fast 檢查金鑰，錯誤訊息不含任何秘密值。
     */
    @Bean
    SesV2Client sesV2Client(@Value("${app.mail.ses.region:ap-northeast-1}") String region,
                            @Value("${app.mail.ses.access-key-id:}") String accessKeyId,
                            @Value("${app.mail.ses.secret-access-key:}") String secretAccessKey) {
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(secretAccessKey)) {
            throw new IllegalStateException(
                "MAIL_PROVIDER=ses，但 AWS_SES_ACCESS_KEY_ID／AWS_SES_SECRET_ACCESS_KEY 未完整設定");
        }
        return SesV2Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .httpClientBuilder(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(20)))
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(Duration.ofSeconds(25))
                .apiCallTimeout(Duration.ofSeconds(30))
                .build())
            .build();
    }

    /** SES 寄信實作（MailConfig 取用後再包 QuotaAwareMailSender） */
    @Bean
    SesMailSender sesMailSender(SesV2Client client, ScheduledMailRepository queue,
                                @Value("${app.mail.from}") String from,
                                @Value("${app.mail.reply-to:}") String replyTo,
                                @Value("${app.mail.ses.configuration-set:}") String configurationSet) {
        return new SesMailSender(client, queue, from, replyTo, configurationSet, Clock.systemUTC());
    }

    /** 待寄佇列派送器（@Scheduled 方法由既有的 @EnableScheduling 啟用） */
    @Bean
    ScheduledMailDispatcher scheduledMailDispatcher(ScheduledMailRepository queue,
                                                    EmailLogRepository emailLogs,
                                                    SesMailSender sender,
                                                    @Value("${app.mail.ses.max-send-rate:5}") double maxSendRate) {
        return new ScheduledMailDispatcher(queue, emailLogs, sender, maxSendRate,
            Clock.systemUTC(), Thread::sleep);
    }

    /** SES 額度探測器，供 MailQuotaService 使用 */
    @Bean
    SesQuotaProbe sesQuotaProbe(SesV2Client client, ScheduledMailRepository queue) {
        return new SesQuotaProbe(client, queue);
    }
}
