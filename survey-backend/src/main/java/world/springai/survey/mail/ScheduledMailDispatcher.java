package world.springai.survey.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.LimitExceededException;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * SES 待寄佇列的派送器：定時撈出到期的信，逐封認領、送出、記錄結果。
 *
 * <p><b>規則</b></p>
 * <ul>
 *   <li>先以條件式 UPDATE 認領（PENDING → SENDING），認領不到就跳過——那一列剛被取消或已被處理。</li>
 *   <li>呼叫 SES 不包在資料庫交易裡，避免一封慢信把連線卡住。</li>
 *   <li>節流、網路錯誤、5xx 是暫時性的：退回 PENDING，依 30 秒 × 2^(n−1) 退避，
 *       達 {@link #MAX_ATTEMPTS} 次才放棄。其他錯誤（收件位址被拒、網域未驗證、帳號停權）重試也沒用，直接失敗。</li>
 *   <li>失敗時回寫 email_log，邀請信等「看 sent 判斷寄過沒」的流程下次才會重寄。</li>
 *   <li>每封之間依設定的速率暫停（沙箱每秒 1 封、正式環境起始約 14 封）。</li>
 *   <li>程序中斷後卡在 SENDING 的列<b>絕不自動重寄</b>：無法得知 SES 收到了沒，
 *       重複寄出一期電子報比漏寄一封更糟，只回報數量交給人工確認（見切換手冊）。</li>
 * </ul>
 */
public class ScheduledMailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMailDispatcher.class);

    /** 暫時性錯誤最多嘗試次數（含第一次） */
    static final int MAX_ATTEMPTS = 5;
    /** 每輪最多處理幾封，避免單輪跑太久 */
    static final int BATCH_LIMIT = 200;
    /** 第一次重試的等待時間，之後每次加倍 */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    /** SENDING 超過這段時間沒有變化，視為程序中斷留下的列 */
    private static final Duration STUCK_AFTER = Duration.ofMinutes(10);
    /** 錯誤訊息寫進資料庫前截斷的長度 */
    private static final int ERROR_MAX = 500;

    /** 暫停指定毫秒；正式環境是 Thread.sleep，測試改成記錄 */
    @FunctionalInterface
    public interface Sleeper {
        void pause(long millis) throws InterruptedException;
    }

    private final ScheduledMailRepository queue;
    private final EmailLogRepository emailLogs;
    /** 實際呼叫 SES 的寄信實作 */
    private final SesMailSender sender;
    /** 每封之間暫停的毫秒數（由每秒上限換算） */
    private final long pauseMillis;
    private final Clock clock;
    private final Sleeper sleeper;

    /**
     * @param maxSendRate 每秒最多送幾封；應不高於 SES 帳號的 MaxSendRate
     */
    public ScheduledMailDispatcher(ScheduledMailRepository queue, EmailLogRepository emailLogs,
                                   SesMailSender sender, double maxSendRate, Clock clock, Sleeper sleeper) {
        if (maxSendRate <= 0) {
            throw new IllegalArgumentException("MAIL_SES_MAX_SEND_RATE 必須大於 0");
        }
        this.queue = queue;
        this.emailLogs = emailLogs;
        this.sender = sender;
        this.pauseMillis = (long) Math.ceil(1000.0 / maxSendRate);
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** 排程進入點：固定間隔派送一輪 */
    @Scheduled(fixedDelayString = "${app.mail.ses.dispatch-interval-ms:10000}",
               initialDelayString = "${app.mail.ses.dispatch-interval-ms:10000}")
    public void tick() {
        try {
            dispatchDue();
        } catch (RuntimeException e) {
            // 例外若往外拋，排程器只會記一行 log；這裡補上脈絡
            log.error("SES 待寄佇列派送失敗：{}", e.getMessage(), e);
        }
    }

    /** 排程進入點：定期檢查卡住的列 */
    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void stuckCheck() {
        reportStuck();
    }

    /**
     * 派送一輪：撈出到期的信，逐封認領並送出。
     *
     * @return 本輪成功交給 SES 的封數
     */
    public int dispatchDue() {
        OffsetDateTime now = now();
        List<Long> ids = queue.findDueIds(now, BATCH_LIMIT);
        int sent = 0;
        for (Long id : ids) {
            if (queue.claim(id) != 1) {
                continue; // 剛被取消，或已被其他輪處理
            }
            ScheduledMail mail = queue.findById(id).orElse(null);
            if (mail == null) {
                continue;
            }
            try {
                String messageId = sender.deliver(mail.getRecipient(), mail.getSubject(), mail.getHtml());
                queue.markSent(id, messageId);
                sent++;
            } catch (RuntimeException e) {
                handleFailure(mail, e, now);
            }
            if (!pause()) {
                break;
            }
        }
        if (sent > 0) {
            log.info("SES 待寄佇列：本輪送出 {} 封（撈到 {} 封）", sent, ids.size());
        }
        return sent;
    }

    /**
     * 回報卡在 SENDING 的列數；有的話記警告，交給人工確認，不自動重寄。
     *
     * @return 卡住的列數
     */
    public long reportStuck() {
        long stuck = queue.countStuck(now().minus(STUCK_AFTER));
        if (stuck > 0) {
            log.warn("SES 待寄佇列有 {} 封卡在 SENDING 超過 {} 分鐘（可能是程序中斷），"
                + "不會自動重寄；請照切換手冊到 SES 寄送紀錄確認後手動處理", stuck, STUCK_AFTER.toMinutes());
        }
        return stuck;
    }

    /** 依錯誤種類決定退回重試或標為失敗 */
    private void handleFailure(ScheduledMail mail, RuntimeException e, OffsetDateTime now) {
        String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage());
        if (isTransient(e) && mail.getAttempts() < MAX_ATTEMPTS) {
            Duration backoff = BASE_BACKOFF.multipliedBy(1L << Math.max(0, mail.getAttempts() - 1));
            queue.retryLater(mail.getId(), error, now.plus(backoff));
            log.warn("SES 寄信暫時失敗，{} 秒後重試 id={} attempts={}：{}",
                backoff.toSeconds(), mail.getId(), mail.getAttempts(), error);
            return;
        }
        queue.markFailed(mail.getId(), error);
        emailLogs.markDeliveryFailed(mail.providerRef(), mail.getRecipient(), error);
        log.warn("SES 寄信失敗，不再重試 id={} attempts={}：{}", mail.getId(), mail.getAttempts(), error);
    }

    /** 節流、網路錯誤與 SES 端 5xx 視為暫時性，其餘（被拒、未驗證、停權、參數錯）重試無用 */
    static boolean isTransient(RuntimeException e) {
        if (e instanceof TooManyRequestsException || e instanceof LimitExceededException) {
            return true;
        }
        if (e instanceof AwsServiceException service) {
            return service.statusCode() >= 500;
        }
        return e instanceof SdkClientException;
    }

    /** 依速率暫停；被中斷時恢復中斷旗標並結束本輪 */
    private boolean pause() {
        try {
            sleeper.pause(pauseMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 目前時間（UTC） */
    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** 錯誤訊息截斷，避免整份 HTML 或堆疊塞進資料庫 */
    private static String truncate(String s) {
        return s == null || s.length() <= ERROR_MAX ? s : s.substring(0, ERROR_MAX);
    }
}
