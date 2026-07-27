package world.springai.survey.audience;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 啟用 Exam cursor 增量同步排程；實際執行仍由 scheduled-enabled 控制。 */
@Configuration
@EnableScheduling
public class AudienceSchedulingConfig {
}
