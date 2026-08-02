package world.springai.survey.promo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import world.springai.survey.newsletter.ContentSplitter;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * I5 修正：對帳時的兩項「僅警告不擋寄送」健檢（spec §6.6）——
 * 版位連結只出現在受限區、promo 標記於 paywall 兩側未成對。
 *
 * <p>用 logback 的 {@link ListAppender} 攔截紀錄而不解析 stdout：
 * 比照 {@code ReferralRewardListenerTest} 的既有模式，不依賴 log pattern 格式。</p>
 */
class PromoPlacementServiceReconcileWarningTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final PromoPlacementService service = new PromoPlacementService(
        placementRepository, proposalRepository, "https://example.org", new ContentSplitter());

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        when(placementRepository.findByCampaignIdAndStatus(anyLong(), anyString()))
            .thenReturn(List.of());
        when(placementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proposalRepository.consumeQuota(anyLong())).thenReturn(1);

        serviceLogger = (Logger) LoggerFactory.getLogger(PromoPlacementService.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        // 一定要卸下：appender 掛在共用的 logger 上，留著會汙染同一個 JVM 的其他測試
        serviceLogger.detachAppender(appender);
        appender.stop();
    }

    /** 建一個 DRAFT 版位 mock，campaign_id 尚未綁定 */
    private void draftPlacement(long id, long proposalId) {
        PromoPlacement pl = new PromoPlacement(proposalId);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", id);
        when(placementRepository.findById(id)).thenReturn(Optional.of(pl));
    }

    private void proposal(long id, String title) {
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", title,
            "文", "看", "https://example.com", 3, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        p.setStatus(PromoProposal.STATUS_APPROVED);
        when(proposalRepository.findById(id)).thenReturn(Optional.of(p));
    }

    /** 版位連結只出現在受限區（付費牆之後）應觸發 warn，訊息含提案名稱與 placementId */
    @Test
    void 版位只出現在受限區時觸發警告() {
        draftPlacement(55L, 9L);
        proposal(9L, "好課方案");

        String markdown = "免費導讀\n\n<!--paywall-->\n\n"
            + "<!--promo-->\n文案\n\n[看](/promo/c/55?rt=x)\n<!--/promo-->\n";
        service.reconcile(100L, markdown);

        List<ILoggingEvent> warnings = appender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .toList();
        assertTrue(warnings.stream().anyMatch(e ->
            e.getFormattedMessage().contains("好課方案") && e.getFormattedMessage().contains("55")),
            "應警告受限區版位，實際紀錄：" + warnings);
    }

    /** 版位連結出現在免費區（或無付費牆）不應觸發受限區警告 */
    @Test
    void 版位出現在免費區不觸發受限區警告() {
        draftPlacement(55L, 9L);
        proposal(9L, "好課方案");

        String markdown = "<!--promo-->\n文案\n\n[看](/promo/c/55?rt=x)\n<!--/promo-->\n"
            + "\n<!--paywall-->\n\n付費內容";
        service.reconcile(100L, markdown);

        assertFalse(appender.list.stream().anyMatch(e ->
            e.getLevel() == Level.WARN && e.getFormattedMessage().contains("受限區")),
            "版位在免費區不應被判定為僅受限區可見：" + appender.list);
    }

    /** promo 標記在某一側未成對（例如漏了結尾標記）應觸發 warn，但不擋寄送 */
    @Test
    void promo標記未成對時觸發警告但不擋寄送() {
        draftPlacement(55L, 9L);

        // 免費區只有開頭標記，缺結尾——不成對
        String markdown = "<!--promo-->\n文案\n\n[看](/promo/c/55?rt=x)\n";
        service.reconcile(100L, markdown);

        assertTrue(appender.list.stream().anyMatch(e ->
            e.getLevel() == Level.WARN && e.getFormattedMessage().contains("未成對")),
            "應警告標記未成對：" + appender.list);
    }

    /** 標記於兩側皆成對時不應觸發成對警告 */
    @Test
    void promo標記成對時不觸發警告() {
        draftPlacement(55L, 9L);
        draftPlacement(66L, 10L);

        String markdown = "<!--promo-->\n文案1\n\n[看](/promo/c/55?rt=x)\n<!--/promo-->\n"
            + "\n<!--paywall-->\n\n"
            + "<!--promo-->\n文案2\n\n[看](/promo/c/66?rt=x)\n<!--/promo-->\n";
        service.reconcile(100L, markdown);

        assertFalse(appender.list.stream().anyMatch(e ->
            e.getLevel() == Level.WARN && e.getFormattedMessage().contains("未成對")),
            "標記成對時不應警告：" + appender.list);
        // 兩個版位都各自完整落在單一側（免費區／受限區各一），55 在免費區不該被判定「僅受限區」
        assertEquals(1, appender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN
                && e.getFormattedMessage().contains("受限區"))
            .count(),
            "只有 66 落在受限區應觸發警告：" + appender.list);
    }
}
