package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 動態行銷追蹤腳本：四組廣告 ID 由環境變數集中管理，
 * survey 問卷頁與 land-page 都載入本端點，改 ID 只需改環境變數、免動程式碼。
 * 留空的平台會自動略過、不載入。
 */
@RestController
public class TrackingController {

    private final String ga4Id;
    private final String adsId;
    private final String metaPixelId;
    private final String lineTagId;

    /** 從環境變數注入四組 ID（未設定者預設空字串 → 對應平台略過） */
    public TrackingController(
            @Value("${tracking.ga4-id:}") String ga4Id,
            @Value("${tracking.google-ads-conversion-id:}") String adsId,
            @Value("${tracking.meta-pixel-id:}") String metaPixelId,
            @Value("${tracking.line-tag-id:}") String lineTagId) {
        this.ga4Id = ga4Id;
        this.adsId = adsId;
        this.metaPixelId = metaPixelId;
        this.lineTagId = lineTagId;
    }

    /** 回傳組裝好真實 ID 的 tracking.js（瀏覽器端腳本，可同源或跨網域被 <script> 載入） */
    @GetMapping(value = "/tracking.js", produces = "application/javascript; charset=UTF-8")
    public ResponseEntity<String> trackingJs() {
        String js = SCRIPT_TEMPLATE
                .replace("__GA4_ID__", jsStr(ga4Id))
                .replace("__ADS_ID__", jsStr(adsId))
                .replace("__META_PIXEL_ID__", jsStr(metaPixelId))
                .replace("__LINE_TAG_ID__", jsStr(lineTagId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript; charset=UTF-8"))
                .header("Cache-Control", "public, max-age=300")
                .body(js);
    }

    /** 轉成安全的 JS 字串內容（避免單引號/反斜線/換行破壞腳本） */
    private String jsStr(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }

    // 腳本模板：四個 __XXX__ 佔位會被環境變數值取代；值為空字串時對應平台 init 直接 return
    private static final String SCRIPT_TEMPLATE = """
            /* 由 survey-backend 動態產生：四組 ID 來自環境變數，survey 與 land-page 共用同一份 */
            (function () {
              var TRACKING_IDS = {
                GOOGLE_TAG_ID: '__GA4_ID__',
                GOOGLE_ADS_CONVERSION_ID: '__ADS_ID__',
                META_PIXEL_ID: '__META_PIXEL_ID__',
                LINE_TAG_ID: '__LINE_TAG_ID__'
              };

              // Google Tag (gtag.js) — GA4 + Google Ads
              (function () {
                if (!TRACKING_IDS.GOOGLE_TAG_ID) return;
                var s = document.createElement('script');
                s.async = true;
                s.src = 'https://www.googletagmanager.com/gtag/js?id=' + TRACKING_IDS.GOOGLE_TAG_ID;
                document.head.appendChild(s);
                window.dataLayer = window.dataLayer || [];
                window.gtag = function () { window.dataLayer.push(arguments); };
                window.gtag('js', new Date());
                window.gtag('config', TRACKING_IDS.GOOGLE_TAG_ID);
              })();

              // Meta Pixel
              (function () {
                if (!TRACKING_IDS.META_PIXEL_ID) return;
                !function (f, b, e, v, n, t, s) {
                  if (f.fbq) return;
                  n = f.fbq = function () { n.callMethod ? n.callMethod.apply(n, arguments) : n.queue.push(arguments); };
                  if (!f._fbq) f._fbq = n;
                  n.push = n; n.loaded = !0; n.version = '2.0'; n.queue = [];
                  t = b.createElement(e); t.async = !0; t.src = v;
                  s = b.getElementsByTagName(e)[0]; s.parentNode.insertBefore(t, s);
                }(window, document, 'script', 'https://connect.facebook.net/en_US/fbevents.js');
                window.fbq('init', TRACKING_IDS.META_PIXEL_ID);
                window.fbq('track', 'PageView');
              })();

              // LINE Tag
              (function () {
                if (!TRACKING_IDS.LINE_TAG_ID) return;
                !function (g, d, o) {
                  g._ltq = g._ltq || [];
                  g._lt = g._lt || function () { g._ltq.push(arguments); };
                  var h = d.getElementsByTagName(o)[0];
                  var s = d.createElement(o);
                  s.async = 1;
                  s.src = 'https://d.line-scdn.net/n/line_tag/public/release/v1/lt.js';
                  h.parentNode.insertBefore(s, h);
                }(window, document, 'script');
                window._lt('init', { customerType: 'lap', tagId: TRACKING_IDS.LINE_TAG_ID });
                window._lt('send', 'pv', [TRACKING_IDS.LINE_TAG_ID]);
              })();

              // UTM 參數解析 — 自動從網址讀取廣告歸因參數
              (function () {
                var params = new URLSearchParams(window.location.search);
                var keys = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content'];
                var utm = {};
                keys.forEach(function (k) { var v = params.get(k); if (v) utm[k] = v; });
                if (Object.keys(utm).length > 0) {
                  window.__utm = utm;
                  try { sessionStorage.setItem('utm', JSON.stringify(utm)); } catch (e) {}
                } else {
                  try { var saved = sessionStorage.getItem('utm'); if (saved) window.__utm = JSON.parse(saved); } catch (e) {}
                }
              })();

              // 統一轉換事件 API（涵蓋 land-page 的 enroll_click 與問卷頁的 survey_submit）
              window.Tracking = {
                event: function (action, params) {
                  params = params || {};
                  var data = Object.assign({}, params, window.__utm || {});
                  if (typeof window.gtag === 'function') {
                    window.gtag('event', action, data);
                    if (TRACKING_IDS.GOOGLE_ADS_CONVERSION_ID && (action === 'enroll_click' || action === 'survey_submit')) {
                      window.gtag('event', 'conversion', {
                        send_to: TRACKING_IDS.GOOGLE_ADS_CONVERSION_ID,
                        value: params.value || 0,
                        currency: 'TWD'
                      });
                    }
                  }
                  if (typeof window.fbq === 'function') {
                    var fbMap = { 'enroll_click': 'InitiateCheckout', 'survey_submit': 'CompleteRegistration', 'view_curriculum': 'ViewContent' };
                    window.fbq('track', fbMap[action] || action, {
                      content_name: 'AI 賦能全端開發',
                      content_category: 'online_course',
                      value: params.value || 0,
                      currency: 'TWD'
                    });
                  }
                  if (typeof window._lt === 'function') {
                    var lineMap = { 'enroll_click': 'Conversion', 'survey_submit': 'Conversion' };
                    var le = lineMap[action];
                    if (le) window._lt('send', le, [TRACKING_IDS.LINE_TAG_ID]);
                  }
                }
              };
            })();
            """;
}
