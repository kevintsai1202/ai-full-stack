package world.springai.survey.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** 問卷送出請求；website 為蜜罐欄位（正常使用者不應填寫） */
public class SurveyRequest {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @Size(max = 100)
    private String name;
    @Size(max = 50)
    private String role;
    @Size(max = 50)
    private String experience;
    @Size(max = 50)
    private String frontendExperience;
    /** 複選主題，限制數量避免濫用 */
    @Size(max = 20)
    private List<String> interest;
    @Size(max = 50)
    private String budget;
    private Map<String, String> utm;
    /** 行銷導向問題答案（如 pain_points），限制鍵數避免濫用 */
    @Size(max = 20)
    private Map<String, Object> answers;
    /** 必須為 true 才算同意（PDPA） */
    @AssertTrue
    private boolean consent;
    /** 蜜罐：以 CSS 隱藏，機器人才會填 */
    private String website;
    /**
     * 資料來源。刻意只是「呼叫端聲稱的值」，實際是否採信由 SurveyController 以白名單
     * 過濾（目前只接受 "newsletter"），避免任意呼叫端指定 survey_form 之外的值來
     * 汙染或偽造對外公開的問卷統計。
     */
    private String source;
    /**
     * 推薦碼（邀請連結的 ?ref= 值）。
     *
     * <p>不做格式驗證：推薦碼是否存在由 confirm 時查 reader 表決定，
     * 這裡收到亂碼只會導致查不到推薦人而不發獎，沒有安全影響。
     * 在此加 @Pattern 反而會讓亂改連結的人收到 400，訂閱直接失敗。</p>
     */
    private String ref;
    /**
     * 文章分享來源（文章 slug）。
     *
     * <p>只用於轉換歸因，不參與發獎判定；後端只會在同時帶有推薦碼且格式安全時採信，
     * 避免任意外部輸入被寫進系統欄位。</p>
     */
    private String share;

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
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getShare() { return share; }
    public void setShare(String share) { this.share = share; }
}
