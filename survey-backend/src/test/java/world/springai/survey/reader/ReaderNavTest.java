package world.springai.survey.reader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReaderNav} 的輸出內容測試：確保工商合作入口僅在登入狀態顯示。
 *
 * <p>與 {@code ReaderNavGuardTest} 分工不同：那個測試守的是「導覽列只能來自
 * 本類」（架構層級的機械化守衛），這裡驗的是「本類自己產生的內容是否正確」
 * （行為層級）。兩者都需要，缺一都無法完整保護 spec 對「工商合作僅登入可見」
 * 的要求。</p>
 */
class ReaderNavTest {

    /** 登入分支應含工商合作入口；未登入分支不應洩漏這個連結 */
    @Test
    void 登入導覽含工商合作_未登入不含() {
        assertTrue(ReaderNav.links(true).contains("<a href=\"/r/promo\">工商合作</a>"));
        assertFalse(ReaderNav.links(false).contains("/r/promo"));
    }
}
