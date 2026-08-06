/**
 * 依目前路徑標示讀者端導覽項目；文章詳情視為「歷史內容」的一部分。
 * 除了視覺 class，也同步 aria-current，讓鍵盤與螢幕報讀器理解目前位置。
 */
function highlightCurrentReaderNavigation() {
  const path = window.location.pathname;
  let activeHref = path;

  if (path.startsWith('/r/news/')) {
    activeHref = '/r/archive';
  } else if (path === '/r' || path === '/r/') {
    activeHref = '/r/';
  }

  const links = document.querySelectorAll('.site-head nav a');
  links.forEach((link) => {
    const selected = link.getAttribute('href') === activeHref;
    link.classList.toggle('is-active', selected);
    if (selected) {
      link.setAttribute('aria-current', 'page');
    } else {
      link.removeAttribute('aria-current');
    }
  });
}

/** 取得目前文章 slug；非文章頁回 null。 */
function currentArticleSlug() {
  if (!document.querySelector('article')) return null;
  const matched = window.location.pathname.match(/^\/r\/news\/([a-z0-9-]+)$/);
  return matched ? matched[1] : null;
}

/**
 * 建立不含個資的匿名訪客識別碼；localStorage 不可用時退回本頁暫存值。
 */
function readerVisitorKey() {
  const storageKey = 'reader-analytics-visitor-v1';
  try {
    let key = localStorage.getItem(storageKey);
    if (!key) {
      key = crypto.randomUUID();
      localStorage.setItem(storageKey, key);
    }
    return key;
  } catch (error) {
    if (!window.__readerVisitorKey) {
      window.__readerVisitorKey = crypto.randomUUID();
    }
    return window.__readerVisitorKey;
  }
}

/**
 * 同時送出第一方匿名事件與既有廣告／分析平台事件；參數刻意不含 Email。
 */
function recordReaderEvent(eventName, externalName) {
  const articleSlug = currentArticleSlug();
  const payload = {
    eventName,
    visitorKey: readerVisitorKey(),
    pagePath: window.location.pathname,
    articleSlug
  };
  const body = JSON.stringify(payload);
  try {
    const blob = new Blob([body], { type: 'application/json' });
    if (!navigator.sendBeacon('/api/reader/funnel', blob)) {
      fetch('/api/reader/funnel', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true
      }).catch(() => {});
    }
  } catch (error) {
    fetch('/api/reader/funnel', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true
    }).catch(() => {});
  }
  if (externalName && window.Tracking && typeof window.Tracking.event === 'function') {
    window.Tracking.event(externalName, {
      page_type: pageType(),
      article_slug: articleSlug || undefined
    });
  }
}

/** 將 reader 路徑歸類成不含參數的頁面類型。 */
function pageType() {
  const path = window.location.pathname;
  if (currentArticleSlug()) return 'article';
  if (path === '/r/' || path === '/r') return 'subscription_home';
  if (path === '/r/archive') return 'archive';
  if (path === '/r/login') return 'login';
  if (path === '/r/me' || path === '/r/invite') return 'account';
  if (path === '/r/rules') return 'rules';
  return 'other';
}

/** 在品牌旁顯示目前訂閱人數；資料暫時不可用時保留不誤導的替代文案。 */
async function showSubscriberCount() {
  const brand = document.querySelector('.site-head .brand');
  if (!brand) return;
  const badge = document.createElement('span');
  badge.className = 'subscriber-count';
  badge.textContent = '讀者社群持續成長';
  badge.setAttribute('aria-live', 'polite');
  brand.insertAdjacentElement('afterend', badge);
  try {
    const response = await fetch('/api/reader/subscriber-count');
    if (!response.ok) return;
    const data = await response.json();
    if (Number.isSafeInteger(data.count) && data.count >= 0) {
      badge.textContent = `目前 ${new Intl.NumberFormat('zh-TW').format(data.count)} 位訂閱者`;
    }
  } catch (error) {
    // 人數屬輔助資訊；API 暫時失敗時不阻擋 reader 主流程。
  }
}

/** 記錄頁面層漏斗，並攔截文章中的訂閱首頁 CTA。 */
function initializeReaderFunnel() {
  recordReaderEvent('READER_PAGE_VIEW', 'reader_page_view');
  const type = pageType();
  if (type === 'article') recordReaderEvent('ARTICLE_VIEW', 'article_view');
  if (type === 'subscription_home') {
    recordReaderEvent('SUBSCRIPTION_HOME_VIEW', 'subscription_home_view');
  }
  if (type === 'article') {
    document.querySelectorAll('a[href^="/r/"]').forEach((link) => {
      const target = new URL(link.href, window.location.origin);
      if (target.pathname === '/r/' || target.pathname === '/r') {
        link.addEventListener('click', () => {
          recordReaderEvent('SUBSCRIPTION_CTA_CLICK', 'subscription_cta_click');
        });
      }
    });
  }
}

// 讓頁面內既有訂閱／解鎖流程能回報結果，不重複實作傳送細節。
window.ReaderAnalytics = { event: recordReaderEvent };

/**
 * 右上工具列（A2）：日夜切換恆顯示；登出僅在登入時顯示。
 * 登入判定：ReaderNav 只在登入時輸出「我的帳戶」連結——這是 server 對 session
 * 的真實判斷，前端不需要（也拿不到，cookie 是 httpOnly）另外的登入 API。
 */
function mountHeadTools() {
  const head = document.querySelector('.site-head-inner');
  if (!head || head.querySelector('.head-tools')) return;
  const tools = document.createElement('div');
  tools.className = 'head-tools';

  const themeBtn = document.createElement('button');
  themeBtn.type = 'button';
  themeBtn.id = 'reader-theme-btn';
  themeBtn.className = 'head-tool-btn';
  themeBtn.title = '切換日夜模式';
  themeBtn.setAttribute('aria-label', '切換日夜模式');
  themeBtn.textContent = document.documentElement.getAttribute('data-theme') === 'dark' ? '🌙' : '☀';
  themeBtn.addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('reader-theme', next);
    themeBtn.textContent = next === 'dark' ? '🌙' : '☀';
  });
  tools.append(themeBtn);

  if (document.querySelector('nav a[href="/r/me"]')) {
    const logoutBtn = document.createElement('button');
    logoutBtn.type = 'button';
    logoutBtn.id = 'reader-logout-btn';
    logoutBtn.className = 'head-tool-btn';
    logoutBtn.textContent = '登出';
    logoutBtn.addEventListener('click', async () => {
      // 既有登出端點會清除 reader_session cookie；成功後回首頁重載
      await fetch('/api/reader/logout', { method: 'POST' });
      location.href = '/r/';
    });
    tools.append(logoutBtn);
  }
  head.append(tools);
}

document.addEventListener('DOMContentLoaded', () => {
  highlightCurrentReaderNavigation();
  showSubscriberCount();
  initializeReaderFunnel();
  mountHeadTools();
});
