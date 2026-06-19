/**
 * 課程內容深度分析：統計每個 Unit 每個 group 的概念數、字數，
 * 並標記哪些是 AI 提示詞實作、哪些是程式碼範例、哪些是純概念。
 */
import fs from "fs";

const content = fs.readFileSync("course-data.js", "utf-8");
const window = {};
eval(content);
const course = window.COURSE;

let totalConcepts = 0;
let totalChars = 0;
let promptConcepts = 0;
let codeConcepts = 0;
let pureConcepts = 0;

for (const dk of ["day1", "day2"]) {
  const day = course[dk];
  if (!day) continue;
  for (const unit of day.units) {
    console.log("");
    console.log(`=== ${unit.id}: ${unit.title} ===`);
    if (!unit.concepts) continue;

    const groups = new Map();
    for (const co of unit.concepts) {
      const g = co.group || co.heading;
      if (!groups.has(g)) groups.set(g, []);
      groups.get(g).push(co);
    }

    let unitChars = 0;
    for (const [g, concepts] of groups) {
      const gChars = concepts.reduce((sum, co) => sum + (co.body?.length || 0), 0);
      unitChars += gChars;

      // 分類每個概念的類型
      const types = concepts.map((co) => {
        const h = co.heading || "";
        const b = co.body || "";
        const isPrompt = h.includes("提示詞") || h.includes("AI Agent");
        const hasCode = b.includes("```");
        const isExample = h.includes("範例") || h.includes("完整") || h.includes("步驟");
        if (isPrompt) return "PROMPT";
        if (hasCode && isExample) return "CODE-EXAMPLE";
        if (hasCode) return "CODE";
        return "CONCEPT";
      });

      const typeStr = types.join(", ");
      console.log(`  [${concepts.length} items, ${gChars} chars] ${g}`);
      concepts.forEach((co, i) => {
        const bodyLen = co.body?.length || 0;
        const type = types[i];
        const marker = type === "PROMPT" ? " ⚡" : type === "CODE-EXAMPLE" ? " 📋" : type === "CODE" ? " 💻" : " 📖";
        console.log(`    ${marker} ${co.heading} (${bodyLen} chars) [${type}]`);
        totalConcepts++;
        totalChars += bodyLen;
        if (type === "PROMPT") promptConcepts++;
        else if (type.includes("CODE")) codeConcepts++;
        else pureConcepts++;
      });
    }
    console.log(`  --- Unit total: ${unitChars} chars ---`);
  }
}

console.log("\n========== 總體統計 ==========");
console.log(`Total concepts: ${totalConcepts}`);
console.log(`Total chars: ${totalChars}`);
console.log(`  ⚡ AI Prompt concepts: ${promptConcepts}`);
console.log(`  💻 Code/Example concepts: ${codeConcepts}`);
console.log(`  📖 Pure concept: ${pureConcepts}`);
