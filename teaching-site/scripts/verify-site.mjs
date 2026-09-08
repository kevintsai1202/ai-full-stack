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
    "assets/videos/00-course-orientation.mp4",
    "assets/illustrations/cover.webp",
    "assets/illustrations/cover.svg"
  ];

  for (const file of requiredFiles) {
    if (!(await exists(path.join(root, file)))) errors.push(`missing file: ${file}`);
  }

  if (!course.title.includes("AI 賦能全端開發")) errors.push("course title mismatch");
  if (!course.courseVideo?.src || !course.courseVideo?.poster) errors.push("course video metadata is incomplete");

  // 畫面真正渲染來源：day1.units + day2.units + day3.units（延伸章）
  const allUnits = [...(course.day1?.units || []), ...(course.day2?.units || []), ...(course.day3?.units || [])];
  // 8 個核心單元 + 延伸章 4 單元（u9 上線實戰 + u10~u12 延伸部署）
  if (allUnits.length !== 12) errors.push(`course must contain 12 units (got ${allUnits.length})`);

  for (const unit of allUnits) {
    if (!unit.principle || unit.principle.length < 80) errors.push(`${unit.id} principle is too thin`);
    if (!Array.isArray(unit.illustrations) || unit.illustrations.length < 3) errors.push(`${unit.id} needs hero, diagram and term illustrations`);
    if (!unit.scenario?.title || !unit.scenario?.description || !unit.scenario?.image) errors.push(`${unit.id} needs a scenario title, description and image`);
    if (!Array.isArray(unit.scenario?.examples) || unit.scenario.examples.length < 2) errors.push(`${unit.id} needs at least 2 scenario examples`);
    if (!Array.isArray(unit.scenario?.technologyRoles) || unit.scenario.technologyRoles.length < 2) errors.push(`${unit.id} needs at least 2 scenario technology roles`);
    for (const example of unit.scenario?.examples || []) {
      if (!example.title || !example.description) errors.push(`${unit.id} scenario example is incomplete`);
    }
    for (const role of unit.scenario?.technologyRoles || []) {
      if (!role.name || !role.role) errors.push(`${unit.id} scenario technology role is incomplete`);
    }

    // 每個小節都要有可讓初學者先讀懂的名詞解釋，避免只剩術語圖片。
    const glossary = course.glossary?.[unit.id] || [];
    if (!Array.isArray(glossary) || glossary.length < 3) errors.push(`${unit.id} needs at least 3 glossary terms`);
    for (const item of glossary) {
      if (!item.term || !item.meaning || !item.example) errors.push(`${unit.id} glossary item is incomplete`);
    }
    // 每章都要有完整的基礎到進階順序，避免只補單一術語而破壞整體學習脈絡。
    const learningOrder = course.glossaryLearningOrder?.[unit.id] || [];
    const glossaryTerms = glossary.map((item) => item.term);
    const glossaryTermSet = new Set(glossaryTerms);
    const learningOrderSet = new Set(learningOrder);
    if (!Array.isArray(learningOrder) || learningOrder.length !== glossary.length) {
      errors.push(`${unit.id} glossary learning order must cover every term`);
    }
    if (learningOrderSet.size !== learningOrder.length) errors.push(`${unit.id} glossary learning order contains duplicates`);
    for (const term of glossaryTerms) {
      if (!learningOrderSet.has(term)) errors.push(`${unit.id} glossary learning order is missing: ${term}`);
    }
    for (const term of learningOrder) {
      if (!glossaryTermSet.has(term)) errors.push(`${unit.id} glossary learning order references unknown term: ${term}`);
    }

    // 前置名詞必須在學習路徑更前面出現，避免畫面提示學生先理解一個尚未解釋的詞。
    const glossaryIndexes = new Map(learningOrder.map((term, index) => [term, index]));
    glossary.forEach((item) => {
      for (const prerequisite of item.prerequisites || []) {
        const prerequisiteIndex = glossaryIndexes.get(prerequisite);
        const itemIndex = glossaryIndexes.get(item.term);
        if (prerequisiteIndex === undefined) errors.push(`${unit.id} glossary prerequisite is missing: ${item.term} -> ${prerequisite}`);
        else if (itemIndex === undefined || prerequisiteIndex >= itemIndex) errors.push(`${unit.id} glossary prerequisite must appear first: ${item.term} -> ${prerequisite}`);
      }
    });
    const learningPaths = course.glossaryLearningPaths?.[unit.id] || [];
    if (!Array.isArray(learningPaths) || learningPaths.length < 2) errors.push(`${unit.id} needs at least 2 glossary learning paths`);

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

    if (unit.scenario?.image) {
      const scenarioAsset = path.join(root, "assets", "illustrations", unit.scenario.image);
      if (!(await exists(scenarioAsset))) errors.push(`${unit.id} missing scenario image: ${unit.scenario.image}`);
    }
  }

  if (errors.length) {
    console.error(errors.join("\n"));
    process.exitCode = 1;
    return;
  }

  const assetCount = allUnits.reduce((sum, unit) => sum + (unit.illustrations?.length || 0) + (unit.scenario?.image ? 1 : 0), 1);
  console.log(`OK: ${allUnits.length} units, ${assetCount} referenced visual assets verified.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
