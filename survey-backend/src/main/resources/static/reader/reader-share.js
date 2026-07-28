/**
 * 初始化讀者端分享元件：產生貼文範本、複製內容，並串接 Facebook、Threads
 * 與裝置原生分享。Instagram 沒有通用網頁發文 intent，因此優先交給原生分享選單，
 * 不支援時改為複製貼文，避免顯示一個實際無法完成分享的假按鈕。
 */
function initializeReaderShareCards() {
  document.querySelectorAll('.viral-share').forEach((card) => {
    const title = card.dataset.shareTitle || '凱文大叔的電子報';
    const kind = card.dataset.shareKind || 'article';
    const rawUrl = card.dataset.shareUrl || window.location.pathname;
    const url = new URL(rawUrl, window.location.origin).href;
    const linkInput = card.querySelector('.share-link-input');
    const postInput = card.querySelector('.share-post-input');
    const status = card.querySelector('.share-status');

    const templates = kind === 'invite'
      ? [
          `最近在看「凱文大叔的電子報」，有 RAG、AI Agent 和全端實戰的踩雷筆記。透過我的連結訂閱並完成信箱確認，我也會獲得邀請點數：\n${url}`,
          `如果你也在做 AI 或全端開發，推薦這份會直接講實作細節的電子報 👇\n${url}`,
          `這份 AI 實戰電子報值得收藏：${url}`
        ]
      : [
          `這篇「${title}」把實作細節整理得很清楚，推薦給也在研究 AI／全端開發的朋友：\n${url}`,
          `剛讀完「${title}」，裡面的踩雷經驗很實用，少走不少彎路 👇\n${url}`,
          `值得收藏的一篇：${title}\n${url}`
        ];

    linkInput.value = url;
    postInput.value = templates[0];

    /** 顯示不會打斷操作的分享結果。 */
    const showStatus = (message, ok = true) => {
      status.textContent = message;
      status.className = `share-status msg show ${ok ? 'ok' : 'err'}`;
    };

    /** 複製文字，舊瀏覽器或非 HTTPS 時退回選取欄位。 */
    const copyText = async (text, fallbackInput) => {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch (error) {
        fallbackInput.focus();
        fallbackInput.select();
        return false;
      }
    };

    card.querySelectorAll('[data-template-index]').forEach((button) => {
      button.addEventListener('click', () => {
        const index = Number(button.dataset.templateIndex);
        postInput.value = templates[index] || templates[0];
        card.querySelectorAll('[data-template-index]').forEach((item) => {
          item.classList.toggle('active', item === button);
        });
      });
    });

    card.querySelector('[data-action="copy-link"]').addEventListener('click', async () => {
      const copied = await copyText(url, linkInput);
      showStatus(copied ? '分享連結已複製。' : '連結已選取，請按 Ctrl+C 複製。', copied);
    });

    card.querySelector('[data-action="copy-post"]').addEventListener('click', async () => {
      const copied = await copyText(postInput.value, postInput);
      showStatus(copied ? '貼文內容已複製。' : '貼文已選取，請按 Ctrl+C 複製。', copied);
    });

    const storyButton = card.querySelector('[data-action="story-card"]');
    if (storyButton && storyButton.dataset.cardUrl) {
      storyButton.addEventListener('click', async () => {
        try {
          const response = await fetch(storyButton.dataset.cardUrl);
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const blob = await response.blob();
          const file = new File([blob], 'springai-story.png', { type: 'image/png' });
          if (navigator.share && navigator.canShare?.({ files: [file] })) {
            await navigator.share({ files: [file], title, text: postInput.value });
            showStatus('已開啟分享選單，選擇 Instagram 限時動態即可。');
            return;
          }
          const anchor = document.createElement('a');
          anchor.href = URL.createObjectURL(blob);
          anchor.download = file.name;
          anchor.click();
          URL.revokeObjectURL(anchor.href);
          showStatus('限時動態圖卡已下載。');
        } catch (error) {
          if (error.name !== 'AbortError') showStatus('圖卡暫時無法下載，請稍後再試。', false);
        }
      });
    }

    card.querySelector('[data-platform="facebook"]').addEventListener('click', () => {
      window.open(
        `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`,
        'facebook-share',
        'popup,width=720,height=620'
      );
    });

    card.querySelector('[data-platform="threads"]').addEventListener('click', () => {
      window.open(
        `https://www.threads.com/intent/post?text=${encodeURIComponent(postInput.value)}`,
        'threads-share',
        'popup,width=720,height=720'
      );
    });

    card.querySelector('[data-platform="instagram"]').addEventListener('click', async () => {
      const shareData = { title, text: postInput.value, url };
      if (navigator.share && (!navigator.canShare || navigator.canShare(shareData))) {
        try {
          await navigator.share(shareData);
          showStatus('已開啟裝置分享選單，請選擇 Instagram。');
          return;
        } catch (error) {
          if (error.name === 'AbortError') return;
        }
      }
      const copied = await copyText(postInput.value, postInput);
      showStatus(
        copied
          ? 'Instagram 不支援網頁直接發文；貼文已複製，請貼到限時動態或貼文。'
          : '請按 Ctrl+C 複製後貼到 Instagram。',
        copied
      );
    });
  });
}

/** 記錄帶推薦碼的一般邀請或文章落地點擊；隨機代碼不含 email、IP 或裝置指紋。 */
async function trackReferralLanding() {
  const params = new URLSearchParams(location.search);
  const ref = params.get('ref');
  const match = location.pathname.match(/^\/r\/news\/([a-z0-9][a-z0-9-]{0,99})$/);
  const isInviteLanding = location.pathname === '/r/' || location.pathname === '/r';
  if (!ref || (!match && !isInviteLanding)) return;
  let visitorKey = localStorage.getItem('springai_ref_visitor');
  if (!visitorKey) {
    visitorKey = crypto.randomUUID().replaceAll('-', '');
    localStorage.setItem('springai_ref_visitor', visitorKey);
  }
  try {
    await fetch('/api/referrals/click', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ref, slug: match ? match[1] : null, visitorKey }),
      keepalive: true
    });
  } catch (error) {
    // 分析資料不可阻斷閱讀；離線或追蹤被封鎖時安靜略過。
  }
}

/** 讀到文末才顯示低干擾提示；可關閉，點擊後平滑捲到完整分享工具。 */
function initializeCompletionShare() {
  const sentinel = document.querySelector('#reading-complete-sentinel');
  const prompt = document.querySelector('#completion-share');
  const shareCard = document.querySelector('.article-share');
  if (!sentinel || !prompt || !shareCard || !('IntersectionObserver' in window)) return;
  const observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      prompt.hidden = false;
      observer.disconnect();
    }
  }, { threshold: 0.5 });
  observer.observe(sentinel);
  prompt.querySelector('[data-action="open-share"]').addEventListener('click', () => {
    prompt.hidden = true;
    shareCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
  prompt.querySelector('.completion-close').addEventListener('click', () => {
    prompt.hidden = true;
  });
}

document.addEventListener('DOMContentLoaded', () => {
  initializeReaderShareCards();
  trackReferralLanding();
  initializeCompletionShare();
});
