package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 扣點解鎖：正常路徑、冪等、餘額不足、併發 */
class UnlockServiceTest {

    private static final long READER_ID = 3L;
    private static final long CAMPAIGN_ID = 42L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-01T10:00:00Z");

    private ReaderRepository readerRepository;
    private ArticleAccessRepository articleAccessRepository;
    private CreditTxnRepository creditTxnRepository;
    private SurveyResponseRepository surveyResponseRepository;
    private CreditPolicy creditPolicy;
    private UnlockService service;

    @BeforeEach
    void setUp() {
        readerRepository = mock(ReaderRepository.class);
        articleAccessRepository = mock(ArticleAccessRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.costOf(any())).thenReturn(10);
        service = new UnlockService(readerRepository, articleAccessRepository,
            creditTxnRepository, surveyResponseRepository, creditPolicy);
    }

    /** 建一篇已發布的 PREMIUM 文章 */
    private Campaign article() {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);
        c.setPublishedAt(NOW.minusDays(1));
        ReflectionTestUtils.setField(c, "id", CAMPAIGN_ID);
        return c;
    }

    /** 建一個指定餘額的讀者物件 */
    private Reader readerWithCredits(int credits) {
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        reader.setCredits(credits);
        return reader;
    }

    /**
     * 讓 findById 一律回傳同一個指定餘額的讀者物件。
     *
     * <p>unlock() 現在會呼叫兩次 findById（扣款前讀一次、扣款後重讀一次
     * 取得權威餘額），這裡兩次都回傳同一顆餘額不變的物件，模擬「扣款期間
     * 沒有其他交易介入」的一般情境。若要模擬扣款期間餘額被別的交易改動，
     * 請改用 {@code when(...).thenReturn(a, b)} 讓兩次呼叫回傳不同物件
     * （見 {@code returnedCreditsComeFromPostDeductionReread}）。</p>
     */
    private void givenReaderWithCredits(int credits) {
        when(readerRepository.findById(READER_ID)).thenReturn(Optional.of(readerWithCredits(credits)));
    }

    /**
     * 正常路徑：扣點、寫帳本、寫解鎖紀錄、更新參與度。
     *
     * <p>findById 被模擬成扣款前後兩次呼叫：第一次回傳扣款前的 300，
     * 第二次回傳資料庫實際扣款後的 290——對應真實環境中
     * {@code deductCredits} 的 {@code clearAutomatically = true} 讓第二次
     * 查詢真的重新命中資料庫，而不是回傳同一顆舊物件。</p>
     */
    @Test
    void unlocksAndDeductsCredits() {
        when(readerRepository.findById(READER_ID))
            .thenReturn(Optional.of(readerWithCredits(300)), Optional.of(readerWithCredits(290)));
        when(articleAccessRepository.existsByReaderIdAndCampaignId(READER_ID, CAMPAIGN_ID)).thenReturn(false);
        when(readerRepository.deductCredits(READER_ID, 10)).thenReturn(1);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.UNLOCKED, result.outcome());
        assertEquals(10, result.cost());
        assertEquals(290, result.credits());
        verify(readerRepository).deductCredits(READER_ID, 10);
        verify(articleAccessRepository).saveAndFlush(any(ArticleAccess.class));
        verify(creditTxnRepository).save(any(CreditTxn.class));
        verify(surveyResponseRepository).touchEngagement(anyString(), any());
    }

    /**
     * 回傳的餘額必須來自扣款後的重新查詢，不是扣款前快照的算術結果。
     *
     * <p>刻意讓 300 - 10 = 290（算術結果）與重新讀取的 250（模擬扣款期間
     * 有另一筆交易，例如同時到達的推薦獎勵加點，動過同一讀者的餘額）
     * 不同，這樣測試才能區分兩種實作：若把 UnlockService 改回
     * {@code reader.getCredits() - cost} 的記憶體算術，這裡會斷言失敗
     * （回傳 290 而非 250）。</p>
     */
    @Test
    void returnedCreditsComeFromPostDeductionReread() {
        when(readerRepository.findById(READER_ID))
            .thenReturn(Optional.of(readerWithCredits(300)), Optional.of(readerWithCredits(250)));
        when(articleAccessRepository.existsByReaderIdAndCampaignId(READER_ID, CAMPAIGN_ID)).thenReturn(false);
        when(readerRepository.deductCredits(READER_ID, 10)).thenReturn(1);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.UNLOCKED, result.outcome());
        assertEquals(250, result.credits());
    }

    /**
     * 扣款必須發生在寫入解鎖紀錄之前。
     *
     * <p>反過來的順序（先插入 article_access 再扣款）在扣款失敗時，
     * 會留下「有解鎖紀錄但沒扣點」的永久免費解鎖——而 article_access
     * 同時是 ALREADY_UNLOCKED 的判斷來源，這個狀態無法自我修復。</p>
     */
    @Test
    void deductsBeforeWritingAccessRecord() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        service.unlock(READER_ID, article(), NOW);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(readerRepository, articleAccessRepository);
        inOrder.verify(readerRepository).deductCredits(READER_ID, 10);
        inOrder.verify(articleAccessRepository).saveAndFlush(any(ArticleAccess.class));
    }

    /** 帳本的 delta 必須是負數，且帶上 campaign_id 供對帳 */
    @Test
    void ledgerRecordsNegativeDeltaWithCampaignId() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        service.unlock(READER_ID, article(), NOW);

        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(captor.capture());
        assertEquals(-10, captor.getValue().getDelta());
        assertEquals(CreditTxn.REASON_READ, captor.getValue().getReason());
        assertEquals(CAMPAIGN_ID, captor.getValue().getCampaignId());
    }

    /** 解鎖紀錄要記下當時實扣點數，日後調參數不影響已解鎖的歷史成本 */
    @Test
    void accessRecordStoresActualCost() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        service.unlock(READER_ID, article(), NOW);

        ArgumentCaptor<ArticleAccess> captor = ArgumentCaptor.forClass(ArticleAccess.class);
        verify(articleAccessRepository).saveAndFlush(captor.capture());
        assertEquals(10, captor.getValue().getCost());
        assertEquals(CAMPAIGN_ID, captor.getValue().getCampaignId());
    }

    /** 已解鎖過就不再扣點，且完全不寫入任何東西 */
    @Test
    void alreadyUnlockedDeductsNothing() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(READER_ID, CAMPAIGN_ID)).thenReturn(true);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.ALREADY_UNLOCKED, result.outcome());
        assertEquals(300, result.credits());
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(articleAccessRepository, never()).saveAndFlush(any());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 餘額不足時不寫入任何東西，並回報還差幾點所需的目前餘額 */
    @Test
    void insufficientCreditsWritesNothing() {
        givenReaderWithCredits(3);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.INSUFFICIENT_CREDITS, result.outcome());
        assertEquals(3, result.credits());
        assertEquals(10, result.cost());
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /**
     * 條件式扣款回 0 列（併發下餘額已被扣走）必須拋例外讓交易回滾。
     *
     * <p>不可回報 INSUFFICIENT_CREDITS 了事：此時餘額檢查已經通過，
     * 回 0 列代表在檢查與扣款之間有另一筆交易扣走了點數。若靜默處理，
     * 呼叫端會以為是普通的餘額不足，而真正的問題（同一讀者的併發解鎖）
     * 就被藏起來了。</p>
     */
    @Test
    void concurrentDeductionFailureThrows() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, article(), NOW));
        verify(creditTxnRepository, never()).save(any());
    }

    /**
     * UNIQUE 撞擊必須往外拋，不可在本方法內吞掉。
     *
     * <p>這是 Spring 交易語意的陷阱：`saveAndFlush` 觸發
     * DataIntegrityViolationException 時，交易已被標記為 rollback-only。
     * 若在 @Transactional 方法內捕捉並正常回傳 ALREADY_UNLOCKED，
     * commit 階段會改拋 UnexpectedRollbackException——呼叫端收到的是
     * 一個看起來毫無關聯的錯誤。正確做法是讓它往外拋，由交易邊界
     * <b>之外</b>的 controller 判讀（見 Task 7）。</p>
     */
    @Test
    void uniqueViolationPropagatesInsteadOfBeingSwallowed() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);
        when(articleAccessRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("uq_article_access"));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.unlock(READER_ID, article(), NOW));
    }

    /** 讀者不存在時拋例外（session 有效但帳戶被刪，屬異常狀態） */
    @Test
    void unknownReaderThrows() {
        when(readerRepository.findById(READER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, article(), NOW));
    }

    /**
     * 未發布的文章不可解鎖。
     *
     * <p>授權判斷在 AccessDecisionService，但扣點是不可逆的寫入，
     * 不該完全信任呼叫端已經判斷過——草稿被解鎖會讓讀者付了點數卻
     * 看到未完成的內容，而點數已經扣掉了。</p>
     */
    @Test
    void unpublishedArticleCannotBeUnlocked() {
        givenReaderWithCredits(300);
        Campaign draft = article();
        draft.setPublishedAt(null);

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, draft, NOW));
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
    }

    /**
     * BASIC 文章不可解鎖。
     *
     * <p>BASIC 對訂閱者本來就免費，對它扣點是純粹的損失。這也是
     * fail-closed 的方向：只有精確等於 PREMIUM 才允許扣點解鎖，
     * tier 打錯字時寧可拒絕解鎖（讀者看得到免費區、可回報問題），
     * 也不要對一個判斷不明的文章扣點。</p>
     */
    @Test
    void basicArticleCannotBeUnlocked() {
        givenReaderWithCredits(300);
        Campaign basic = article();
        basic.setTier(Campaign.TIER_BASIC);

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, basic, NOW));
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
    }

    /**
     * 非精確 PREMIUM 的 tier（打錯字、大小寫錯誤、多餘空白）一律拒絕解鎖。
     *
     * <p>擋的判斷式是「{@code tier} 不精確等於 {@code PREMIUM}」，不是
     * 「{@code tier} 等於 {@code BASIC}」——這兩者只在合法值集合
     * {BASIC, PREMIUM} 內等價。若實作被誤寫成後者（只擋 BASIC、放行任何
     * 非 BASIC 值），{@link #basicArticleCannotBeUnlocked} 仍然是綠的，
     * 因為它從未測過 BASIC 以外的異常值——真正暴露這個方向錯誤的正是
     * 這裡的小寫 "premium"。這與階段 B 抓到的一個 Critical 同源：當時
     * {@code !campaign.isPremium()} 讓小寫 tier 的文章被誤判為 BASIC
     * 而全文外洩。方向是 fail-closed：tier 判斷不明時寧可拒絕解鎖。</p>
     */
    @Test
    void unknownTierCannotBeUnlocked() {
        givenReaderWithCredits(300);
        Campaign unknownTier = article();
        unknownTier.setTier("premium");

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, unknownTier, NOW));
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
    }
}
