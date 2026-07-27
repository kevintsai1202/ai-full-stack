package world.springai.survey.audience;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * builder 產生的連結必須真的能被 controller 接受——路由層的往返測試。
 *
 * <p><b>這是唯一能證明「搬移無損」的測試。</b>{@link SubscriptionControllerTest}
 * 都是直接呼叫 controller 方法的 POJO 呼叫，完全不經過 Spring 路由——因此把
 * {@code @RequestParam("t")} 改名成 token、加上 class-level
 * {@code @RequestMapping}、或拿掉 produces，都不會讓它們變紅，
 * 但會讓所有已寄出的信件連結失效。</p>
 *
 * <p>刻意把 {@link SubscriptionLinkBuilder} 的 base URL 設為空字串，讓 builder
 * 產出相對路徑，才能整串餵給 MockMvc——這樣路徑、參數名、mapping、content-type
 * 四件事同時被一個測試鎖住。tokenService 用真實 bean（{@code @Import}），
 * 這樣簽章驗證也一併走真實邏輯，不是靠 mock 放行。</p>
 */
@WebMvcTest(SubscriptionController.class)
@Import(UnsubscribeTokenService.class)
@TestPropertySource(properties = {
    "app.unsubscribe-secret=test-secret"
})
class SubscriptionRoutingTest {

    @Autowired MockMvc mvc;
    @Autowired UnsubscribeTokenService tokenService;
    @MockBean SurveyResponseRepository repository;
    @MockBean AudiencePlatformService audiencePlatformService;

    /** builder 產生的確認連結必須真的能被 controller 接受，並真的觸發 repository 寫入 */
    @Test
    void confirmLinkFromBuilderIsAcceptedByController() throws Exception {
        SubscriptionLinkBuilder builder = new SubscriptionLinkBuilder(tokenService, "");
        when(repository.confirmByEmail("a@b.com")).thenReturn(1);

        // 用 URI.create 而非 get(String)：builder 產出的 query string 已經是
        // URL-encoded（email 的 @ 已編碼成 %40）。get(String) 會把整串當成
        // URI 樣板再重新編碼一次，導致 %40 被二次編碼成 %2540，
        // controller 收到的 email 參數就變成沒解碼的原始字串，簽章驗證必敗。
        mvc.perform(get(URI.create(builder.confirmLink("a@b.com"))))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/html"));

        verify(repository).confirmByEmail("a@b.com");
    }

    /** builder 產生的退訂連結必須真的能被 controller 接受，並真的觸發 repository 寫入 */
    @Test
    void unsubscribeLinkFromBuilderIsAcceptedByController() throws Exception {
        SubscriptionLinkBuilder builder = new SubscriptionLinkBuilder(tokenService, "");

        mvc.perform(get(URI.create(builder.unsubscribeLink("a@b.com"))))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/html"));

        verify(repository).unsubscribeByEmail("a@b.com");
    }

    /**
     * 簽章錯誤的連結也要走真實路由回 200 且不寫入。
     *
     * <p>確保「不洩漏名單」這條性質也走過真實路由驗證，而不只是
     * controller 的 POJO 呼叫測過。</p>
     */
    @Test
    void tamperedSignatureFromRealRouteReturnsOkWithoutWriting() throws Exception {
        SubscriptionLinkBuilder builder = new SubscriptionLinkBuilder(tokenService, "");
        String tampered = builder.confirmLink("a@b.com") + "-tampered";

        mvc.perform(get(URI.create(tampered)))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/html"));

        verify(repository, never()).confirmByEmail(anyString());
    }
}
