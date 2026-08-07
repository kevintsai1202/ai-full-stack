// 用途：從 teaching-site/course-data.js 匯出各單元完整素材（concepts / prompts / tasks / materials）
//       成 markdown 檔，供課程素材包（course-package/）撰寫口語稿時引用。
// 執行方式：node scripts/export-teaching-site-content.mjs
// 輸出：course-package/_source/u1.md ~ u9.md、superpowers.md、quiz.md、materials.md、appendix.md
// 備註：course-data.js 為 `window.COURSE = {...}` 形式的 JSON，去掉前綴後可直接 JSON.parse

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const srcFile = path.join(root, 'teaching-site', 'course-data.js');
const outDir = path.join(root, 'course-package', '_source');

// 讀取並解析 course-data.js（去掉 window.COURSE = 前綴與結尾分號）
const raw = fs.readFileSync(srcFile, 'utf8')
  .replace(/^window\.COURSE\s*=\s*/, '')
  .replace(/;\s*$/, '');
const course = JSON.parse(raw);

fs.mkdirSync(outDir, { recursive: true });

/** 將單一單元物件轉成 markdown 素材檔內容 */
function unitToMarkdown(u) {
  const lines = [];
  lines.push(`# ${u.id}｜${u.title}`);
  if (u.subtitle) lines.push(`\n> ${u.subtitle}`);
  if (u.time) lines.push(`\n**時數**：${u.time}`);
  if (u.features?.length) lines.push(`\n**特色**：${u.features.join('、')}`);
  if (u.goals?.length) {
    lines.push('\n## 學習目標');
    for (const g of u.goals) lines.push(`- ${g}`);
  }
  if (u.principle) {
    lines.push('\n## 核心原則');
    lines.push(u.principle);
  }
  if (u.concepts?.length) {
    lines.push('\n## 概念講解（concepts）');
    for (const c of u.concepts) {
      lines.push(`\n### ${c.heading}${c.group ? `（分組：${c.group}）` : ''}`);
      lines.push(c.body || '');
    }
  }
  if (u.prompts?.length) {
    lines.push('\n## 提示詞（prompts）');
    for (const p of u.prompts) {
      lines.push(`\n### ${p.title}${p.kind ? `［${p.kind}］` : ''}`);
      if (p.note) lines.push(`> ${p.note}`);
      lines.push('```text');
      lines.push(p.text || '');
      lines.push('```');
    }
  }
  if (u.tasks?.length) {
    lines.push('\n## 實作任務（tasks）');
    for (const t of u.tasks) {
      // 任務欄位名稱不固定，逐一列出所有字串欄位
      const label = t.title || t.text || t.label || JSON.stringify(t);
      lines.push(`- **${t.id}**：${typeof label === 'string' ? label : ''}`);
      for (const [k, v] of Object.entries(t)) {
        if (['id', 'title', 'text', 'label'].includes(k)) continue;
        lines.push(`  - ${k}: ${typeof v === 'string' ? v : JSON.stringify(v)}`);
      }
    }
  }
  if (u.materials?.length) {
    lines.push('\n## 對應素材（materials）');
    for (const m of u.materials) lines.push(`- ${typeof m === 'string' ? m : JSON.stringify(m)}`);
  }
  return lines.join('\n');
}

// 匯出 day1 + day2 的所有單元（u1~u9）
const allUnits = [...(course.day1?.units || []), ...(course.day2?.units || [])];
for (const u of allUnits) {
  fs.writeFileSync(path.join(outDir, `${u.id}.md`), unitToMarkdown(u), 'utf8');
  console.log(`已匯出 ${u.id}.md（${u.title}）`);
}

// 匯出 superpowers 技能介紹區塊（對應 Hahow 第 9 章）
{
  const sp = course.superpowers;
  const lines = [`# superpowers｜${sp.title || '常用開發技能'}`];
  if (sp.intro) lines.push(`\n${sp.intro}`);
  for (const g of sp.groups || []) {
    lines.push(`\n## ${g.phase || g.title || ''}`);
    for (const s of g.skills || g.items || []) {
      lines.push(`\n### ${s.name || s.title || ''}`);
      for (const [k, v] of Object.entries(s)) {
        if (['name', 'title'].includes(k)) continue;
        lines.push(`- **${k}**：${typeof v === 'string' ? v : JSON.stringify(v)}`);
      }
    }
  }
  fs.writeFileSync(path.join(outDir, 'superpowers.md'), lines.join('\n'), 'utf8');
  console.log('已匯出 superpowers.md');
}

// 匯出測驗、素材清單與附錄（overview/sharedCase 一併帶出供口語稿參考）
fs.writeFileSync(path.join(outDir, 'quiz.md'), '# 測驗題\n\n```json\n' + JSON.stringify(course.quiz, null, 2) + '\n```', 'utf8');
fs.writeFileSync(path.join(outDir, 'materials.md'), '# 素材清單\n\n```json\n' + JSON.stringify(course.materials, null, 2) + '\n```', 'utf8');
fs.writeFileSync(path.join(outDir, 'appendix.md'), '# 附錄（術語 / FAQ / 驗收）\n\n```json\n' + JSON.stringify(course.appendix, null, 2) + '\n```', 'utf8');
fs.writeFileSync(path.join(outDir, 'overview.md'), '# 課程總覽與共用案例\n\n```json\n' + JSON.stringify({ meta: course.meta, overview: course.overview, sharedCase: course.sharedCase }, null, 2) + '\n```', 'utf8');
console.log('已匯出 quiz.md / materials.md / appendix.md / overview.md');
