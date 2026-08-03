package world.springai.survey.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
}
