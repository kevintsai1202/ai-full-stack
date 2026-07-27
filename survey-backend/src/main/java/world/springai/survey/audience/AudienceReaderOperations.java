package world.springai.survey.audience;

import java.time.OffsetDateTime;

/**
 * 名單批次操作需要的 Reader 能力介面。
 * 介面放在 audience 層，由較上層 reader 實作，避免核心名單反向依賴 Reader 實作細節。
 */
public interface AudienceReaderOperations {

    /** 對單一既有 Reader 調整點數；成功回 true。 */
    boolean grantCreditsForAudience(String email, int delta, String note);

    /** 授予或延長單一人物 VIP。 */
    void grantVipForAudience(String email, int days, OffsetDateTime now);
}
