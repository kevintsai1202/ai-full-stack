package world.springai.survey.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 媒體實際格式、尺寸與主動內容拒絕測試。 */
class MediaContentInspectorTest {

    private MediaProperties properties;
    private MediaContentInspector inspector;

    /** 每個案例使用正常的媒體上限。 */
    @BeforeEach
    void setUp() {
        properties = new MediaProperties();
        inspector = new MediaContentInspector(properties);
    }

    /** 真正可解碼的 PNG 才能被接受，並回傳實際寬高。 */
    @Test
    void acceptsDecodablePngAndReadsDimensions() throws Exception {
        byte[] bytes = png(2, 3);

        MediaContentInspector.Detected detected = inspector.inspect(bytes, "cover.png", "image/png");

        assertEquals(MediaAsset.KIND_IMAGE, detected.kind());
        assertEquals("image/png", detected.contentType());
        assertEquals(2, detected.width());
        assertEquals(3, detected.height());
    }

    /** 只有 PNG header、沒有可解碼內容時必須拒絕。 */
    @Test
    void rejectsSpoofedTruncatedPng() {
        byte[] bytes = new byte[24];
        byte[] header = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(header, 0, bytes, 0, header.length);
        bytes[19] = 1;
        bytes[23] = 1;

        assertThrows(ResponseStatusException.class,
            () -> inspector.inspect(bytes, "fake.png", "image/png"));
    }

    /** 即使圖片可解碼，超過像素上限仍在解碼前被拒絕。 */
    @Test
    void rejectsImageOverPixelLimit() throws Exception {
        properties.setImageMaxPixels(5);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> inspector.inspect(png(2, 3), "large.png", "image/png"));

        assertEquals(400, error.getStatusCode().value());
    }

    /** SVG 是可執行主動內容，不得進公開 bucket。 */
    @Test
    void rejectsSvgEvenWhenBrowserDeclaresImage() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"
            .getBytes(StandardCharsets.UTF_8);

        assertThrows(ResponseStatusException.class,
            () -> inspector.inspect(svg, "active.svg", "image/svg+xml"));
    }

    /** PDF 依 magic bytes 接受，不信任單純副檔名。 */
    @Test
    void acceptsPdfByMagicBytes() {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        MediaContentInspector.Detected detected =
            inspector.inspect(pdf, "guide.pdf", "application/pdf");

        assertEquals(MediaAsset.KIND_FILE, detected.kind());
        assertEquals("application/pdf", detected.contentType());
    }

    /** Office 文件必須同時含 Content Types 與對應 application 目錄。 */
    @Test
    void acceptsStructurallyValidDocx() throws Exception {
        byte[] docx = zip("[Content_Types].xml", "<Types/>", "word/document.xml", "<document/>");

        MediaContentInspector.Detected detected = inspector.inspect(docx, "guide.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertEquals("docx", detected.extension());
    }

    /** 任意 ZIP 改名成 DOCX 仍必須拒絕。 */
    @Test
    void rejectsZipDisguisedAsDocx() throws Exception {
        byte[] zip = zip("payload.bin", "x", "readme.txt", "not office");

        assertThrows(ResponseStatusException.class, () -> inspector.inspect(zip, "fake.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    /** 瀏覽器宣告 MIME 與實際內容矛盾時拒絕。 */
    @Test
    void rejectsDeclaredMimeMismatch() {
        byte[] pdf = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        assertThrows(ResponseStatusException.class,
            () -> inspector.inspect(pdf, "guide.pdf", "image/png"));
    }

    /** 產生測試用真實 PNG。 */
    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }

    /** 產生包含兩個 entry 的測試 ZIP。 */
    private byte[] zip(String firstName, String firstContent, String secondName, String secondContent)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(firstName));
            zip.write(firstContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(secondName));
            zip.write(secondContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
