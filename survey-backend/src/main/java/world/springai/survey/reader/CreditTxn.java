package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 點數帳本實體，對應資料表 credit_txn。
 *
 * <p><b>只增不改不刪</b>：reader.credits 永遠可由本表重算稽核。這也是
 * 「規則調整不扣減既有點數餘額」這個對外承諾能成立的原因——調整參數只影響
 * 未來的扣點金額，不回溯既有交易。</p>
 */
@Entity
@Table(name = "credit_txn")
public class CreditTxn {

    /** 首次登入的初始贈點 */
    public static final String REASON_SIGNUP_GRANT = "SIGNUP_GRANT";
    /** 邀請成功獎勵（被邀者點確認信後才發） */
    public static final String REASON_REFERRAL = "REFERRAL";
    /** 被邀者完成確認後的雙邊加碼。 */
    public static final String REASON_REFERRAL_INVITEE = "REFERRAL_INVITEE";
    /** 推薦人達成 3／5／10 人里程碑的額外獎勵。 */
    public static final String REASON_REFERRAL_MILESTONE = "REFERRAL_MILESTONE";
    /** 閱讀進階文章扣點 */
    public static final String REASON_READ = "READ";
    /** 後台手動加點（如贈與上課學員） */
    public static final String REASON_ADMIN_GRANT = "ADMIN_GRANT";
    /** 工商提案申請扣點（負向） */
    public static final String REASON_PROMO_APPLY = "PROMO_APPLY";
    /** 工商提案退點：被拒全退、封存退未投放餘額（正向） */
    public static final String REASON_PROMO_REFUND = "PROMO_REFUND";
    /** 問卷填答發點（正向）；防重發唯一鍵由 (reader_id, survey_form_key) 組成 */
    public static final String REASON_SURVEY_REWARD = "SURVEY_REWARD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬讀者 */
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    /** 點數變動：正數加點、負數扣點 */
    @Column(nullable = false)
    private int delta;

    /** 交易原因，取本類的 REASON_* 常數 */
    @Column(nullable = false)
    private String reason;

    /** reason=READ 時對應的文章 */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** reason=PROMO_APPLY／PROMO_REFUND 時對應的提案；退點冪等判斷依據 */
    @Column(name = "promo_proposal_id")
    private Long promoProposalId;

    /** reason=SURVEY_REWARD 時對應的問卷；發點對象問卷，防重發唯一鍵成分 */
    @Column(name = "survey_form_key")
    private String surveyFormKey;

    /** 說明文字，ADMIN_GRANT 時記錄贈點理由 */
    private String note;

    /** 建立時間，由資料庫維護 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected CreditTxn() {
    }

    /** 建立一筆點數交易 */
    public CreditTxn(Long readerId, int delta, String reason, Long campaignId, String note) {
        this.readerId = readerId;
        this.delta = delta;
        this.reason = reason;
        this.campaignId = campaignId;
        this.note = note;
    }

    /** 建立一筆點數交易（工商提案路徑，帶提案 id） */
    public CreditTxn(Long readerId, int delta, String reason, Long campaignId,
                     String note, Long promoProposalId) {
        this(readerId, delta, reason, campaignId, note);
        this.promoProposalId = promoProposalId;
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public int getDelta() { return delta; }
    public String getReason() { return reason; }
    public Long getCampaignId() { return campaignId; }
    public Long getPromoProposalId() { return promoProposalId; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getSurveyFormKey() { return surveyFormKey; }

    /** 設定發點對象問卷（reason=SURVEY_REWARD 時使用） */
    public void setSurveyFormKey(String surveyFormKey) { this.surveyFormKey = surveyFormKey; }
}
