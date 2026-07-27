package world.springai.survey.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** MinIO 文章媒體的資料庫中繼資料。 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_FILE = "FILE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;
    @Column(nullable = false, unique = true, length = 64)
    private String sha256;
    @Column(nullable = false)
    private String kind;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "original_name", nullable = false)
    private String originalName;
    private Integer width;
    private Integer height;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 使用的無參數建構子。 */
    protected MediaAsset() {
    }

    /** 建立已完成內容驗證的媒體索引。 */
    public MediaAsset(String objectKey, String sha256, String kind, String contentType,
                      long sizeBytes, String originalName, Integer width, Integer height) {
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.kind = kind;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.originalName = originalName;
        this.width = width;
        this.height = height;
    }

    public Long getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getSha256() { return sha256; }
    public String getKind() { return kind; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getOriginalName() { return originalName; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** 是否可作為文章封面或 Markdown 圖片。 */
    public boolean isImage() {
        return KIND_IMAGE.equals(kind);
    }
}
