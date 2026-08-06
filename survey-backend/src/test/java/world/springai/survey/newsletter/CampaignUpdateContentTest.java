package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import world.springai.survey.mail.EmailLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章內容更新：只動內容欄位，不碰計費、slug 與寄出的信件快照。
 *
 * <p><b>這份測試的能力邊界</b>：repository 是 mock，資料庫完全不存在，所以它證明得了
 * 「呼叫了什麼、順序為何」，證明不了「提交後資料庫裡是什麼」。封面被 Hibernate
 * 整列寫回覆蓋那個 Critical 就是在這一層看不見的——請見
 * {@code CampaignUpdateContentPersistenceTest}（連真實 PostgreSQL）。</p>
 */
class CampaignUpdateContentTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 應以「只寫三欄」的條件式 UPDATE 更新 subject/markdown/updated_at */
    @Test
    void updatesContentFields() {
        CampaignRepository repo = existingRepo();
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        newService(repo, metadata).updateContent(1L, "新標題", "新內文", "📮", null, List.of("AI"), NOW);

        verify(repo).updateContentFields(1L, "新標題", "新內文", NOW);
    }

    /**
     * <b>絕不可用 {@code save(entity)} 整列寫回</b>：同一交易內
     * {@code metadataService.update()} 是走 JdbcTemplate 原生 SQL 改封面（不觸發 flush），
     * 一旦這裡用了 save，提交時舊快照會把新封面還原回去而 API 仍回 updated:true。
     */
    @Test
    void neverSavesWholeEntity() {
        CampaignRepository repo = existingRepo();

        newService(repo, mock(CampaignMetadataService.class))
            .updateContent(1L, "新標題", "新內文", "📮", null, List.of("AI"), NOW);

        verify(repo, never()).save(any());
        verify(repo, never()).saveAll(any());
        // 連載入實體都不該做：被 Hibernate 管理的實體正是整列寫回的起點
        verify(repo, never()).findById(anyLong());
    }

    /** 封面與標籤必須交給既有的 metadata 服務，先驗證後更新 */
    @Test
    void delegatesCoverAndTagsToMetadataService() {
        CampaignRepository repo = existingRepo();
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        newService(repo, metadata).updateContent(1L, "標題", "內文", "📮", 7L, List.of("AI"), NOW);

        verify(metadata).validate("📮", List.of("AI"), 7L);
        verify(metadata).update(1L, "📮", List.of("AI"), 7L);
    }

    /**
     * 絕不可寫入 email_log——那代表信被重寄了一次。
     * 這是本端點與 reschedule 最關鍵的差異（spec §4.3、§6）。
     */
    @Test
    void neverWritesEmailLog() {
        CampaignRepository repo = existingRepo();
        EmailLogRepository emailLog = mock(EmailLogRepository.class);

        newServiceWithEmailLog(repo, emailLog)
            .updateContent(1L, "新標題", "新內文", null, null, List.of(), NOW);

        verify(emailLog, never()).save(any());
        verify(emailLog, never()).saveAll(any());
    }

    /**
     * campaign 不存在時應回 404，且不得呼叫 metadataService.update
     *（否則會對一篇不存在的文章寫入標籤）。
     */
    @Test
    void campaignNotFoundReturns404AndSkipsMetadataUpdate() {
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.existsById(99L)).thenReturn(false);
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> newService(repo, metadata)
                .updateContent(99L, "標題", "內文", null, null, List.of(), NOW));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(metadata, never()).update(anyLong(), any(), any(), any());
    }

    /**
     * 存在性檢查通過後、UPDATE 前該列被刪除（併發）時，受影響筆數為 0，仍須回 404，
     * 且不得寫入標籤——正確性來自受影響筆數，不是來自先前的檢查。
     */
    @Test
    void concurrentDeleteBetweenCheckAndUpdateReturns404() {
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.existsById(1L)).thenReturn(true);
        when(repo.updateContentFields(eq(1L), anyString(), anyString(), any())).thenReturn(0);
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> newService(repo, metadata)
                .updateContent(1L, "標題", "內文", null, null, List.of(), NOW));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(metadata, never()).update(anyLong(), any(), any(), any());
    }

    /**
     * metadataService.validate 失敗時不得寫入任何部分更新：validate 必須在
     * 內容欄位落庫之前執行，否則封面驗證失敗時會留下「標題已改、封面沒改」的半套更新
     *（測試用 mock repository 不會真的 rollback，鎖的是呼叫順序這個不變量）。
     */
    @Test
    void metadataValidationFailurePreventsPartialUpdate() {
        CampaignRepository repo = existingRepo();
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面不合法"))
            .when(metadata).validate(any(), any(), any());

        assertThrows(ResponseStatusException.class,
            () -> newService(repo, metadata)
                .updateContent(1L, "新標題", "新內文", "bad", null, List.of(), NOW));

        verify(repo, never()).updateContentFields(anyLong(), any(), any(), any());
    }

    /**
     * 主旨為 null／空白一律 400，不得落庫。
     *
     * <p>DB 是 {@code TEXT NOT NULL}：null 會在 flush 時炸成
     * {@code DataIntegrityViolationException} → 500（語意上應該是 400）；
     * 空字串則會通過 NOT NULL，把一篇<b>已發布</b>文章的主旨靜默清空。
     * 前端雖有擋，但這是可被直接呼叫的 admin API。</p>
     */
    @Test
    void blankSubjectIsRejectedBeforeAnyWrite() {
        for (String badSubject : new String[] {null, "", "   "}) {
            CampaignRepository repo = existingRepo();
            CampaignMetadataService metadata = mock(CampaignMetadataService.class);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> newService(repo, metadata)
                    .updateContent(1L, badSubject, "內文", null, null, List.of(), NOW),
                "主旨「" + badSubject + "」應被拒絕");

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(repo, never()).updateContentFields(anyLong(), any(), any(), any());
            verify(metadata, never()).update(anyLong(), any(), any(), any());
        }
    }

    /** 內文為 null／空白一律 400，理由同主旨（空字串＝把已發布文章清空） */
    @Test
    void blankMarkdownIsRejectedBeforeAnyWrite() {
        for (String badMarkdown : new String[] {null, "", "   "}) {
            CampaignRepository repo = existingRepo();
            CampaignMetadataService metadata = mock(CampaignMetadataService.class);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> newService(repo, metadata)
                    .updateContent(1L, "標題", badMarkdown, null, null, List.of(), NOW),
                "內文「" + badMarkdown + "」應被拒絕");

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(repo, never()).updateContentFields(anyLong(), any(), any(), any());
            verify(metadata, never()).update(anyLong(), any(), any(), any());
        }
    }

    /** 建一個「文章存在且 UPDATE 影響 1 列」的 repository mock，供多數案例共用 */
    private CampaignRepository existingRepo() {
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.existsById(1L)).thenReturn(true);
        when(repo.updateContentFields(eq(1L), anyString(), anyString(), any())).thenReturn(1);
        return repo;
    }

    /** 建一個只需要 repo 與 metadata 兩個依賴的 CampaignService，其餘依賴一律 mock */
    private CampaignService newService(CampaignRepository repo, CampaignMetadataService metadata) {
        return newServiceWithEmailLog(repo, mock(EmailLogRepository.class), metadata);
    }

    /** 與 newService 相同，但把 EmailLogRepository 換成傳入的 mock，供驗證未被使用 */
    private CampaignService newServiceWithEmailLog(CampaignRepository repo, EmailLogRepository emailLog) {
        return newServiceWithEmailLog(repo, emailLog, mock(CampaignMetadataService.class));
    }

    private CampaignService newServiceWithEmailLog(CampaignRepository repo, EmailLogRepository emailLog,
                                                    CampaignMetadataService metadata) {
        return new CampaignService(
            mock(world.springai.survey.mail.MailSender.class),
            mock(world.springai.survey.audience.RecipientService.class),
            repo,
            emailLog,
            mock(MarkdownRenderer.class),
            mock(world.springai.survey.mail.EmailTemplate.class),
            mock(world.springai.survey.audience.SubscriptionLinkBuilder.class),
            mock(world.springai.survey.mail.MailQuotaService.class),
            mock(ContentSplitter.class),
            mock(world.springai.survey.ReaderSiteLinks.class),
            mock(MailBodyRenderer.class),
            mock(world.springai.survey.promo.PromoPlacementService.class),
            mock(world.springai.survey.promo.PromoRecipientTokenService.class),
            mock(SurveyBlockRenderer.class),
            metadata);
    }
}
