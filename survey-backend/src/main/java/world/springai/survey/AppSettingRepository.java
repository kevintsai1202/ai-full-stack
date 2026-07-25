package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

/** 可調參數資料存取層；主鍵即參數鍵，故 findById 就是依鍵查詢 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
