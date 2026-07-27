package world.springai.survey.media;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** 依實際內容而非檔名／宣告 MIME 判定可接受的媒體格式。 */
@Component
public class MediaContentInspector {

    private static final int MAX_OOXML_ENTRIES = 512;
    private static final long MAX_OOXML_EXPANDED_BYTES = 32L * 1024 * 1024;
    private final MediaProperties properties;

    /** 注入圖片像素上限。 */
    public MediaContentInspector(MediaProperties properties) {
        this.properties = properties;
    }

    /** 已判定且正規化的媒體資訊。 */
    public record Detected(String kind, String contentType, String extension,
                           Integer width, Integer height) {
    }

    /**
     * 驗證實際格式、宣告 MIME 與圖片尺寸；不支援的主動內容一律拒絕。
     */
    public Detected inspect(byte[] bytes, String originalName, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw badRequest("檔案內容不可為空");
        }
        String extension = extensionOf(originalName);
        Detected detected = detectImage(bytes);
        if (detected == null) {
            detected = detectDocument(bytes, extension);
        }
        if (detected == null) {
            throw badRequest("不支援此檔案格式；圖片限 PNG/JPEG/GIF/WebP，文件限 PDF/TXT/CSV/DOCX/XLSX/PPTX");
        }
        validateDeclaredType(detected, declaredContentType);
        if (MediaAsset.KIND_IMAGE.equals(detected.kind())) {
            long pixels = Math.multiplyExact((long) detected.width(), (long) detected.height());
            if (pixels > properties.getImageMaxPixels()) {
                throw badRequest("圖片像素過大，請縮小解析度後再上傳");
            }
            validateDecodableImage(bytes, detected);
        }
        return detected;
    }

    /**
     * PNG/JPEG/GIF 交給實際 decoder 驗證，不能只靠可偽造的 header；WebP 則驗 RIFF 長度。
     */
    private void validateDecodableImage(byte[] bytes, Detected detected) {
        if ("image/webp".equals(detected.contentType())) {
            long declaredRiffSize = Integer.toUnsignedLong(
                ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()) + 8;
            if (declaredRiffSize != bytes.length) {
                throw badRequest("WebP 圖片長度與 header 不一致");
            }
            return;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != detected.width() || image.getHeight() != detected.height()) {
                throw badRequest("圖片已損毀或實際尺寸不一致");
            }
            image.flush();
        } catch (IOException exception) {
            throw badRequest("圖片已損毀或無法解碼");
        }
    }

    /** 依 magic bytes 判定圖片格式並讀取寬高。 */
    private Detected detectImage(byte[] bytes) {
        if (startsWith(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) {
            requireLength(bytes, 24);
            int width = readIntBigEndian(bytes, 16);
            int height = readIntBigEndian(bytes, 20);
            return image("image/png", "png", width, height);
        }
        if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38)
                && bytes.length >= 10
                && (bytes[4] == 0x37 || bytes[4] == 0x39)
                && bytes[5] == 0x61) {
            return image("image/gif", "gif", readUnsignedShortLE(bytes, 6), readUnsignedShortLE(bytes, 8));
        }
        if (startsWith(bytes, 0xff, 0xd8, 0xff)) {
            int[] size = jpegSize(bytes);
            return image("image/jpeg", "jpg", size[0], size[1]);
        }
        if (bytes.length >= 30 && asciiEquals(bytes, 0, "RIFF") && asciiEquals(bytes, 8, "WEBP")) {
            int[] size = webpSize(bytes);
            return image("image/webp", "webp", size[0], size[1]);
        }
        return null;
    }

    /** 依 magic bytes 與可信副檔名判定可下載文件。 */
    private Detected detectDocument(byte[] bytes, String extension) {
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2d)) {
            return file("application/pdf", "pdf");
        }
        if (Set.of("docx", "xlsx", "pptx").contains(extension)
                && startsWith(bytes, 0x50, 0x4b, 0x03, 0x04)) {
            validateOoxml(bytes, extension);
            return switch (extension) {
                case "docx" -> file("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
                case "xlsx" -> file("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
                case "pptx" -> file("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
                default -> throw new IllegalStateException("不可達的 OOXML 類型");
            };
        }
        if (Set.of("txt", "csv").contains(extension) && isUtf8Text(bytes)) {
            return "csv".equals(extension) ? file("text/csv", "csv") : file("text/plain", "txt");
        }
        return null;
    }

    /**
     * 驗證 OOXML 的必要 entry 並限制展開量，避免把任意 ZIP 或壓縮炸彈當文件代管。
     */
    private void validateOoxml(byte[] bytes, String extension) {
        String requiredPrefix = switch (extension) {
            case "docx" -> "word/";
            case "xlsx" -> "xl/";
            case "pptx" -> "ppt/";
            default -> throw badRequest("不支援的 Office 文件");
        };
        boolean contentTypes = false;
        boolean applicationPart = false;
        int entries = 0;
        long expanded = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_OOXML_ENTRIES) {
                    throw badRequest("Office 文件結構過於複雜");
                }
                String name = entry.getName().replace('\\', '/');
                contentTypes |= "[Content_Types].xml".equals(name);
                applicationPart |= name.startsWith(requiredPrefix);
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    expanded += read;
                    if (expanded > MAX_OOXML_EXPANDED_BYTES) {
                        throw badRequest("Office 文件展開後過大");
                    }
                }
            }
        } catch (IOException exception) {
            throw badRequest("Office 文件已損毀或格式不正確");
        }
        if (!contentTypes || !applicationPart) {
            throw badRequest("檔案內容與 Office 副檔名不符");
        }
    }

    /** 驗證瀏覽器宣告的 MIME 不與實際內容矛盾；octet-stream 視為未宣告。 */
    private void validateDeclaredType(Detected detected, String declared) {
        if (declared == null || declared.isBlank()
                || "application/octet-stream".equalsIgnoreCase(declared)) {
            return;
        }
        String normalized = declared.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        Set<String> aliases = switch (detected.contentType()) {
            case "image/jpeg" -> Set.of("image/jpeg", "image/jpg");
            case "text/csv" -> Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain");
            default -> Set.of(detected.contentType());
        };
        if (!aliases.contains(normalized)) {
            throw badRequest("檔案內容與瀏覽器宣告的格式不符");
        }
    }

    /** 解析 JPEG SOF marker 中的寬高，拒絕沒有有效 frame 的偽造檔。 */
    private int[] jpegSize(byte[] bytes) {
        int offset = 2;
        while (offset + 4 < bytes.length) {
            if ((bytes[offset] & 0xff) != 0xff) {
                offset++;
                continue;
            }
            int marker = bytes[offset + 1] & 0xff;
            offset += 2;
            if (marker == 0xd8 || marker == 0xd9 || marker == 0x01
                    || (marker >= 0xd0 && marker <= 0xd7)) {
                continue;
            }
            if (offset + 2 > bytes.length) {
                break;
            }
            int length = ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
            if (length < 2 || offset + length > bytes.length) {
                break;
            }
            if (isSofMarker(marker) && length >= 7) {
                int height = ((bytes[offset + 3] & 0xff) << 8) | (bytes[offset + 4] & 0xff);
                int width = ((bytes[offset + 5] & 0xff) << 8) | (bytes[offset + 6] & 0xff);
                return validDimensions(width, height);
            }
            offset += length;
        }
        throw badRequest("JPEG 圖片已損毀或缺少尺寸資訊");
    }

    /** 判斷 JPEG marker 是否為包含寬高的 Start Of Frame。 */
    private boolean isSofMarker(int marker) {
        return Set.of(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7,
            0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf).contains(marker);
    }

    /** 解析 WebP VP8X／VP8L／VP8 三種 header 的寬高。 */
    private int[] webpSize(byte[] bytes) {
        String chunk = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk)) {
            requireLength(bytes, 30);
            return validDimensions(1 + readUnsigned24LE(bytes, 24), 1 + readUnsigned24LE(bytes, 27));
        }
        if ("VP8L".equals(chunk)) {
            requireLength(bytes, 25);
            if ((bytes[20] & 0xff) != 0x2f) {
                throw badRequest("WebP 圖片 header 不正確");
            }
            int width = 1 + ((bytes[21] & 0xff) | ((bytes[22] & 0x3f) << 8));
            int height = 1 + (((bytes[22] & 0xc0) >> 6)
                | ((bytes[23] & 0xff) << 2) | ((bytes[24] & 0x0f) << 10));
            return validDimensions(width, height);
        }
        if ("VP8 ".equals(chunk)) {
            requireLength(bytes, 30);
            if (!startsWithAt(bytes, 23, 0x9d, 0x01, 0x2a)) {
                throw badRequest("WebP 圖片 frame header 不正確");
            }
            return validDimensions(readUnsignedShortLE(bytes, 26) & 0x3fff,
                readUnsignedShortLE(bytes, 28) & 0x3fff);
        }
        throw badRequest("不支援此 WebP 圖片編碼");
    }

    /** 以嚴格 UTF-8 解碼並拒絕 NUL，避免把二進位內容偽裝成文字文件。 */
    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    /** 建立圖片偵測結果並驗證尺寸為正數。 */
    private Detected image(String contentType, String extension, int width, int height) {
        int[] size = validDimensions(width, height);
        return new Detected(MediaAsset.KIND_IMAGE, contentType, extension, size[0], size[1]);
    }

    /** 建立文件偵測結果。 */
    private Detected file(String contentType, String extension) {
        return new Detected(MediaAsset.KIND_FILE, contentType, extension, null, null);
    }

    /** 拒絕零或負數尺寸。 */
    private int[] validDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw badRequest("圖片尺寸不正確");
        }
        return new int[]{width, height};
    }

    /** 從使用者檔名取得小寫副檔名；路徑片段不參與判斷。 */
    private String extensionOf(String originalName) {
        String safe = originalName == null ? "" : originalName.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1);
        int dot = safe.lastIndexOf('.');
        return dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 驗證 byte array 至少有指定長度。 */
    private void requireLength(byte[] bytes, int length) {
        if (bytes.length < length) {
            throw badRequest("檔案內容不完整");
        }
    }

    /** 比對檔頭 magic bytes。 */
    private boolean startsWith(byte[] bytes, int... expected) {
        return startsWithAt(bytes, 0, expected);
    }

    /** 從指定 offset 比對 magic bytes。 */
    private boolean startsWithAt(byte[] bytes, int offset, int... expected) {
        if (offset < 0 || bytes.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[offset + index] & 0xff) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    /** 比對固定 ASCII 標記。 */
    private boolean asciiEquals(byte[] bytes, int offset, String expected) {
        return startsWithAt(bytes, offset, expected.chars().toArray());
    }

    /** 讀取 big-endian 32-bit 整數。 */
    private int readIntBigEndian(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    /** 讀取 little-endian 16-bit 無號整數。 */
    private int readUnsignedShortLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    /** 讀取 little-endian 24-bit 無號整數。 */
    private int readUnsigned24LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
            | ((bytes[offset + 2] & 0xff) << 16);
    }

    /** 建立不洩漏內部例外細節的 400。 */
    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
