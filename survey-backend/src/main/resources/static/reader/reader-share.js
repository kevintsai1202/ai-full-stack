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

document.addEventListener('DOMContentLoaded', initializeReaderShareCards);
