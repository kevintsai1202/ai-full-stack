package world.springai.survey.reader;

import org.springframework.stereotype.Component;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 由 session cookie 解析出「目前是誰、有沒有確認訂閱」。
 *
 * <p>抽成獨立元件的理由：這段邏輯被登入 API 與內容頁面共用，重複實作會讓兩邊
 * 對「訂閱」的判定有機會走偏。訂閱狀態一律取自名單中心（spec 原則 3），
 * 不從 reader 表推導。</p>
 */
@Component
public class ReaderContext {

    private final ReaderSessionService sessionService;
    private final ReaderRepository readerRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    /** 注入 session、讀者與名單中心 */
    public ReaderContext(ReaderSessionService sessionService,
                        ReaderRepository readerRepository,
                        SurveyResponseRepository surveyResponseRepository) {
        this.sessionService = sessionService;
        this.readerRepository = readerRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    /**
     * 目前的讀者狀態。
     *
     * @param reader     已登入的讀者
     * @param subscribed 是否為已確認訂閱者（來自名單中心）
     */
    public record Current(Reader reader, boolean subscribed) {}

    /** 解析 session cookie；無效或未登入回 empty */
    public Optional<Current> resolve(String sessionCookie) {
        return sessionService.readReaderId(sessionCookie, OffsetDateTime.now())
            .flatMap(readerRepository::findById)
            .map(reader -> new Current(reader, surveyResponseRepository.isSubscribed(reader.getEmail())));
    }
}
