package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** 分享點擊資料存取層。 */
public interface ReferralClickRepository extends JpaRepository<ReferralClick, Long> {

    /** 熱門文章的唯一點擊彙總，限制前 20 名供後台顯示。 */
    @Query(value = """
        select coalesce(source_slug, '(一般邀請連結)') as source, count(*) as clicks
          from referral_click
         group by source_slug
         order by count(*) desc
         limit 20
        """, nativeQuery = true)
    List<Object[]> topSources();
}

