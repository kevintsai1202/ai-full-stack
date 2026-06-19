/**
 * 驗證 group 欄位只出現在正確位置（heading 行之後）
 */
import fs from "fs";

const content = fs.readFileSync("course-data.js", "utf-8");
const lines = content.split("\n");
let goodInserts = 0;
let badInserts = 0;

for (let i = 0; i < lines.length; i++) {
  const line = lines[i].trim();
  if (line.startsWith('"group"')) {
    const prevLine = lines[i - 1]?.trim() || "";
    if (prevLine.startsWith('"heading"')) {
      goodInserts++;
    } else {
      badInserts++;
      console.log(`Line ${i + 1} BAD insert after: ${prevLine.substring(0, 100)}`);
    }
  }
}

console.log(`\nGood inserts: ${goodInserts}`);
console.log(`Bad inserts: ${badInserts}`);
console.log(`Total: ${goodInserts + badInserts}`);
