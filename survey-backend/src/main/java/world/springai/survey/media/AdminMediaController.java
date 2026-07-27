package world.springai.survey.media;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import world.springai.survey.AdminKeyGuard;

import java.util.List;

/** 受管理金鑰保護的文章媒體上傳與媒體庫 API。 */
@RestController
public class AdminMediaController {

    private static final String KEY_HEADER = "X-Admin-Key";
    private final AdminKeyGuard guard;
    private final MediaAssetService mediaService;

    /** 注入管理金鑰守門與媒體服務。 */
    public AdminMediaController(AdminKeyGuard guard, MediaAssetService mediaService) {
        this.guard = guard;
        this.mediaService = mediaService;
    }

    /** 上傳圖片或安全白名單文件。 */
    @PostMapping(value = "/api/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaAssetService.MediaView upload(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam("file") MultipartFile file) {
        guard.verify(key);
        return mediaService.upload(file);
    }

    /** 列出最近上傳的媒體。 */
    @GetMapping("/api/admin/media")
    public List<MediaAssetService.MediaView> list(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestParam(defaultValue = "100") int limit) {
        guard.verify(key);
        return mediaService.list(limit);
    }
}
