package world.springai.survey.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 驗證、去重並上傳文章媒體，提供後台媒體庫與公開 URL。 */
@Service
public class MediaAssetService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetService.class);
    private final MediaAssetRepository repository;
    private final MediaContentInspector inspector;
    private final ObjectProvider<S3Client> s3Provider;
    private final MediaProperties properties;

    /** 注入資料庫、內容檢查器與可選 S3 client。 */
    public MediaAssetService(MediaAssetRepository repository,
                             MediaContentInspector inspector,
                             ObjectProvider<S3Client> s3Provider,
                             MediaProperties properties) {
        this.repository = repository;
        this.inspector = inspector;
        this.s3Provider = s3Provider;
        this.properties = properties;
    }

    /** 回傳後台需要的安全媒體欄位。 */
    public record MediaView(Long id, String kind, String contentType, long sizeBytes,
                            String originalName, Integer width, Integer height,
                            String createdAt, String url) {
    }

    /**
     * 上傳檔案：先限制傳輸大小，再以內容判型、計算雜湊並冪等寫入 MinIO。
     */
    public MediaView upload(MultipartFile file) {
        S3Client s3 = requireStorage();
        if (file == null || file.isEmpty()) {
            throw badRequest("請選擇要上傳的檔案");
        }
        long declaredSize = file.getSize();
        if (declaredSize <= 0 || declaredSize > properties.getFileMaxBytes()) {
            throw badRequest("檔案大小超過 10 MB 上限");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw badRequest("無法讀取上傳內容");
        }
        if (bytes.length != declaredSize || bytes.length > properties.getFileMaxBytes()) {
            throw badRequest("上傳內容大小不一致或超過上限");
        }

        String originalName = sanitizeName(file.getOriginalFilename());
        MediaContentInspector.Detected detected =
            inspector.inspect(bytes, originalName, file.getContentType());
        if (MediaAsset.KIND_IMAGE.equals(detected.kind())
                && bytes.length > properties.getImageMaxBytes()) {
            throw badRequest("圖片大小超過 5 MB 上限");
        }

        String hash = sha256(bytes);
        Optional<MediaAsset> existing = repository.findBySha256(hash);
        if (existing.isPresent()) {
            return view(existing.get());
        }

        String objectKey = ("%s/%s.%s").formatted(
            MediaAsset.KIND_IMAGE.equals(detected.kind()) ? "images" : "files",
            hash, detected.extension());
        putObject(s3, objectKey, detected, bytes, originalName);

        MediaAsset asset = new MediaAsset(objectKey, hash, detected.kind(), detected.contentType(),
            bytes.length, originalName, detected.width(), detected.height());
        try {
            return view(repository.save(asset));
        } catch (DataIntegrityViolationException exception) {
            // 兩個節點同時上傳相同內容時，物件 key 相同且內容相同；唯一鍵輸家讀回贏家即可。
            return repository.findBySha256(hash).map(this::view).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "媒體索引暫時無法建立"));
        }
    }

    /** 列出最近媒體；上限收斂到 1–200，避免一次拉回整張表。 */
    public List<MediaView> list(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
            .stream().map(this::view).toList();
    }

    /** 驗證指定媒體存在且為圖片，供文章封面在副作用前守門。 */
    public void requireImage(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        MediaAsset asset = repository.findById(mediaId)
            .orElseThrow(() -> badRequest("找不到指定的封面圖片"));
        if (!asset.isImage()) {
            throw badRequest("文章封面必須選擇圖片，不能使用文件");
        }
    }

    /** 批次取得公開 URL，避免 archive 每張卡片各查一次資料庫。 */
    public Map<Long, String> publicUrls(Iterable<Long> ids) {
        Map<Long, String> urls = new LinkedHashMap<>();
        repository.findAllById(ids).forEach(asset -> urls.put(asset.getId(), publicUrl(asset)));
        return urls;
    }

    /** 取得單一媒體公開 URL。 */
    public Optional<String> publicUrl(Long id) {
        return id == null ? Optional.empty() : repository.findById(id).map(this::publicUrl);
    }

    /** 將 entity 轉成不包含 object key／hash 的後台 DTO。 */
    private MediaView view(MediaAsset asset) {
        return new MediaView(asset.getId(), asset.getKind(), asset.getContentType(),
            asset.getSizeBytes(), asset.getOriginalName(), asset.getWidth(), asset.getHeight(),
            asset.getCreatedAt() == null ? null : asset.getCreatedAt().toString(), publicUrl(asset));
    }

    /** 把已驗證內容寫進以 hash 命名的物件，圖片 inline、文件 attachment。 */
    private void putObject(S3Client s3, String objectKey, MediaContentInspector.Detected detected,
                           byte[] bytes, String originalName) {
        String disposition = MediaAsset.KIND_IMAGE.equals(detected.kind())
            ? "inline"
            : "attachment; filename*=UTF-8''" + URLEncoder.encode(originalName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(objectKey)
            .contentType(detected.contentType())
            .contentLength((long) bytes.length)
            .cacheControl("public, max-age=31536000, immutable")
            .contentDisposition(disposition)
            .build();
        try {
            s3.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception exception) {
            log.warn("MinIO 上傳失敗 status={} requestId={}",
                exception.statusCode(), exception.requestId());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "媒體儲存暫時無法使用");
        }
    }

    /** 媒體停用或 bean 不存在時明確回 503，不假裝上傳成功。 */
    private S3Client requireStorage() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "媒體儲存尚未啟用");
        }
        S3Client s3 = s3Provider.getIfAvailable();
        if (s3 == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "媒體儲存尚未就緒");
        }
        return s3;
    }

    /** 組 path-style 公開 URL；object key 只由伺服器產生，不含使用者輸入。 */
    private String publicUrl(MediaAsset asset) {
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        return UriComponentsBuilder.fromUriString(base)
            .pathSegment(properties.getBucket())
            .path("/" + asset.getObjectKey())
            .build()
            .encode()
            .toUriString();
    }

    /** 移除路徑與控制字元並限制長度，避免檔名注入 response header。 */
    private String sanitizeName(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
            .replaceAll("[\\p{Cntrl}]", "")
            .strip();
        if (name.isBlank()) {
            return "upload";
        }
        int[] codePoints = name.codePoints().limit(180).toArray();
        return new String(codePoints, 0, codePoints.length);
    }

    /** 計算小寫十六進位 SHA-256。 */
    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                .toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支援 SHA-256", exception);
        }
    }

    /** 建立內容驗證 400。 */
    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
