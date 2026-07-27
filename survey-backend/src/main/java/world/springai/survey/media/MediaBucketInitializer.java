package world.springai.survey.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** 啟動時以冪等方式建立 bucket 並套用匿名唯讀 policy。 */
@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class MediaBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaBucketInitializer.class);
    private final S3Client s3;
    private final MediaProperties properties;

    /** 注入 S3 client 與 bucket 設定。 */
    public MediaBucketInitializer(S3Client s3, MediaProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    /**
     * 初始化失敗直接中止啟動；媒體功能不能以「設定啟用但實際不可用」的狀態上線。
     */
    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.getBucket();
        if (!bucketExists(bucket)) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception exception) {
                // 多實例同時啟動時，另一台可能在 head 與 create 之間先建好。
                if (!bucketExists(bucket)) {
                    throw new IllegalStateException("MinIO bucket 建立失敗，服務停止啟動");
                }
            }
        }
        s3.putBucketPolicy(PutBucketPolicyRequest.builder()
            .bucket(bucket)
            .policy(publicReadPolicy(bucket))
            .build());
        log.info("文章媒體 bucket 已就緒：{}", bucket);
    }

    /** 查詢 bucket 是否存在；404 視為不存在，其餘錯誤一律向上拋出。 */
    private boolean bucketExists(String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new IllegalStateException("無法確認 MinIO bucket 狀態，服務停止啟動");
        }
    }

    /** 建立只允許匿名讀取物件、不允許列目錄或寫入的 bucket policy。 */
    private String publicReadPolicy(String bucket) {
        return """
            {
              "Version":"2012-10-17",
              "Statement":[{
                "Effect":"Allow",
                "Principal":{"AWS":["*"]},
                "Action":["s3:GetObject"],
                "Resource":["arn:aws:s3:::%s/*"]
              }]
            }
            """.formatted(bucket);
    }
}
