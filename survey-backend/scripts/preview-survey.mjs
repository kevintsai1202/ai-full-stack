// 本機互動式預覽伺服器（dev only）：
// 純靜態伺服器無後端 API，圖表會空白。此腳本額外模擬 survey-backend 的三個端點，
// 讓開發者能在本機完整預覽問卷頁 + 右側即時統計，且送出後數字會即時跳動。
// 正式環境一律由 Spring Boot 的 survey-backend 提供真實資料，與本檔無關。
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const staticDir = path.resolve(here, "..", "src", "main", "resources", "static");
const port = Number(process.env.PORT || 5189);

// 可變的模擬統計狀態：送出問卷時即時累加，重現「即時填寫狀況」
const stats = {
  total: 137,
  interest: { "RAG 知識庫": 98, "Tool Calling": 81, "AI 輔助程式開發": 73, "前端整合": 52, "Spring Security": 44, "資料庫": 39, "Docker 部署": 31, "Spring 其他模組": 22 },
  status: { "在職工程師，想技能升級": 61, "想轉職全端／AI 工程師": 38, "在公司推動 AI 轉型": 19, "熟練 AI 工具但沒有開發經驗": 11, "學生": 8 },
  role: { "後端工程師": 49, "全端工程師": 28, "前端工程師": 21, "資料／AI 工程師": 17, "非本科轉職者": 12, "技術主管／PM": 10 }
};

// 將 {label:count} map 轉成依數量排序的 bucket 陣列，取前 limit 名
function buckets(map, limit) {
  return Object.entries(map).map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count).slice(0, limit);
}

const types = { ".html": "text/html; charset=utf-8", ".js": "application/javascript", ".css": "text/css", ".png": "image/png" };

const server = http.createServer(async (req, res) => {
  const url = req.url.split("?")[0];

  // 模擬動態 tracking.js（本機預覽用空殼，不實際送出追蹤）
  if (url === "/tracking.js") {
    res.writeHead(200, { "Content-Type": "application/javascript" });
    return res.end("window.Tracking={event:function(n){console.log('[mock tracking]',n);}};");
  }

  // 模擬公開即時統計端點
  if (url === "/api/survey/stats") {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({
      total: stats.total,
      interest: buckets(stats.interest, 99),
      status: buckets(stats.status, 99),
      role: buckets(stats.role, 6)
    }));
  }

  // 模擬問卷送出：累加統計後回 201，讓預覽看得到數字即時跳動
  if (url === "/api/survey" && req.method === "POST") {
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      try {
        const p = JSON.parse(body || "{}");
        if (p.website) { res.writeHead(204); return res.end(); } // 蜜罐
        stats.total += 1;
        (p.interest || []).forEach((k) => { stats.interest[k] = (stats.interest[k] || 0) + 1; });
        const st = p.answers && p.answers.status;
        if (st) stats.status[st] = (stats.status[st] || 0) + 1;
        if (p.role) stats.role[p.role] = (stats.role[p.role] || 0) + 1;
      } catch { /* 忽略解析錯誤 */ }
      res.writeHead(201); res.end();
    });
    return;
  }

  // 其餘走靜態檔
  const file = path.join(staticDir, url === "/" ? "index.html" : url);
  try {
    const buf = await fs.readFile(file);
    res.writeHead(200, { "Content-Type": types[path.extname(file)] || "application/octet-stream" });
    res.end(buf);
  } catch {
    res.writeHead(404); res.end("not found");
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log(`✓ 問卷頁互動預覽: http://127.0.0.1:${port}/  （含模擬即時統計，送出可見數字跳動）`);
});
