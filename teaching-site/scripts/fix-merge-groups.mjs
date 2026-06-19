/**
 * 修正重複 heading（常見錯誤與排查、AI 提示詞練習）的 group 歸屬。
 * 策略：讓這些共用 heading 合併到它們前一個概念所屬的群組中。
 */
import fs from "fs";

const DATA_PATH = "course-data.js";
const content = fs.readFileSync(DATA_PATH, "utf-8");
const window = {};
eval(content);
const course = window.COURSE;

/** 需要修正的 heading 列表：將其 group 改為「前一個概念的 group」 */
const MERGE_HEADINGS = new Set(["常見錯誤與排查", "AI 提示詞練習"]);

const fixes = [];

for (const dayKey of ["day1", "day2"]) {
  const day = course[dayKey];
  if (!day) continue;
  for (const unit of day.units) {
    if (!unit.concepts) continue;
    for (let i = 0; i < unit.concepts.length; i++) {
      const c = unit.concepts[i];
      if (MERGE_HEADINGS.has(c.heading) && i > 0) {
        const prevGroup = unit.concepts[i - 1].group;
        if (c.group !== prevGroup) {
          fixes.push({
            unit: unit.id,
            index: i,
            heading: c.heading,
            oldGroup: c.group,
            newGroup: prevGroup,
          });
        }
      }
    }
  }
}

console.log(`找到 ${fixes.length} 個需要修正的概念:`);
fixes.forEach((f) => console.log(`  ${f.unit}[${f.index}] "${f.heading}": "${f.oldGroup}" -> "${f.newGroup}"`));

// 執行替換：在檔案中逐一找到目標 group 行並修改
let fileContent = fs.readFileSync(DATA_PATH, "utf-8");
const lines = fileContent.split("\n");

// 追蹤 concepts 陣列內的 heading 出現次數
const headingCounter = {};
let insideConcepts = false;
let depth = 0;

// 收集每個修正目標的 (heading, occurrenceIndex) 和新 group
// 先為每個修正標記它是該 heading 在 concepts 中的第幾次出現
const fixTargets = new Map(); // key = "heading:occurrence", value = newGroup

// 重新遍歷原始物件確認出現順序
const headingOccurrence = {};
for (const dayKey of ["day1", "day2"]) {
  const day = course[dayKey];
  if (!day) continue;
  for (const unit of day.units) {
    if (!unit.concepts) continue;
    for (let i = 0; i < unit.concepts.length; i++) {
      const h = unit.concepts[i].heading;
      headingOccurrence[h] = (headingOccurrence[h] || 0) + 1;
      const occ = headingOccurrence[h];
      const fix = fixes.find(
        (f) => f.unit === unit.id && f.index === i && f.heading === h
      );
      if (fix) {
        fixTargets.set(`${h}:::${occ}`, fix.newGroup);
      }
    }
  }
}

// 現在逐行掃描檔案，追蹤 heading 出現次數，定位要修改的 group 行
const headingSeen = {};
const newLines = [];
let fixedCount = 0;

for (let i = 0; i < lines.length; i++) {
  const trimmed = lines[i].trim();
  const headingMatch = trimmed.match(/^"heading":\s*"(.+?)"/);
  if (headingMatch) {
    const h = headingMatch[1];
    headingSeen[h] = (headingSeen[h] || 0) + 1;
  }

  // 檢查這行是否是 group 行，且前一行是需要修正的 heading
  if (trimmed.startsWith('"group"') && i > 0) {
    const prevTrimmed = lines[i - 1].trim();
    const prevMatch = prevTrimmed.match(/^"heading":\s*"(.+?)"/);
    if (prevMatch) {
      const h = prevMatch[1];
      const occ = headingSeen[h] || 0;
      const key = `${h}:::${occ}`;
      if (fixTargets.has(key)) {
        const newGroup = fixTargets.get(key);
        const indent = lines[i].match(/^(\s*)/)[1];
        newLines.push(`${indent}"group": "${newGroup}",`);
        fixedCount++;
        console.log(`  Fixed line ${i + 1}: "${h}" -> "${newGroup}"`);
        continue;
      }
    }
  }

  newLines.push(lines[i]);
}

fs.writeFileSync(DATA_PATH, newLines.join("\n"), "utf-8");
console.log(`\n✅ 共修正 ${fixedCount} 行 group 欄位`);
