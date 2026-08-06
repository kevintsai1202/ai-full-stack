package world.springai.survey.reader;

import java.util.List;

/**
 * Reader 漏斗的語意正確結構（D7）：
 * 原始的六個事件計數之間沒有包含關係——articleViews 與 subscriptionHomeViews 是平行入口，
 * 訂閱與解鎖是兩條不同路徑；並排成單一漏斗必然倒掛。本 view 把頂端定義為平行入口的加總，
 * 其下拆成兩條各自遞減的鏈。原始計數在 dashboard 回應中原樣保留（readerFunnel key），
 * 供新舊數值並存驗證。
 */
public record ReaderFunnelView(long totalViews, List<Step> subscribePath, List<Step> unlockPath) {

    /** 漏斗單層：key 供前端程式化比對、label 顯示、count 去重訪客數 */
    public record Step(String key, String label, long count) {}

    /** 由六個原始事件計數組裝語意正確的結構；純函數，方便單測鎖住加總語意 */
    public static ReaderFunnelView from(long articleViews, long subscriptionHomeViews,
            long subscribeAttempts, long subscribeSuccess, long unlockClicks, long unlockSuccess) {
        return new ReaderFunnelView(
            articleViews + subscriptionHomeViews,
            List.of(new Step("subscribeAttempts", "送出訂閱", subscribeAttempts),
                    new Step("subscribeSuccess", "訂閱成功", subscribeSuccess)),
            List.of(new Step("unlockClicks", "點選解鎖", unlockClicks),
                    new Step("unlockSuccess", "解鎖成功", unlockSuccess)));
    }
}
