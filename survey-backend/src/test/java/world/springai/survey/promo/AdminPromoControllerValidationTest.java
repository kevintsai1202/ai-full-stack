package world.springai.survey.promo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import world.springai.survey.AdminKeyGuard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * 釘住「PromoValidationException 必須轉 400」這個行為，避免回歸。
 *
 * <p>先前的 {@code @ExceptionHandler} 在 handler 內部 {@code throw new ResponseStatusException(...)}，
 * 而 Spring 的 {@code ExceptionHandlerExceptionResolver} 對 handler 內再拋出的例外一律視為
 * 「處理失敗」放棄處理，讓原始例外繼續往外傳播、最終落到預設 500——不是新拋出例外的狀態碼。
 * 本測試以 {@code @WebMvcTest} 實跑 MockMvc（而非直接呼叫 Java 方法）以重現此落差，
 * 修法後 handler 改為回傳 {@code ResponseEntity<ProblemDetail>}。</p>
 */
@WebMvcTest(AdminPromoController.class)
@Import(AdminKeyGuard.class)
@TestPropertySource(properties = {"app.admin-api-key=test-key"})
class AdminPromoControllerValidationTest {

    @Autowired MockMvc mvc;
    @MockBean PromoProposalService proposalService;
    @MockBean PromoPlacementService placementService;
    @MockBean PromoStatsService statsService;
    @MockBean PromoProposalRepository proposalRepository;

    /** service 拋出 PromoValidationException 時，端點須回 400 且 body 帶原始訊息 */
    @Test
    void approveWithValidationExceptionReturns400() throws Exception {
        when(proposalService.approve(1L))
            .thenThrow(new PromoProposalService.PromoValidationException("狀態不可核准"));

        mvc.perform(post("/api/admin/promo/proposals/1/approve").header("X-Admin-Key", "test-key"))
           .andExpect(status().isBadRequest())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("狀態不可核准")));
    }

    /** 無金鑰時仍先擋在 AdminKeyGuard，回 401（確認守衛順序未被本次修正影響） */
    @Test
    void approveWithoutKeyReturns401() throws Exception {
        mvc.perform(post("/api/admin/promo/proposals/1/approve"))
           .andExpect(status().isUnauthorized());
    }
}
