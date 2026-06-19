package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/** 無金鑰時的寄信 fallback：不真寄、只記 log，回傳固定假 id，供本機/測試使用 */
public class NoopMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopMailSender.class);

    /** 不實際寄信，僅記錄一筆 log 並回傳 "noop" */
    @Override
    public String send(String to, String subject, String html) {
        log.info("（未設 SEND_MAIL_API）略過寄信 to={} subject={}", to, subject);
        return "noop";
    }

    /** 不實際寄送，僅記 log，回傳固定 job id */
    @Override
    public String sendBatch(List<Email> emails) {
        log.info("（未設 SEND_MAIL_API）略過批量寄信 count={}", emails.size());
        return "noop-batch";
    }

    /** 不實際排程，僅記 log，回傳固定 id */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        log.info("（未設 SEND_MAIL_API）略過排程寄信 to={} at={}", email.to(), scheduledAt);
        return "noop-sched";
    }

    /** 不實際取消，僅記 log，回傳 true */
    @Override
    public boolean cancelScheduled(String providerId) {
        log.info("（未設 SEND_MAIL_API）略過取消排程 id={}", providerId);
        return true;
    }
}
