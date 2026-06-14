import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/**
 * 讀取 course-data.js，取得可驗證的課程資料。
 */
async function loadCourse() {
  const code = await fs.readFile(path.join(root, "course-data.js"), "utf8");
  const context = { window: {} };
  vm.createContext(context);
  vm.runInContext(code, context);
  return context.window.COURSE;
}

/**
 * 確認檔案存在。
 */
async function exists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

/**
 * 主驗證流程。
 */
async function main() {
  const course = await loadCourse();
  const errors = [];
  const requiredFiles = [
    "index.html",
    "styles.css",
    "course-data.js",
    "src/main.jsx",
    "src/App.jsx",
    "assets/illustrations/cover.png",
    "assets/illustrations/cover.svg"
  ];

  for (const file of requiredFiles) {
    if (!(await exists(path.join(root, file)))) errors.push(`missing file: ${file}`);
  }

  if (!course.title.includes("AI 賦能全端開發")) errors.push("course title mismatch");
  if (!Array.isArray(course.units) || course.units.length !== 8) errors.push("course must contain 8 units");

  for (const unit of course.units || []) {
    if (!unit.prompt || unit.prompt.length < 800) errors.push(`${unit.id} prompt is too thin`);
    if (!unit.principle || unit.principle.length < 80) errors.push(`${unit.id} principle is too thin`);
    if (!Array.isArray(unit.illustrations) || unit.illustrations.length < 3) errors.push(`${unit.id} needs hero, diagram and term illustrations`);
    if (!unit.prompt.includes("請接續") && unit.id !== "u1") errors.push(`${unit.id} prompt does not continue from previous unit`);
    for (const illustration of unit.illustrations || []) {
      const asset = path.join(root, "assets", "illustrations", illustration.name);
      if (!(await exists(asset))) errors.push(`${unit.id} missing illustration: ${illustration.name}`);
    }
  }

  if (errors.length) {
    console.error(errors.join("\n"));
    process.exitCode = 1;
    return;
  }

  const assetCount = (course.units || []).reduce((sum, unit) => sum + (unit.illustrations?.length || 0), 1);
  console.log(`OK: ${course.units.length} units, ${assetCount} referenced visual assets verified.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
