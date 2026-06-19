package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

/** 寄送記錄資料存取層 */
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
}
