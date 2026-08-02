package world.springai.survey.promo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 版位資料存取層 */
public interface PromoPlacementRepository extends JpaRepository<PromoPlacement, Long> {

    /** 某期電子報中特定狀態的版位列表（對帳、寄送組版時使用） */
    List<PromoPlacement> findByCampaignIdAndStatus(Long campaignId, String status);
}
