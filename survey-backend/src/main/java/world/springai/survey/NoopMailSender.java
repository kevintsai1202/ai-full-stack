package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 無金鑰時的寄信 fallback：不真寄、只記 log，回傳固定假 id，供本機/測試使用 */
public class NoopMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopMailSender.class);

    /** 不實際寄信，僅記錄一筆 log 並回傳 "noop" */
    @Override
    public String send(String to, String subject, String html) {
        log.info("（未設 SEND_MAIL_API）略過寄信 to={} subject={}", to, subject);
        return "noop";
    }
}
