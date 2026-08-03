package world.springai.survey.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.newsletter.CampaignRepository;
import world.springai.survey.promo.PromoRecipientTokenService;
import world.springai.survey.reader.ReaderSessionService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * {@link SurveyVoteService} 純 Mockito 單元測試：驗證投票目標合法性、
 * 身分歸戶優先序（RECIPIENT > READER > ANON）與具名一人一票 upsert，
 * 照 {@code PromoClickServiceTest} 模式，全 mock、不需 DB。
 */
class SurveyVoteServiceTest {

    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);
    private final SurveyVoteRepository voteRepository = mock(SurveyVoteRepository.class);
    private final PromoRecipientTokenService tokenService = mock(PromoRecipientTokenService.class);
    private final ReaderSessionService sessionService = mock(ReaderSessionService.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private SurveyVoteService service;

    @BeforeEach
    void setUp() {
        service = new SurveyVoteService(
            formSchemaService, voteRepository, tokenService, sessionService, campaignRepository);
        // 預設：無 token、無 session、campaign 存在、查無既有投票列——各測試按需覆寫
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(any(), any())).thenReturn(Optional.empty());
        when(campaignRepository.existsById(any())).thenReturn(true);
        when(voteRepository.findByFormKeyAndIdentityTypeAndIdentityKey(any(), any(), any()))
            .thenReturn(Optional.empty());
    }

    /** 準備信中一鍵題：formKey/fieldKey 固定搭配，options 依序對應 optionIndex */
    private void givenQuestion(String formKey, String fieldKey, List<String> options) {
        when(formSchemaService.emailVoteQuestion(formKey)).thenReturn(
            Optional.of(new FormSchemaService.EmailVoteQuestion(formKey, "標題", fieldKey, "標籤", options)));
    }

    @Test
    void 合法投票_RECIPIENT歸戶_upsert改票() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        when(campaignRepository.existsById(9L)).thenReturn(true);
        // 第一次：insert；同時驗證 redirect 字串完整內容（有 c 有 rt）
        assertEquals(Optional.of("/r/survey/reader-poll?voted=0&c=9&rt=tok"),
            service.vote("reader-poll", "rating", 0, 9L, "tok", null));
        verify(voteRepository).save(argThat(v -> "很有幫助".equals(v.getOptionValue())
            && SurveyVote.IDENTITY_RECIPIENT.equals(v.getIdentityType())));
        // 第二次同身分：改票（先查到既有列→setOptionValue 再 save）
        SurveyVote existing = new SurveyVote("reader-poll", "rating", "很有幫助", 9L,
            SurveyVote.CHANNEL_EMAIL, SurveyVote.IDENTITY_RECIPIENT, "a@b.c");
        when(voteRepository.findByFormKeyAndIdentityTypeAndIdentityKey(
            "reader-poll", SurveyVote.IDENTITY_RECIPIENT, "a@b.c")).thenReturn(Optional.of(existing));
        service.vote("reader-poll", "rating", 2, 9L, "tok", null);
        assertEquals("沒幫助", existing.getOptionValue());
    }

    @Test
    void 問卷未發布回empty不落票() {
        when(formSchemaService.emailVoteQuestion("ghost")).thenReturn(Optional.empty());
        assertTrue(service.vote("ghost", "rating", 0, null, null, null).isEmpty());
        verify(voteRepository, never()).save(any());
    }

    /** 問卷存在但呼叫端傳的 fieldKey 不等於該問卷綁定的信中一鍵題欄位——同樣視為目標不合法 */
    @Test
    void 問卷存在但fieldKey不符信中題回empty不落票() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        assertTrue(service.vote("reader-poll", "wrong-field", 0, null, null, null).isEmpty());
        verify(voteRepository, never()).save(any());
    }

    @Test
    void optionIndex超界回empty() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        assertTrue(service.vote("reader-poll", "rating", 3, null, null, null).isEmpty());
        verify(voteRepository, never()).save(any());
    }

    /** Minor：負數 optionIndex 同樣是超界，須與正數超界一致回 empty 不落票 */
    @Test
    void optionIndex為負數回empty() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        assertTrue(service.vote("reader-poll", "rating", -1, null, null, null).isEmpty());
        verify(voteRepository, never()).save(any());
    }

    @Test
    void campaign不存在照轉址但不落票() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        when(campaignRepository.existsById(0L)).thenReturn(false);
        assertTrue(service.vote("reader-poll", "rating", 1, 0L, null, null).isPresent()); // 轉址照給
        verify(voteRepository, never()).save(any()); // 涵蓋測試信 c=0
    }

    @Test
    void 匿名insert不查重() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        // 無 c 無 rt：redirect 字串完整內容驗證
        assertEquals(Optional.of("/r/survey/reader-poll?voted=0"),
            service.vote("reader-poll", "rating", 0, null, null, null));
        verify(voteRepository).save(argThat(v -> SurveyVote.IDENTITY_ANON.equals(v.getIdentityType())
            && v.getIdentityKey() == null));
        verify(voteRepository, never()).findByFormKeyAndIdentityTypeAndIdentityKey(any(), any(), any());
    }

    @Test
    void 落票DB失敗不擋轉址() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        when(voteRepository.save(any())).thenThrow(new RuntimeException("db down"));
        assertTrue(service.vote("reader-poll", "rating", 0, null, null, null).isPresent());
    }

    /**
     * M1 修正：改票時必須同步更新 fieldKey。既有列可能是「信中一鍵題改綁到不同欄位」
     * 前留下的舊列（同一 formKey + identityType + identityKey，但 fieldKey 是舊的），
     * 若 upsert 只改 optionValue 不改 fieldKey，會留下一列 optionValue 屬於新欄位、
     * fieldKey 卻仍指向舊欄位的自相矛盾資料。
     */
    @Test
    void 改票時同步更新fieldKey_防信中題改綁後留自相矛盾列() {
        givenQuestion("reader-poll", "new-field", List.of("A", "B"));
        when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
        SurveyVote existing = new SurveyVote("reader-poll", "old-field", "A", 9L,
            SurveyVote.CHANNEL_EMAIL, SurveyVote.IDENTITY_RECIPIENT, "a@b.c");
        when(voteRepository.findByFormKeyAndIdentityTypeAndIdentityKey(
            "reader-poll", SurveyVote.IDENTITY_RECIPIENT, "a@b.c")).thenReturn(Optional.of(existing));

        service.vote("reader-poll", "new-field", 0, 9L, "tok", null);

        assertEquals("new-field", existing.getFieldKey(),
            "改票時 fieldKey 必須同步更新為目前信中一鍵題綁定的欄位");
    }

    /**
     * M2 修正：redirect 字串組裝時 rt 須經 URL 編碼，防止 rt 帶入 CR/LF 或
     * {@code &}/{@code =} 等特殊字元時破壞查詢字串結構（甚至注入額外參數或
     * header）。挑一個含 {@code &} 與 {@code =} 的病態值驗證編碼後不變成裸字元；
     * 既有測試中的正常 token（純英數字）編碼前後不變，不受影響。
     */
    @Test
    void rt含特殊字元時進行URL編碼防止注入() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通"));

        Optional<String> result =
            service.vote("reader-poll", "rating", 0, null, "tok&x=1", null);

        assertEquals(Optional.of("/r/survey/reader-poll?voted=0&rt=tok%26x%3D1"), result);
    }

    /**
     * 前置審查交辦（Task 1 審查者）：rt 驗證回病態的 {@code Optional.of("")}（空字串但存在）時，
     * identityKey 為 blank。NULL 對唯一索引不生效（多個 NULL 視為互不相等），若讓空字串當作
     * 具名 identityKey 寫入則不受此限制、逃過一票限制，必須在此降級為 ANON 落票。
     */
    @Test
    void rt驗證回空字串時降級為ANON落票() {
        givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
        when(tokenService.verify("tok")).thenReturn(Optional.of(""));
        assertTrue(service.vote("reader-poll", "rating", 0, null, "tok", null).isPresent());
        verify(voteRepository).save(argThat(v -> SurveyVote.IDENTITY_ANON.equals(v.getIdentityType())
            && v.getIdentityKey() == null));
        verify(voteRepository, never()).findByFormKeyAndIdentityTypeAndIdentityKey(any(), any(), any());
    }
}
