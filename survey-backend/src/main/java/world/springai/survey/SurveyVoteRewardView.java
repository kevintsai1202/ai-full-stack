package world.springai.survey;

/**
 * 問卷卡片顯示投票獎勵所需的唯一取值來源。
 *
 * <p><b>為什麼需要這個介面</b>：問卷卡片渲染器住在 {@code newsletter} 套件，
 * 而點數規則住在 {@code reader} 套件的 {@code CreditPolicy}——{@code
 * PackageDependencyTest.newsletterMustNotDependOnReader} 明文禁止
 * {@code newsletter → reader}（會形成上層循環）。把介面放在根套件、由
 * {@code CreditPolicy} 實作，渲染器只認介面，依賴方向就變成
 * {@code newsletter → 根套件 ← reader}，同時保住「點數數字只有 CreditPolicy
 * 一個來源」這條規則（下限保護不會因為繞道 AppSettingService 而漏掉）。
 * 作法比照 {@link ReaderSiteLinks} 這個既有的根套件共用型別。</p>
 */
public interface SurveyVoteRewardView {

    /** 一鍵投票的獎勵點數；0 表示後台已關閉投票發點 */
    int surveyVoteReward();
}
