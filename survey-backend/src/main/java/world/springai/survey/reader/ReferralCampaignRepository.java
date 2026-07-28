package world.springai.survey.reader;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 限時邀請活動資料存取層。 */
public interface ReferralCampaignRepository extends JpaRepository<ReferralCampaign, Long> {

    List<ReferralCampaign> findAllByOrderByStartsAtDesc();

    /** 目前進行中的活動，後續再依文章或 hashtag 判斷是否命中。 */
    @Query("select c from ReferralCampaign c where c.active = true "
        + "and c.startsAt <= :now and c.endsAt > :now order by c.multiplier desc")
    List<ReferralCampaign> findActiveAt(@Param("now") OffsetDateTime now);

    /** 判斷文章是否具有活動指定的 hashtag。 */
    @Query(value = """
        select count(*) > 0
          from campaign c
          join campaign_tag ct on ct.campaign_id = c.id
          join content_tag t on t.id = ct.tag_id
         where c.slug = :articleSlug and t.slug = :tagSlug
        """, nativeQuery = true)
    boolean articleHasTag(@Param("articleSlug") String articleSlug,
                          @Param("tagSlug") String tagSlug);
}

