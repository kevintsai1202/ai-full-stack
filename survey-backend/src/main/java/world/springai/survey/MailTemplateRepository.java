package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 信件範本資料存取層 */
public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {
    /** 依範本識別鍵查詢（如 invite） */
    Optional<MailTemplate> findByTemplateKey(String templateKey);
}
