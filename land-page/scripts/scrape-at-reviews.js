// ============================================================
// AmazingTalker 評價擷取腳本（可重跑）
// 用途：從凱文大叔的 AmazingTalker 教師頁擷取「程式設計」課程的真實五星評價
//       （姓名 + 內容 + 日期），輸出乾淨 JSON 供 land-page 使用。
// 執行方式（需先安裝 agent-browser）：
//   1. agent-browser open "<教師頁 URL>?review_panel_visible=true"
//   2. 於頁面點開「顯示全部 96 則評價」並捲動載入全部
//   3. cat scrape-at-reviews.js | agent-browser eval --stdin > at-reviews.json
// 說明：評價面板的每則評論在 DOM 內會出現兩份節點，其中一份的內容會「污染」
//       到下一則評論文字；乾淨的那份不含日期字串，本腳本以此判斷並去重。
// ============================================================
(() => {
  // 取得目前開啟的評價 overlay
  const ov = document.querySelector('.v-overlay--active') || document;
  // 評價日期格式（用來辨識被污染的內容）
  const dateRe = /\d{4}年\d{1,2}月\d{1,2}日/;
  // 每則評價以 .commenter-wrapper（姓名+時間）為錨點
  const cws = [...ov.querySelectorAll('.commenter-wrapper')];

  const map = new Map(); // key: 姓名|日期 -> {name,date,content}
  cws.forEach(cw => {
    const time = cw.querySelector('.time')?.innerText.trim() || '';
    const nt = cw.querySelector('.name-time-wrapper');
    const name = nt ? nt.innerText.replace(time, '').trim() : '';
    // 內容 = 緊鄰的下一個兄弟節點文字，去除教師回覆與「翻譯」按鈕字樣
    let content = cw.nextElementSibling ? cw.nextElementSibling.innerText.trim() : '';
    content = content.split('教師回覆')[0].replace(/\n?翻譯\s*$/, '').trim();

    const key = name + '|' + time;
    // 優先保留「乾淨」內容：非空、且不含日期字串（未被下一則污染）
    const clean = content && !dateRe.test(content);
    if (!map.has(key)) {
      map.set(key, { name, date: time, content: clean ? content : '' });
    } else if (clean) {
      map.get(key).content = content;
    }
  });

  // 僅保留有實際文字評論者，依日期新到舊輸出
  const list = [...map.values()].filter(r => r.content.length > 0);
  return JSON.stringify(list, null, 2);
})()
