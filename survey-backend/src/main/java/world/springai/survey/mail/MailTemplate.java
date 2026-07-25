package world.springai.survey.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 信件範本實體，對應資料表 mail_template；body_html 以 {{confirmLink}} 佔位確認連結 */
@Entity
@Table(name = "mail_template")
public class MailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 範本識別鍵（如 invite），唯一 */
    @Column(name = "template_key", nullable = false, unique = true)
    private String templateKey;

    /** 信件主旨 */
    @Column(nullable = false)
    private String subject;

    /** 信件 HTML 內文（含佔位符） */
    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參數建構子 */
    protected MailTemplate() {
    }

    /** 建立一筆範本 */
    public MailTemplate(String templateKey, String subject, String bodyHtml) {
        this.templateKey = templateKey;
        this.subject = subject;
        this.bodyHtml = bodyHtml;
    }

    public Long getId() { return id; }
    public String getTemplateKey() { return templateKey; }
    public String getSubject() { return subject; }
    public String getBodyHtml() { return bodyHtml; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setSubject(String subject) { this.subject = subject; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
}
