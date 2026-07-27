package world.springai.survey.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

/** 建立連線 MinIO 的 path-style S3 client。 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaStorageConfiguration {

    /**
     * 只有明確啟用媒體儲存時才建立 S3 client，讓既有本機／測試環境不依賴 MinIO。
     */
    @Bean
    @ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
    S3Client mediaS3Client(MediaProperties properties) {
        validate(properties);
        return S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .checksumValidationEnabled(true)
                .build())
            .httpClientBuilder(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(20)))
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(Duration.ofSeconds(25))
                .apiCallTimeout(Duration.ofSeconds(30))
                .retryPolicy(RetryPolicy.none())
                .build())
            .build();
    }

    /** 啟用時 fail-fast 驗證必要設定，錯誤訊息不包含任何秘密值。 */
    private void validate(MediaProperties properties) {
        requireHttpUrl(properties.getEndpoint(), "MINIO_ENDPOINT");
        requireHttpUrl(properties.getPublicBaseUrl(), "MINIO_PUBLIC_BASE_URL");
        if (properties.getAccessKey() == null || properties.getAccessKey().isBlank()
                || properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("媒體儲存已啟用，但 MinIO 存取金鑰未完整設定");
        }
        if (properties.getBucket() == null
                || !properties.getBucket().matches("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")) {
            throw new IllegalStateException("MINIO_BUCKET 格式不合法");
        }
        if (properties.getImageMaxBytes() <= 0 || properties.getFileMaxBytes() <= 0
                || properties.getImageMaxPixels() <= 0) {
            throw new IllegalStateException("媒體大小與像素上限必須大於 0");
        }
    }

    /** 只接受明確的 HTTP(S) URL，避免無效 endpoint 到第一次上傳才被發現。 */
    private void requireHttpUrl(String value, String settingName) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(settingName + " 必須是有效的 HTTP(S) URL");
        }
    }
}
