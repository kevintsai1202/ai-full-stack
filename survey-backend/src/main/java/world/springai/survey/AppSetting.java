package world.springai.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 可調參數實體，對應資料表 app_setting；點數與門檻類參數存 DB 以便後台改完立即生效 */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    /** 參數鍵，如 credit.signup_grant */
    @Id
    @Column(name = "setting_key")
    private String settingKey;

    /** 參數值，一律以字串存放，由讀取端依需要轉型 */
    @Column(nullable = false)
    private String value;

    /** 最後更新時間，由資料庫維護 */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** JPA 需要的無參數建構子 */
    protected AppSetting() {
    }

    /** 以鍵值建立一筆參數 */
    public AppSetting(String settingKey, String value) {
        this.settingKey = settingKey;
        this.value = value;
    }

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
