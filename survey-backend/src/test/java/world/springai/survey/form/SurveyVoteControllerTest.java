package world.springai.survey.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 信中一鍵投票端點：合法投票 302 到接續頁；目標不合法或 o 非數字一律 404（避免 500 洩漏堆疊） */
class SurveyVoteControllerTest {

    private SurveyVoteService voteService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        voteService = mock(SurveyVoteService.class);
        SurveyVoteController controller = new SurveyVoteController(voteService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 合法投票302轉址到接續頁() throws Exception {
        when(voteService.vote(eq("reader-poll"), eq("q1"), eq(1), eq(9L), eq("tok"), any()))
            .thenReturn(Optional.of("/r/survey/reader-poll?voted=1&c=9&rt=tok"));

        mvc.perform(get("/s/v/reader-poll")
                .param("f", "q1").param("o", "1").param("c", "9").param("rt", "tok"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/survey/reader-poll?voted=1&c=9&rt=tok"));
    }

    @Test
    void 投票目標不合法service回empty時回404() throws Exception {
        when(voteService.vote(any(), any(), anyInt(), any(), any(), any()))
            .thenReturn(Optional.empty());

        mvc.perform(get("/s/v/reader-poll").param("f", "q1").param("o", "1"))
           .andExpect(status().isNotFound());
    }

    @Test
    void o參數非數字回404且不呼叫service() throws Exception {
        mvc.perform(get("/s/v/reader-poll").param("f", "q1").param("o", "abc"))
           .andExpect(status().isNotFound());

        verify(voteService, never()).vote(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void o參數缺漏回404() throws Exception {
        mvc.perform(get("/s/v/reader-poll").param("f", "q1"))
           .andExpect(status().isNotFound());
    }

    /**
     * M3 修正：{@code c}（campaignId）與 {@code o} 同樣宣告為 {@code String} 自行
     * parse，而非讓 Spring 用 {@code @RequestParam Long} 直接綁定——理由相同：
     * 綁定失敗會被預設 handler 接住回 500。但落地行為與 {@code o} 不同：{@code c}
     * 只是可選的活動歸因，不是「這是不是合法投票目標」的判斷依據，因此非數字時
     * 視為未帶 campaignId（傳 null），仍讓合法投票正常進行，而非整個請求 404。
     */
    @Test
    void c參數非數字時視為null仍可投票() throws Exception {
        when(voteService.vote(eq("reader-poll"), eq("q1"), eq(1), isNull(), any(), any()))
            .thenReturn(Optional.of("/r/survey/reader-poll?voted=1"));

        mvc.perform(get("/s/v/reader-poll")
                .param("f", "q1").param("o", "1").param("c", "abc"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/survey/reader-poll?voted=1"));

        verify(voteService).vote(eq("reader-poll"), eq("q1"), eq(1), isNull(), any(), any());
    }

    /** c 參數缺漏時同樣視為 null，行為與現況一致（不應是這次修正的破壞性變更）。 */
    @Test
    void c參數缺漏時視為null() throws Exception {
        when(voteService.vote(eq("reader-poll"), eq("q1"), eq(1), isNull(), any(), any()))
            .thenReturn(Optional.of("/r/survey/reader-poll?voted=1"));

        mvc.perform(get("/s/v/reader-poll").param("f", "q1").param("o", "1"))
           .andExpect(status().isFound());
    }
}
