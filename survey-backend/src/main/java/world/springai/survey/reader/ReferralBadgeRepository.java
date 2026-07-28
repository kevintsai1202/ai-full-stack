package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 邀請徽章資料存取層。 */
public interface ReferralBadgeRepository extends JpaRepository<ReferralBadge, Long> {
    boolean existsByReaderIdAndMilestone(Long readerId, int milestone);
    List<ReferralBadge> findByReaderIdOrderByMilestoneAsc(Long readerId);
}

