package world.springai.survey;

import java.time.Instant;
import java.util.List;

/** 寄信抽象：單封、批量、排程與取消排程 */
public interface MailSender {

    /** 一封信的內容（寄件人由實作端的設定提供） */
    record Email(String to, String subject, String html) {}

    /** 寄一封 HTML 信給單一收件人，回傳 provider 訊息 id */
    String send(String to, String subject, String html);

    /** 批量立即寄送（呼叫端需自行切成每批 ≤100 封），回傳整批的 job id */
    String sendBatch(List<Email> emails);

    /** 排程單封到未來時間，回傳該封的 provider id（可用於取消） */
    String schedule(Email email, Instant scheduledAt);

    /** 取消一封排程信；成功回 true */
    boolean cancelScheduled(String providerId);
}
