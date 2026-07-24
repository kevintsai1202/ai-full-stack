-- 信件範本表：讓後台可編輯邀請信等範本內容（body_html 以 {{confirmLink}} 佔位確認連結）
CREATE TABLE mail_template (
    id           BIGSERIAL PRIMARY KEY,
    template_key VARCHAR(50)  NOT NULL UNIQUE,   -- 範本識別鍵（如 invite）
    subject      VARCHAR(255) NOT NULL,          -- 信件主旨
    body_html    TEXT         NOT NULL,          -- 信件 HTML 內文（含佔位符）
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 種子資料：現行邀請信內容，讓後台一上線就有範本可編輯
INSERT INTO mail_template (template_key, subject, body_html) VALUES (
  'invite',
  '你上過我的課——要不要一起繼續深入？',
  '<div style="font-family:system-ui,''Microsoft JhengHei'',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
  <h2>嗨，好久不見，我是凱文大叔！</h2>
  <p>你會收到這封信，是因為你之前上過我的<strong>基礎課程</strong>，也參加過課後的<strong>線上測驗</strong>——先謝謝你當時的參與。</p>
  <p>最近我正在準備一份<strong>電子報</strong>，想把平常研究和實戰的東西整理起來，固定分享給老同學。訂閱之後你會收到：</p>
  <ul>
    <li><strong>深入的技術討論</strong>：RAG、AI Agent、全端實戰的實作細節與踩雷筆記</li>
    <li><strong>AI 新知與新技術</strong>：新模型、新工具與趨勢的第一手觀察整理</li>
    <li><strong>各種好康優惠</strong>：除了 AI 產品的優惠活動外，還包含我自己線上、線下課程的專屬優惠</li>
  </ul>
  <p>如果你願意收到，點下面確認一下就好：</p>
  <p style="text-align:center;margin:28px 0">
    <a href="{{confirmLink}}" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">是的，我要訂閱</a>
  </p>
  <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
  <p style="color:#888;font-size:.85rem">
    寄件人：凱文大叔（你曾參加過我的基礎課程與線上測驗）。<br>
    若不想收到，直接略過這封信即可——未確認前我們不會再寄信給你。
  </p>
</div>'
);
