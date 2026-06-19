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
    "app.js",
    "assets/illustrations/cover.png",
    "assets/illustrations/cover.svg"
  ];

  for (const file of requiredFiles) {
    if (!(await exists(path.join(root, file)))) errors.push(`missing file: ${file}`);
  }

  if (!course.title.includes("AI 賦能全端開發")) errors.push("course title mismatch");

  // 畫面真正渲染來源：day1.units + day2.units
  const allUnits = [...(course.day1?.units || []), ...(course.day2?.units || [])];
  if (allUnits.length !== 8) errors.push(`course must contain 8 units (got ${allUnits.length})`);

  for (const unit of allUnits) {
    if (!unit.principle || unit.principle.length < 80) errors.push(`${unit.id} principle is too thin`);
    if (!Array.isArray(unit.illustrations) || unit.illustrations.length < 3) errors.push(`${unit.id} needs hero, diagram and term illustrations`);

    const prompts = Array.isArray(unit.prompts) ? unit.prompts : [];
    const builds = prompts.filter((p) => (p.kind || "build") === "build");
    const verifies = prompts.filter((p) => p.kind === "verify");
    if (!builds.length) errors.push(`${unit.id} needs at least one build prompt`);
    if (!verifies.length) errors.push(`${unit.id} needs at least one verify prompt`);
    // 提示詞不得貼程式碼（三反引號）
    for (const p of prompts) {
      if (p.text && p.text.includes("```")) errors.push(`${unit.id} prompt "${p.title}" must not contain code fences`);
    }

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

  const assetCount = allUnits.reduce((sum, unit) => sum + (unit.illustrations?.length || 0), 1);
  console.log(`OK: ${allUnits.length} units, ${assetCount} referenced visual assets verified.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
