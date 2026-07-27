package world.springai.survey.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文章媒體物件儲存設定；憑證只存在伺服器環境變數。 */
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    private boolean enabled;
    private String endpoint = "http://127.0.0.1:9000";
    private String publicBaseUrl = "http://127.0.0.1:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "newsletter-media";
    private String region = "us-east-1";
    private long imageMaxBytes = 5L * 1024 * 1024;
    private long fileMaxBytes = 10L * 1024 * 1024;
    private long imageMaxPixels = 40_000_000L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public long getImageMaxBytes() { return imageMaxBytes; }
    public void setImageMaxBytes(long imageMaxBytes) { this.imageMaxBytes = imageMaxBytes; }
    public long getFileMaxBytes() { return fileMaxBytes; }
    public void setFileMaxBytes(long fileMaxBytes) { this.fileMaxBytes = fileMaxBytes; }
    public long getImageMaxPixels() { return imageMaxPixels; }
    public void setImageMaxPixels(long imageMaxPixels) { this.imageMaxPixels = imageMaxPixels; }
}
