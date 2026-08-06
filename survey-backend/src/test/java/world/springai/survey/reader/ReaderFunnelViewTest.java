package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ReaderFunnelView 的語意鎖定測試（D7）：頂端加總平行入口、兩條路徑各自成鏈。 */
class ReaderFunnelViewTest {

    /** D7：頂端為平行入口（文章瀏覽＋訂閱首頁瀏覽）的加總；兩條路徑各自成鏈 */
    @Test
    void topLayerSumsParallelEntries() {
        ReaderFunnelView view = ReaderFunnelView.from(100, 20, 30, 22, 15, 11);
        assertEquals(120, view.totalViews());
        assertEquals(List.of(
            new ReaderFunnelView.Step("subscribeAttempts", "送出訂閱", 30),
            new ReaderFunnelView.Step("subscribeSuccess", "訂閱成功", 22)), view.subscribePath());
        assertEquals(List.of(
            new ReaderFunnelView.Step("unlockClicks", "點選解鎖", 15),
            new ReaderFunnelView.Step("unlockSuccess", "解鎖成功", 11)), view.unlockPath());
    }
}
