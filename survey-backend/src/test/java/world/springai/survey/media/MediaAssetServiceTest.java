package world.springai.survey.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 媒體上傳停用、MinIO 寫入與內容去重測試。 */
class MediaAssetServiceTest {

    private MediaAssetRepository repository;
    private ObjectProvider<S3Client> provider;
    private S3Client s3;
    private MediaProperties properties;
    private MediaAssetService service;

    /** 建立不連線的 S3 mock 與預設啟用設定。 */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        provider = mock(ObjectProvider.class);
        s3 = mock(S3Client.class);
        properties = new MediaProperties();
        properties.setEnabled(true);
        properties.setPublicBaseUrl("https://media.example.com");
        properties.setBucket("newsletter-media");
        when(provider.getIfAvailable()).thenReturn(s3);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        service = new MediaAssetService(repository, new MediaContentInspector(properties),
            provider, properties);
    }

    /** 媒體儲存未啟用時明確回 503，不建立假資料列。 */
    @Test
    void disabledStorageReturnsServiceUnavailable() {
        properties.setEnabled(false);
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

        ResponseStatusException error =
            assertThrows(ResponseStatusException.class, () -> service.upload(file));

        assertEquals(503, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    /** 新圖片會寫入 hash object key 並建立媒體索引。 */
    @Test
    void uploadsValidatedImageToS3() throws Exception {
        byte[] bytes = png();
        when(repository.findBySha256(any())).thenReturn(Optional.empty());
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAssetService.MediaView result = service.upload(
            new MockMultipartFile("file", "cover.png", "image/png", bytes));

        assertEquals(MediaAsset.KIND_IMAGE, result.kind());
        assertEquals("https://media.example.com/newsletter-media/"
            + result.url().substring(result.url().lastIndexOf("images/")), result.url());
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(repository).save(any(MediaAsset.class));
    }

    /** 相同內容已存在時直接回傳，不再次寫 MinIO。 */
    @Test
    void duplicateContentDoesNotUploadAgain() throws Exception {
        byte[] bytes = png();
        MediaAsset existing = new MediaAsset("images/existing.png", "hash",
            MediaAsset.KIND_IMAGE, "image/png", bytes.length, "first.png", 2, 2);
        when(repository.findBySha256(any())).thenReturn(Optional.of(existing));

        MediaAssetService.MediaView result = service.upload(
            new MockMultipartFile("file", "second.png", "image/png", bytes));

        assertEquals("first.png", result.originalName());
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(repository, never()).save(any());
    }

    /** 產生可解碼的 2×2 PNG。 */
    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }
}
