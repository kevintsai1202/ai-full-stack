package world.springai.survey.audience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 問卷回應實體，對應資料表 survey_response */
@Entity
@Table(name = "survey_response")
public class SurveyResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 樂觀鎖版本，避免日後整列更新靜默覆蓋同意狀態。 */
    @Version
    @Column(nullable = false)
    private long version;
    @Column(nullable = false)
    private String email;
    private String name;
    private String role;
    private String experience;
    /** 前端經驗區間 */
    @Column(name = "frontend_experience")
    private String frontendExperience;
    /** 複選主題，存成 jsonb 陣列 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> interest;
    private String budget;
    /** UTM 歸因，存成 jsonb 物件 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> utm;
    /** 行銷導向問題答案（痛點/急迫度/想學的系統知識…），存成 jsonb */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> answers;
    @Column(nullable = false)
    private boolean consent;
    @Column(nullable = false)
    private boolean unsubscribed = false;
    /** 名單來源：survey_form（問卷填寫）或 exam（線上測驗匯入）等 */
    @Column(nullable = false)
    private String source = "survey_form";
    /** 最後互動時間（確認訂閱／開信／登入／解鎖／改資料），供階段 F 的參與度分級使用 */
    @Column(name = "last_engaged_at")
    private OffsetDateTime lastEngagedAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getFrontendExperience() { return frontendExperience; }
    public void setFrontendExperience(String frontendExperience) { this.frontendExperience = frontendExperience; }
    public Map<String, Object> getAnswers() { return answers; }
    public void setAnswers(Map<String, Object> answers) { this.answers = answers; }
    public List<String> getInterest() { return interest; }
    public void setInterest(List<String> interest) { this.interest = interest; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public Map<String, String> getUtm() { return utm; }
    public void setUtm(Map<String, String> utm) { this.utm = utm; }
    public boolean isConsent() { return consent; }
    public void setConsent(boolean consent) { this.consent = consent; }
    public boolean isUnsubscribed() { return unsubscribed; }
    public void setUnsubscribed(boolean unsubscribed) { this.unsubscribed = unsubscribed; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public OffsetDateTime getLastEngagedAt() { return lastEngagedAt; }
    public void setLastEngagedAt(OffsetDateTime lastEngagedAt) { this.lastEngagedAt = lastEngagedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
