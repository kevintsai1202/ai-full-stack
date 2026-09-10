import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 取得本腳本、課程內容與 repository 根目錄，讓 Windows 與其他環境都能解析路徑。
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const courseRoot = path.resolve(scriptDir, '..');
const repoRoot = path.resolve(courseRoot, '..', '..');

// 課程內容的必要入口與索引文件。
const requiredFiles = [
  'README.md',
  'day1/content.md',
  'day2/content.md',
  'supporting-docs.md',
  'materials/README.md',
  'materials/issueflow-project-assignment.md',
  'materials/linear-tool-brief.md',
  'materials/source-registry.md',
  '../course-outline/overview.md',
];

const errors = [];

// 讀取 UTF-8 文件，並在缺檔時收集可讀的驗證錯誤。
function readText(relativePath) {
  const absolutePath = path.resolve(courseRoot, relativePath);
  if (!fs.existsSync(absolutePath)) {
    errors.push(`缺少必要文件：${relativePath}`);
    return '';
  }
  return fs.readFileSync(absolutePath, 'utf8');
}

// 確認 Markdown 中的相對連結能對應到實際文件；課堂產生的 artifacts 目錄保留給學員執行後建立。
function validateMarkdownLinks(relativePath, text) {
  const sourcePath = path.resolve(courseRoot, relativePath);
  const linkPattern = /\]\(([^)]+)\)/g;
  let match;
  while ((match = linkPattern.exec(text)) !== null) {
    const target = match[1];
    if (/^(https?:|mailto:|#)/.test(target) || target.startsWith('artifacts/')) {
      continue;
    }
    const targetPath = path.resolve(path.dirname(sourcePath), target.split('#')[0]);
    if (!fs.existsSync(targetPath)) {
      errors.push(`Markdown 連結不存在：${relativePath} -> ${target}`);
    }
  }
}

// 確認課程單元、插圖需求與每一個圖片檔案都已完成。
function validateContentFile(relativePath) {
  const text = readText(relativePath);
  const unitCount = (text.match(/^## u-/gm) ?? []).length;
  const illustrationCount = (text.match(/^\*\*圖片需求 \(illustrations\)\*\*:/gm) ?? []).length;
  if (unitCount !== 2 || illustrationCount !== 2) {
    errors.push(`${relativePath} 單元數=${unitCount}、圖片需求數=${illustrationCount}，預期皆為 2`);
  }

  const imagePattern = /`([^`]+\.png)`/g;
  let match;
  while ((match = imagePattern.exec(text)) !== null) {
    const imageName = match[1];
    const candidates = [
      path.resolve(courseRoot, 'assets/diagrams', imageName),
      path.resolve(repoRoot, 'live-slides/assets', imageName),
      path.resolve(repoRoot, imageName),
      path.resolve(courseRoot, 'assets', imageName),
    ];
    if (!candidates.some((candidate) => fs.existsSync(candidate))) {
      errors.push(`${relativePath} 圖片不存在：${imageName}`);
    }
  }
}

for (const relativePath of requiredFiles) {
  const text = readText(relativePath);
  if (text) {
    validateMarkdownLinks(relativePath, text);
  }
}

validateContentFile('day1/content.md');
validateContentFile('day2/content.md');

// 每張流程圖都必須同時保留可維護的 SVG 原始檔與課堂使用的 PNG 成品。
const diagramDir = path.resolve(courseRoot, 'assets/diagrams');
const svgFiles = fs.existsSync(diagramDir)
  ? fs.readdirSync(diagramDir).filter((fileName) => fileName.endsWith('.svg'))
  : [];
for (const svgFile of svgFiles) {
  const pngFile = path.join(diagramDir, svgFile.replace(/\.svg$/, '.png'));
  if (!fs.existsSync(pngFile)) {
    errors.push(`流程圖缺少 PNG 成品：${path.basename(pngFile)}`);
  }
}

// 來源索引至少要涵蓋課程正文使用的主要外部資料與測試依據。
const sourceRegistry = readText('materials/source-registry.md');
const sourceCount = (sourceRegistry.match(/^\| REF-/gm) ?? []).length;
if (sourceCount < 14) {
  errors.push(`來源索引只有 ${sourceCount} 筆，預期至少 14 筆`);
}

// 四章各四項任務必須同時出現在課程正文與專案作業規格中，避免只寫在其中一處。
const taskIds = Array.from({ length: 16 }, (_, index) => {
  const day = index < 8 ? 1 : 2;
  const unit = index < 4 ? 'u1' : index < 8 ? 'u2' : index < 12 ? 'u1' : 'u2';
  const taskNumber = (index % 4) + 1;
  return `d${day}-${unit}-t${taskNumber}`;
});
const courseText = `${readText('day1/content.md')}\n${readText('day2/content.md')}`;
const assignmentText = readText('materials/issueflow-project-assignment.md');
for (const taskId of taskIds) {
  if (!courseText.includes(`\`${taskId}\``)) {
    errors.push(`課程正文缺少任務 ID：${taskId}`);
  }
  if (!assignmentText.includes(`\`${taskId}\``)) {
    errors.push(`專案作業規格缺少任務 ID：${taskId}`);
  }
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

console.log(`課程內容驗證通過：${requiredFiles.length} 個入口文件、${svgFiles.length} 組 SVG/PNG、${sourceCount} 筆來源`);
