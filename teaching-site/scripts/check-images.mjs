import { readFileSync, existsSync } from "fs";
import { join } from "path";

const dir = "d:/GitHub/hahow-ai-full-stack/teaching-site/assets/illustrations";
const appJs = readFileSync("d:/GitHub/hahow-ai-full-stack/teaching-site/app.js", "utf8");

// 解析 conceptImageMap 中所有檔名
const lines = appJs.split("\n");
const imgs = [];
let inMap = false;
for (const l of lines) {
  if (l.includes("conceptImageMap")) { inMap = true; continue; }
  if (inMap && l.trim() === "};") break;
  if (inMap) {
    const m = l.match(/:\s*"([^"]+\.png)"/);
    if (m) imgs.push(m[1]);
  }
}

const unique = [...new Set(imgs)].sort();
let miss = 0, ok = 0;

console.log("=== conceptVisual 映射檔案檢查 ===\n");
for (const f of unique) {
  const exists = existsSync(join(dir, f));
  console.log((exists ? " ✅ " : " ❌ ") + f);
  if (exists) ok++; else miss++;
}

console.log(`\nTotal: ${unique.length}  Found: ${ok}  Missing: ${miss}`);
