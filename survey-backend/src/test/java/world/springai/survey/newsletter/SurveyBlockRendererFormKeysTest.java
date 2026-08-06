package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;
import world.springai.survey.form.FormSchemaService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * {@link SurveyBlockRenderer#embeddedFormKeys(String)} 單元測試：驗證依內文出現順序、
 * 去重列出所有內嵌問卷標記的 formKey，供文章側邊欄投票統計（Task 7 / B1）決定要列哪些卡。
 */
class SurveyBlockRendererFormKeysTest {

    /** 建構方式沿用 SurveyBlockRendererTest：純 Mockito mock，不需 DB */
    private final FormSchemaService formSchemaService = mock(FormSchemaService.class);
    private final world.springai.survey.SurveyVoteRewardView rewardView =
        mock(world.springai.survey.SurveyVoteRewardView.class);
    private final SurveyBlockRenderer renderer = new SurveyBlockRenderer(formSchemaService, rewardView);

    /** 依內文出現順序列出全部內嵌問卷 key；重複標記去重保留首次；無標記回空 */
    @Test
    void listsMarkersInDocumentOrder() {
        String html = "<p>a</p><!--survey:form-b--><p>b</p><!--survey:form-a--><!--survey:form-b-->";
        assertEquals(List.of("form-b", "form-a"), renderer.embeddedFormKeys(html));
        assertEquals(List.of(), renderer.embeddedFormKeys("<p>沒有標記</p>"));
        assertEquals(List.of(), renderer.embeddedFormKeys(null));
    }
}
