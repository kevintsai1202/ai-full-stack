package world.springai.survey.promo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 點擊紀錄資料存取層 */
public interface PromoClickRepository extends JpaRepository<PromoClick, Long> {

    /** 每版位×通道的總點擊與唯一點擊（ANON 不進唯一）投影 */
    interface ChannelStat {
        Long getPlacementId();
        String getChannel();
        long getTotal();
        long getUniq();
    }

    /** 彙總指定版位的點擊統計；唯一點擊 = 非匿名身分去重 */
    @Query(value = "SELECT placement_id AS placementId, channel, COUNT(*) AS total, "
        + "COUNT(DISTINCT identity_type || ':' || identity_key) "
        + "FILTER (WHERE identity_type <> 'ANON') AS uniq "
        + "FROM promo_click WHERE placement_id IN (:ids) "
        + "GROUP BY placement_id, channel", nativeQuery = true)
    List<ChannelStat> statsForPlacements(@Param("ids") List<Long> ids);
}
