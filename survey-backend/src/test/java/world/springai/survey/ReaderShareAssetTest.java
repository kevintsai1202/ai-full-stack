package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 讀者端社群分享資產的靜態契約測試。 */
class ReaderShareAssetTest {

    /** 分享腳本必須保留三平台行為與 Instagram 的無 Web Intent 退路。 */
    @Test
    void shareScriptContainsPlatformActionsAndFallback() throws Exception {
        String script = Files.readString(
            Path.of("src/main/resources/static/reader/reader-share.js"));

        assertTrue(script.contains("facebook.com/sharer/sharer.php"),
            "Facebook 按鈕必須開啟正式分享網址");
        assertTrue(script.contains("threads.com/intent/post"),
            "Threads 按鈕必須開啟 Web Intent");
        assertTrue(script.contains("navigator.share"),
            "Instagram 應優先使用裝置原生分享選單");
        assertTrue(script.contains("navigator.clipboard.writeText"),
            "不支援原生分享時必須能複製貼文");
        assertTrue(script.contains("AbortError"),
            "使用者取消原生分享不是錯誤，不應顯示失敗訊息");
        assertTrue(script.contains("location.pathname === '/r/'"),
            "一般邀請連結也必須記錄進分享漏斗");
        assertTrue(script.contains("slug: match ? match[1] : null"),
            "一般邀請點擊必須以空文章來源送出，不可偽裝成文章分享");
    }
}
