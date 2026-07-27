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

document.addEventListener('DOMContentLoaded', highlightCurrentReaderNavigation);
