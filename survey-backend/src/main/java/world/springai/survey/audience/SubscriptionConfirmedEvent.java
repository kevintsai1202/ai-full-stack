package world.springai.survey.audience;

/**
 * 確認訂閱成功事件。
 *
 * <p><b>為什麼需要事件而不是直接呼叫</b>：確認訂閱成功時要發放推薦獎勵，
 * 而獎勵屬於 {@code reader}（點數帳本）。但 spec §3 規定 {@code audience}
 * 是下層，不得依賴 {@code reader}——直接呼叫會讓 {@code PackageDependencyTest}
 * 變紅，而那條規則存在的理由（拆服務時的拆解線）是真的。
 * 事件讓 {@code audience} 只宣告「發生了什麼」，不需要知道有誰在乎。</p>
 *
 * @param email 已確認訂閱者的 email，<b>已正規化為小寫並去除前後空白</b>。
 *              正規化在發布端完成，訂閱端不必各自處理——否則每個監聽器
 *              都要記得正規化，忘記一次就是查不到人而靜默不發獎。
 */
public record SubscriptionConfirmedEvent(String email) {
}
