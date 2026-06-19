/**
 * 行銷追蹤模組（問卷頁）— 統一管理 Google Ads / GA4 / Meta Pixel / LINE Tag
 *
 * 重要：請填入「與 land-page 完全相同」的四組 ID。
 * 用同一組廣告帳號 ID，填過問卷的訪客才會與 land-page 進入同一受眾池，
 * 之後可在廣告後台對「填過問卷的人」做再行銷與類似受眾（lookalike）擴展。
 *
 * 使用方式（純靜態頁，由 survey-backend 服務）：
 * 1. 將下方四組 ID 換成與 land-page 相同的真實 ID（留預設值的平台自動略過）。
 * 2. 問卷頁已用 <script src="tracking.js"></script> 載入本檔。
 * 3. 送出問卷成功會呼叫 window.Tracking.event('survey_submit')，
 *    對應 Meta 標準事件 CompleteRegistration，方便建立自訂受眾。
 */

// ============================================================
// 🔧 請替換為你的真實追蹤 ID（與 land-page 相同）
// ============================================================
const TRACKING_IDS = {
  // Google Tag ID（格式：G-XXXXXXXXXX 或 AW-XXXXXXXXXX）
  GOOGLE_TAG_ID: 'G-XXXXXXXXXX',

  // Google Ads 轉換 ID（格式：AW-XXXXXXXXXX/XXXXXXXX）
  GOOGLE_ADS_CONVERSION_ID: 'AW-XXXXXXXXXX/XXXXXXXX',

  // Meta Pixel ID（純數字）
  META_PIXEL_ID: 'XXXXXXXXXXXXXXX',

  // LINE Tag ID（格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx）
  LINE_TAG_ID: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
};

// ============================================================
// Google Tag (gtag.js) — GA4 + Google Ads
// ============================================================
(function initGoogleTag() {
  if (TRACKING_IDS.GOOGLE_TAG_ID === 'G-XXXXXXXXXX') return;

  const script = document.createElement('script');
  script.async = true;
  script.src = 'https://www.googletagmanager.com/gtag/js?id=' + TRACKING_IDS.GOOGLE_TAG_ID;
  document.head.appendChild(script);

  window.dataLayer = window.dataLayer || [];
  window.gtag = function () { window.dataLayer.push(arguments); };
  window.gtag('js', new Date());
  window.gtag('config', TRACKING_IDS.GOOGLE_TAG_ID);
})();

// ============================================================
// Meta Pixel (Facebook / Instagram)
// ============================================================
(function initMetaPixel() {
  if (TRACKING_IDS.META_PIXEL_ID === 'XXXXXXXXXXXXXXX') return;

  !function (f, b, e, v, n, t, s) {
    if (f.fbq) return;
    n = f.fbq = function () { n.callMethod ? n.callMethod.apply(n, arguments) : n.queue.push(arguments); };
    if (!f._fbq) f._fbq = n;
    n.push = n; n.loaded = !0; n.version = '2.0';
    n.queue = [];
    t = b.createElement(e); t.async = !0; t.src = v;
    s = b.getElementsByTagName(e)[0]; s.parentNode.insertBefore(t, s);
  }(window, document, 'script', 'https://connect.facebook.net/en_US/fbevents.js');

  window.fbq('init', TRACKING_IDS.META_PIXEL_ID);
  window.fbq('track', 'PageView');
})();

// ============================================================
// LINE Tag
// ============================================================
(function initLineTag() {
  if (TRACKING_IDS.LINE_TAG_ID === 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx') return;

  !function (g, d, o) {
    g._ltq = g._ltq || [];
    g._lt = g._lt || function () { g._ltq.push(arguments); };
    var h = d.getElementsByTagName(o)[0];
    var s = d.createElement(o);
    s.async = 1;
    s.src = 'https://d.line-scdn.net/n/line_tag/public/release/v1/lt.js';
    h.parentNode.insertBefore(s, h);
  }(window, document, 'script');

  window._lt('init', {
    customerType: 'lap',
    tagId: TRACKING_IDS.LINE_TAG_ID,
  });
  window._lt('send', 'pv', [TRACKING_IDS.LINE_TAG_ID]);
})();

// ============================================================
// UTM 參數解析 — 自動從網址讀取廣告歸因參數
// ============================================================
(function parseUtm() {
  var params = new URLSearchParams(window.location.search);
  var utmKeys = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content'];
  var utm = {};
  utmKeys.forEach(function (key) {
    var val = params.get(key);
    if (val) utm[key] = val;
  });
  if (Object.keys(utm).length > 0) {
    window.__utm = utm;
    try { sessionStorage.setItem('utm', JSON.stringify(utm)); } catch (e) { /* 無痕模式 */ }
  } else {
    try {
      var saved = sessionStorage.getItem('utm');
      if (saved) window.__utm = JSON.parse(saved);
    } catch (e) { /* 無痕模式 */ }
  }
})();

// ============================================================
// 統一轉換事件 API
// ============================================================
/**
 * 發送轉換事件到所有已啟用的廣告平台
 * @param {string} action - 事件名稱（如 'survey_submit'）
 * @param {Object} [params] - 附加參數
 */
window.Tracking = {
  event: function (action, params) {
    params = params || {};
    var eventData = Object.assign({}, params, window.__utm || {});

    // Google Analytics 4 / Google Ads
    if (typeof window.gtag === 'function') {
      window.gtag('event', action, eventData);
      // 問卷完成也送 Google Ads 轉換（若有設定轉換 ID）
      if (TRACKING_IDS.GOOGLE_ADS_CONVERSION_ID !== 'AW-XXXXXXXXXX/XXXXXXXX'
          && (action === 'enroll_click' || action === 'survey_submit')) {
        window.gtag('event', 'conversion', {
          send_to: TRACKING_IDS.GOOGLE_ADS_CONVERSION_ID,
          value: params.value || 0,
          currency: 'TWD',
        });
      }
    }

    // Meta Pixel：survey_submit 對應標準事件 CompleteRegistration，便於建立自訂受眾
    if (typeof window.fbq === 'function') {
      var fbEventMap = {
        'enroll_click': 'InitiateCheckout',
        'survey_submit': 'CompleteRegistration',
        'view_curriculum': 'ViewContent',
      };
      var fbEvent = fbEventMap[action] || action;
      window.fbq('track', fbEvent, {
        content_name: 'AI 賦能全端開發',
        content_category: 'online_course',
        value: params.value || 0,
        currency: 'TWD',
      });
    }

    // LINE Tag
    if (typeof window._lt === 'function') {
      var lineEventMap = {
        'enroll_click': 'Conversion',
        'survey_submit': 'Conversion',
      };
      var lineEvent = lineEventMap[action];
      if (lineEvent) {
        window._lt('send', lineEvent, [TRACKING_IDS.LINE_TAG_ID]);
      }
    }
  },
};
