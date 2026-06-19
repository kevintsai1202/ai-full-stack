package world.springai.survey;

/** 寄信抽象：單封寄送，回傳 provider 訊息 id；寄送失敗時拋 RuntimeException */
public interface MailSender {
    /** 寄一封 HTML 信給單一收件人，回傳 provider 訊息 id */
    String send(String to, String subject, String html);
}
