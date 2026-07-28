package world.springai.survey.reader;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 為 Open Graph 與限時動態動態產生文章 PNG 圖卡。 */
@RestController
public class ShareCardController {

    private final CampaignRepository campaigns;

    /** 注入文章資料來源。 */
    public ShareCardController(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    /** 產生 1200×630 預覽圖或 1080×1920 限動圖；未發布文章一律 404。 */
    @GetMapping(value = "/r/share-card/{slug}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> card(@PathVariable String slug,
                                       @RequestParam(defaultValue = "og") String layout)
            throws IOException {
        Campaign campaign = campaigns.findBySlug(slug).filter(Campaign::isPublished)
            .orElse(null);
        if (campaign == null) return ResponseEntity.notFound().build();
        boolean story = "story".equals(layout);
        byte[] png = render(campaign, story ? 1080 : 1200, story ? 1920 : 630, story);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
            .contentType(MediaType.IMAGE_PNG)
            .body(png);
    }

    /** 使用 Java 內建繪圖生成無外部依賴的品牌圖卡。 */
    private byte[] render(Campaign campaign, int width, int height, boolean story)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(244, 250, 248));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(8, 127, 115));
        g.fillRoundRect(story ? 72 : 64, story ? 110 : 54,
            story ? width - 144 : width - 128, story ? height - 220 : height - 108,
            story ? 52 : 36, story ? 52 : 36);
        g.setColor(Color.WHITE);
        int x = story ? 130 : 112;
        int maxWidth = width - x * 2;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, story ? 42 : 32));
        g.drawString("SPRINGAI.WORLD  ·  凱文大叔的電子報", x, story ? 230 : 145);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, story ? 76 : 58));
        List<String> lines = wrap(g.getFontMetrics(), campaign.getSubject(), maxWidth,
            story ? 6 : 3);
        int y = story ? 470 : 270;
        int step = story ? 112 : 82;
        for (String line : lines) {
            g.drawString(line, x, y);
            y += step;
        }
        String emoji = campaign.getCoverEmoji();
        if (emoji != null && !emoji.isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, story ? 170 : 100));
            g.drawString(emoji, x, story ? 1420 : 520);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, story ? 38 : 27));
        g.drawString(story ? "向上滑／點擊連結閱讀完整文章" : "閱讀、收藏，分享給需要的人",
            x, story ? height - 220 : height - 105);
        g.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** 依實際字寬換行，避免中英文混排超出圖卡。 */
    private List<String> wrap(FontMetrics metrics, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (metrics.stringWidth(current.toString() + ch) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() == maxLines - 1) break;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}

