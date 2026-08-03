package world.springai.survey.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** {@link CouponCampaign} 的資料存取層 */
public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {

    /** 依建立時間新到舊排序取得所有活動，供後台活動列表使用 */
    List<CouponCampaign> findAllByOrderByCreatedAtDesc();
}
