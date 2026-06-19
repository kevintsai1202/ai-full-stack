package world.springai.survey;

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
    /** 複選主題，限制數量避免濫用 */
    @Size(max = 20)
    private List<String> interest;
    @Size(max = 50)
    private String budget;
    private Map<String, String> utm;
    /** 必須為 true 才算同意（PDPA） */
    @AssertTrue
    private boolean consent;
    /** 蜜罐：以 CSS 隱藏，機器人才會填 */
    private String website;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
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
}
