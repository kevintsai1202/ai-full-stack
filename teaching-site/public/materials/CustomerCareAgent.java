// CustomerCareAgent.java — u-3 案例程式骨架（提示詞③的期望輸出範例）
// 用途：對照 AI 產出的骨架；也可作為提示詞④（執行軌跡模擬）的輸入。
// 注意：套件與 import 依實際專案調整；LLM 步驟的 prompt 細節見 u-6。

record CustomerQuery(Long customerId, String question) {}

record Trip(String from, String to, Instant departure, Instant arrival, float amount) {}

record TravellerActivity(String name, Instant from, Instant to, List<Trip> trips) {

    @Tool // 由 Java 計算，LLM 不心算
    public float totalSpend() {
        return trips.stream().map(Trip::amount).reduce(0f, Float::sum);
    }

    @Tool(description = "Trips per year")
    public float tripsPerYear() {
        long days = Duration.between(from, to).toDays();
        return days == 0 ? trips.size() : (trips.size() * 365f) / days;
    }
}

record ActivitySummary(String text, boolean highSpender, boolean frequentTraveler) {}

record OfferDraft(String offer, String rationale) {}

record ReviewedOffer(String offer, String approvedBy) {}

@Agent(description = "客戶活動摘要與個人化方案")
public class CustomerCareAgent {

    @Action // CustomerQuery → TravellerActivity（純程式，呼叫既有報表服務）
    TravellerActivity fetchActivity(CustomerQuery query, TravelActivityReportingService svc) {
        return svc.report(query.customerId());
    }

    @Action // TravellerActivity → ActivitySummary（LLM 摘要，最小工具暴露）
    ActivitySummary summarize(TravellerActivity activity, Ai ai) {
        // ai.withDefaultLlm().withToolObject(activity).generateText(...)
        throw new UnsupportedOperationException("課堂實作");
    }

    @Action // ActivitySummary → OfferDraft（LLM 產生方案草稿）
    OfferDraft proposeOffer(ActivitySummary summary, Ai ai) {
        throw new UnsupportedOperationException("課堂實作");
    }

    @AchievesGoal(description = "產出可發送的個人化方案")
    @Action // OfferDraft → ReviewedOffer（規則檢查或強模型審核）
    ReviewedOffer reviewOffer(OfferDraft draft) {
        throw new UnsupportedOperationException("課堂實作");
    }
}
