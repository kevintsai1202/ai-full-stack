package world.springai.survey.audience;

import io.github.kevintsai1202.sesmail.events.SesBounce;
import io.github.kevintsai1202.sesmail.events.SesComplaint;
import io.github.kevintsai1202.sesmail.events.SesEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SES 退信／檢舉事件的處理：讓收不到或不想收的信箱不再收到電子報。
 *
 * <ul>
 *   <li>永久退信（信箱不存在等）→ 退訂，並寫入來源 {@code ses-bounce} 的退訂事件。</li>
 *   <li>暫時退信（信箱滿、暫時拒收）→ 只記 log，不退訂。</li>
 *   <li>檢舉為垃圾信 → 立即退訂，來源 {@code ses-complaint}。</li>
 * </ul>
 *
 * <p>SNS 可能重送同一則通知：{@code unsubscribeByEmail} 重做沒有副作用，且只有真的改到資料時
 * 才寫同意事件，所以整體是冪等的。email 正規化必須與退訂連結端點一致（{@code trim().toLowerCase(Locale.ROOT)}）。
 * 只有啟用 {@code sesmail.sns.enabled} 時 starter 才會呼叫本類。</p>
 */
@Component
public class SesSuppressionListener implements SesEventListener {

    private static final Logger log = LoggerFactory.getLogger(SesSuppressionListener.class);

    /** 退訂事件來源：永久退信 */
    static final String SOURCE_BOUNCE = "ses-bounce";
    /** 退訂事件來源：檢舉 */
    static final String SOURCE_COMPLAINT = "ses-complaint";

    private final SurveyResponseRepository repository;
    private final AudiencePlatformService audiencePlatformService;

    /** 注入訂閱名單與人物同意事件服務 */
    public SesSuppressionListener(SurveyResponseRepository repository,
                                  AudiencePlatformService audiencePlatformService) {
        this.repository = repository;
        this.audiencePlatformService = audiencePlatformService;
    }

    /** 永久退信才退訂；暫時退信只記錄 */
    @Override
    public void onBounce(SesBounce bounce) {
        if (!bounce.permanent()) {
            log.info("SES 暫時退信（{}／{}），不退訂：{}", bounce.bounceType(), bounce.bounceSubType(),
                bounce.recipients().size());
            return;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("bounceType", bounce.bounceType());
        putIfPresent(evidence, "bounceSubType", bounce.bounceSubType());
        putIfPresent(evidence, "messageId", bounce.messageId());
        suppress(bounce.recipients(), SOURCE_BOUNCE, evidence);
    }

    /** 檢舉一律退訂 */
    @Override
    public void onComplaint(SesComplaint complaint) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        putIfPresent(evidence, "feedbackType", complaint.feedbackType());
        putIfPresent(evidence, "messageId", complaint.messageId());
        suppress(complaint.recipients(), SOURCE_COMPLAINT, evidence);
    }

    /** 逐一退訂；只有真的改到資料時才寫退訂事件 */
    private void suppress(List<String> recipients, String source, Map<String, Object> evidence) {
        OffsetDateTime now = OffsetDateTime.now();
        for (String email : recipients) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            int affected = repository.unsubscribeByEmail(normalized);
            if (affected > 0) {
                audiencePlatformService.appendConsentByEmail(normalized,
                    AudiencePlatformService.CONSENT_UNSUBSCRIBED, source, evidence, now);
                log.info("SES {} 已退訂一位讀者", source);
            }
        }
    }

    /** 值非 null 才放入（Map.of 不接受 null，證據也不需要空欄位） */
    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
