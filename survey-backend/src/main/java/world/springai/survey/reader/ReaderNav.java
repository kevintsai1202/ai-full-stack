package world.springai.survey.reader;

/**
 * 讀者端頁首導覽列的<b>唯一</b>來源。
 *
 * <p><b>為什麼需要這一層</b>：在此之前，同一條導覽列有四份各自拼字串的實作
 * （{@code ReaderPageController}、{@code ReaderPortalController} 的
 * {@code /r/me} 與 {@code /r/invite} 各一份、{@code RulesPageController}），
 * 加上 {@code templates/reader/index.html} 裡寫死的第五份。結果是可預期的：</p>
 * <ul>
 *   <li>{@code /r/} 首頁對<b>已登入</b>讀者仍顯示「登入」——點下去會再寄一封
 *       magic link 給早就登入的人。</li>
 *   <li>{@code /r/rules} <b>不在任何一份導覽列裡</b>。規則頁是點數機制的可信度
 *       來源（spec §5.11），讀者卻只能從 paywall 或頁內文字連結進去；也就是說
 *       「還沒撞到付費牆的人」永遠不會知道有這一頁。</li>
 *   <li>{@code /r/me} 與 {@code /r/invite} 互相連結但都不連歷史內容以外的第三頁，
 *       兩個登入後的頁面呈現不同的功能地圖。</li>
 * </ul>
 *
 * <p><b>本類的輸出必須永遠是固定字串</b>：{@link HtmlTemplate#render} 的契約是
 * 替換值原樣插入 HTML、不做跳脫，而呼叫端傳的就是本方法的回傳值。
 * <b>不得</b>把任何使用者可控值（email、顯示名稱、slug、query 參數……）拼進
 * 導覽列；真的需要時必須先過 {@link HtmlTemplate#escapeHtml}。目前的實作沒有
 * 任何參數會進入輸出（{@code loggedIn} 只選分支），所以是安全的。</p>
 *
 * <p><b>機械化守衛</b>：{@code ReaderNavGuardTest} 守著兩件事——① reader 套件的
 * 生產程式碼中，除本類外不得出現 {@code <a href="/r/archive"}、
 * {@code <a href="/r/me"}、{@code <a href="/r/login"} 這三個逐字字串；
 * ② {@code templates/reader/*.html}（{@code login.html} 與 {@code not-found.html}
 * 除外）的 {@code <nav>} 區塊內只能有 {@code <!--NAV_LINKS-->} 佔位符、不得含
 * {@code <a}。這兩道檢查窄到目前偽陽性為零（paywall 行動按鈕、規則頁提示連結
 * 用的都是不同的字串），能擋住「某個 controller 順手 inline 一份導覽」與
 * 「某個模板的 {@code <nav>} 寫死連結」這兩種最常見的回歸；<b>新增讀者端頁面時，
 * 導覽列一律呼叫本類，不要自己拼。</b></p>
 *
 * <p>{@code /r/login} 與 404 頁刻意<b>不</b>使用本類：登入頁的導覽列若含「登入」
 * 就是連向自己，而那兩頁都是終點頁（讀者到那裡是為了完成一件事或離開），
 * 維持最小導覽是刻意的，不是漏改。</p>
 */
final class ReaderNav {

    /** 純靜態工具類，不需要實例 */
    private ReaderNav() {
    }

    /** 首頁：讓訂閱入口在導覽列中有明確位置，也能呈現目前頁面的選取狀態。 */
    private static final String HOME = "<a href=\"/r/\">首頁</a>";

    /** 歷史內容：所有頁面都有，放第一個（既有四份實作一致的唯一一項） */
    private static final String ARCHIVE = "<a href=\"/r/archive\">歷史內容</a>";

    /** 遊戲規則：點數機制的可信度來源，登入與否都需要看得到（spec §5.11） */
    private static final String RULES = "<a href=\"/r/rules\">遊戲規則</a>";

    /** 我的帳戶：餘額與交易明細 */
    private static final String ME = "<a href=\"/r/me\">我的帳戶</a>";

    /** 我的邀請：邀請連結與成效 */
    private static final String INVITE = "<a href=\"/r/invite\">我的邀請</a>";

    /** 登入：未登入時的行動入口 */
    private static final String LOGIN = "<a href=\"/r/login\">登入</a>";

    /**
     * 產生 {@code <!--NAV_LINKS-->} 的內容。
     *
     * <p>順序沿用既有頁面的慣例：公開頁在前（歷史內容、遊戲規則），
     * 個人頁在後。登入後同時列出「我的帳戶」與「我的邀請」——這會讓
     * {@code /r/me} 的導覽列出現連向自己的項目，這是刻意取捨：
     * 每頁各自把自己拿掉會讓「導覽列長什麼樣」重新變成 per-page 的知識，
     * 正是本類要消滅的東西，而導覽列連向當前頁是一般網站的常態。</p>
     *
     * @param loggedIn 是否已解析出有效的讀者身分（由呼叫端以 {@link ReaderContext} 判定）
     * @return 可直接插入 {@code <nav>} 的 HTML 片段；固定字串，不含任何使用者可控值
     */
    static String links(boolean loggedIn) {
        if (loggedIn) {
            return HOME + ARCHIVE + RULES + ME + INVITE;
        }
        return HOME + ARCHIVE + RULES + LOGIN;
    }
}
