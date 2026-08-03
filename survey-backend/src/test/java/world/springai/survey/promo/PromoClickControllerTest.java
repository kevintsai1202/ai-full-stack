package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.promo.PromoClickService.Destination;
import world.springai.survey.reader.HtmlTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 工商點擊端點：https 目的地 302 轉址；mailto 目的地渲染聯絡中介頁（不轉址） */
class PromoClickControllerTest {

    private PromoClickService clickService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clickService = mock(PromoClickService.class);
        // HtmlTemplate 用真實實例：測試同時涵蓋聯絡頁模板的佔位符是否存在
        PromoClickController controller = new PromoClickController(clickService, new HtmlTemplate());
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build();
    }

    private void givenDestination(String linkUrl, String title) {
        when(clickService.resolveAndRecord(eq(55L), any(), any()))
            .thenReturn(Optional.of(new Destination(linkUrl, title)));
    }

    @Test
    void https目的地照舊302轉址() throws Exception {
        givenDestination("https://example.com/course", "好課推薦");
        mvc.perform(get("/promo/c/55"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "https://example.com/course"));
    }

    @Test
    void mailto目的地渲染聯絡頁而非轉址() throws Exception {
        givenDestination("mailto:sales@example.com", "OpenClaw AI 數位助理");
        mvc.perform(get("/promo/c/55"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/html"))
           // 顯示提案名稱與 Email 地址（供任何環境手動複製）
           .andExpect(content().string(containsString("OpenClaw AI 數位助理")))
           .andExpect(content().string(containsString("sales@example.com")))
           // 保留 mailto 捷徑給有設定本機郵件軟體的讀者
           .andExpect(content().string(containsString("href=\"mailto:sales@example.com\"")))
           // 一鍵複製按鈕
           .andExpect(content().string(containsString("id=\"copy-email\"")));
    }

    @Test
    void mailto帶subject參數_顯示地址不含參數但href保留() throws Exception {
        givenDestination("mailto:sales@example.com?subject=合作洽詢", "好課");
        mvc.perform(get("/promo/c/55"))
           .andExpect(status().isOk())
           // href 保留完整參數（郵件軟體會帶入主旨）
           .andExpect(content().string(containsString("mailto:sales@example.com?subject=合作洽詢")))
           // 顯示與複製用的地址是純信箱，不帶參數
           .andExpect(content().string(containsString("data-email=\"sales@example.com\"")));
    }

    @Test
    void 提案名稱含特殊字元時聯絡頁需跳脫() throws Exception {
        givenDestination("mailto:sales@example.com", "A\"B<C>");
        mvc.perform(get("/promo/c/55"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("A\"B<C>"))))
           .andExpect(content().string(containsString("A&quot;B&lt;C&gt;")));
    }

    @Test
    void 版位不存在回404() throws Exception {
        when(clickService.resolveAndRecord(eq(55L), any(), any()))
            .thenReturn(Optional.empty());
        mvc.perform(get("/promo/c/55")).andExpect(status().isNotFound());
    }
}
