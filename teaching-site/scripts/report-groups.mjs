/**
 * 最終群組統計報告
 */
import fs from "fs";

const content = fs.readFileSync("course-data.js", "utf-8");
const window = {};
eval(content);
const course = window.COURSE;

for (const dayKey of ["day1", "day2"]) {
  const day = course[dayKey];
  if (!day) continue;
  for (const unit of day.units) {
    if (!unit.concepts) continue;
    const groups = new Map();
    for (const c of unit.concepts) {
      const g = c.group || c.heading;
      groups.set(g, (groups.get(g) || 0) + 1);
    }
    console.log(`${unit.id} (${unit.concepts.length} concepts -> ${groups.size} groups):`);
    for (const [g, count] of groups) {
      console.log(`  [${count}] ${g}`);
    }
  }
}
