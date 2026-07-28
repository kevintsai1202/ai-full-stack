package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ReaderSiteLinks 行為測試：讀者網域、結尾斜線與登入返回位置。 */
class ReaderSiteLinksTest {

    /** 所有讀者連結使用獨立網域，且不產生雙斜線。 */
    @Test
    void buildsReaderLinksFromNormalizedBaseUrl() {
        ReaderSiteLinks links = new ReaderSiteLinks("https://reader.springai.world/");

        assertEquals("https://reader.springai.world/r/news/hello",
            links.article("hello"));
        assertEquals("https://reader.springai.world/r/archive", links.archive());
        assertEquals(
            "https://reader.springai.world/r/login?redirect=%2Fr%2Fnews%2Fhello",
            links.login("/r/news/hello"));
    }

    /** Magic Link 保留文章返回位置，讓登入後直接進入原文章。 */
    @Test
    void verifyLinkPreservesEncodedRedirect() {
        ReaderSiteLinks links = new ReaderSiteLinks("https://reader.springai.world");

        assertEquals(
            "https://reader.springai.world/api/reader/login/verify?t=TOKEN"
                + "&redirect=%2Fr%2Fnews%2Fhello",
            links.verifyLogin("TOKEN", "/r/news/hello"));
    }

    /** 邀請連結必須使用讀者站網域，並安全編碼推薦碼。 */
    @Test
    void buildsReaderReferralSubscriptionUrl() {
        ReaderSiteLinks links = new ReaderSiteLinks("https://reader.springai.world/");

        assertEquals("https://reader.springai.world/r/?ref=CODE%2B123",
            links.subscribeWithReferral("CODE+123"));
    }
}
