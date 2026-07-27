package world.springai.survey.media;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 文章媒體中繼資料存取。 */
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /** 內容雜湊去重。 */
    Optional<MediaAsset> findBySha256(String sha256);

    /** 媒體庫依建立時間新到舊列出。 */
    List<MediaAsset> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
