// 用途：驗證課程素材包（course-package/）完整性
//   1. 每個章節目錄的小節檔案齊全（對照 Hahow 官方 9 章 36 單元 + 7 作業 + 達標解鎖章）
//   2. 每個小節檔案都有「## 口語稿」節，且口語稿內容 >= 500 字（中文字元）
//   3. 每個小節檔案都有「## 單元定位」與「## 教學素材」（ch09 與作業檔允許無「示範與提示詞」）
//   4. 每份教學／作業檔都具備逐步操作驗收區；每份旁白稿都具備邊念邊操作提示卡
// 執行方式：node scripts/verify-course-package.mjs
// 結果：全部通過印 PASS 並 exit 0；任何缺漏列出後 exit 1

import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pkg = path.join(root, 'course-package');

// 讀取 teaching-site 的 source of truth，確認素材包沒有對應到不存在的單元。
// course-data.js 是含尾逗號的 JS 物件字面值（JSON.parse 會失敗），比照網站載入方式在 vm 沙箱執行取得物件。
const courseRaw = fs.readFileSync(path.join(root, 'teaching-site', 'course-data.js'), 'utf8');
const courseSandbox = { window: {} };
vm.runInNewContext(courseRaw, courseSandbox, { filename: 'course-data.js' });
const course = courseSandbox.window.COURSE;
// 單元分散在 day1／day2／day3（延伸實戰：u9～u12）等鍵值下，統一收集所有帶 units 的區段。
const sourceUnits = new Map([
  ...Object.values(course)
    .filter((section) => section && Array.isArray(section.units))
    .flatMap((section) => section.units.map((u) => [u.id, u])),
  ['superpowers', course.superpowers],
]);

// 每個正式單元檔名與 teaching-site source 的對應；同一個 u* 可拆成多個授課小節。
const sourceByFile = {
  'ch01-env-and-ai-workflow/01-environment-setup.md': 'u1',
  'ch01-env-and-ai-workflow/02-project-scaffold.md': 'u1',
  'ch01-env-and-ai-workflow/03-when-to-use-ai.md': 'u1',
  'ch01-env-and-ai-workflow/04-why-crm.md': 'u1',
  'ch02-spring-mvc-rest-domain/01-spring-boot-mvc.md': 'u2',
  'ch02-spring-mvc-rest-domain/02-rest-api-design.md': 'u2',
  'ch02-spring-mvc-rest-domain/03-layered-architecture.md': 'u2',
  'ch02-spring-mvc-rest-domain/04-input-validation.md': 'u2',
  'ch02-spring-mvc-rest-domain/05-crm-domain-model.md': 'u2',
  'ch03-persistence-and-search/01-docker-database.md': 'u3',
  'ch03-persistence-and-search/02-flyway-schema-versioning.md': 'u3',
  'ch03-persistence-and-search/03-datasource-config.md': 'u3',
  'ch03-persistence-and-search/04-orm-entities.md': 'u3',
  'ch03-persistence-and-search/05-dynamic-query.md': 'u3',
  'ch03-persistence-and-search/06-crm-data-model-integration.md': 'u3',
  'ch04-security-jwt-openapi/01-openapi-swagger.md': 'u4',
  'ch04-security-jwt-openapi/02-exception-logging-aop.md': 'u4',
  'ch04-security-jwt-openapi/03-spring-security-jwt.md': 'u4',
  'ch04-security-jwt-openapi/04-crm-authorization.md': 'u4',
  'ch05-react-crm-workbench/01-react-project-setup.md': 'u5',
  'ch05-react-crm-workbench/02-jsx-basics.md': 'u5',
  'ch05-react-crm-workbench/03-frontend-visual-guidelines.md': 'u5',
  'ch05-react-crm-workbench/04-crm-workbench-design.md': 'u5',
  'ch06-spring-ai-sse-toolcalling/01-ai-chat-and-memory.md': 'u6',
  'ch06-spring-ai-sse-toolcalling/02-tool-calling.md': 'u6',
  'ch06-spring-ai-sse-toolcalling/03-sse-streaming.md': 'u6',
  'ch06-spring-ai-sse-toolcalling/04-business-value.md': 'u6',
  'ch07-rag-pgvector-mcp/01-rag-and-etl.md': 'u7',
  'ch07-rag-pgvector-mcp/02-mcp-and-skills.md': 'u7',
  'ch07-rag-pgvector-mcp/03-long-term-memory.md': 'u7',
  'ch07-rag-pgvector-mcp/04-crm-knowledge-base.md': 'u7',
  'ch08-capstone-demo-day/01-full-demo.md': 'u8',
  'ch08-capstone-demo-day/02-testing-strategy.md': 'u8',
  'ch09-dev-skills/01-superpowers.md': 'superpowers',
  'ch09-dev-skills/02-ui-ux-pro-max.md': 'extension-ui-ux-pro-max',
  'ch09-dev-skills/03-deep-memory.md': 'extension-deep-memory',
  'bonus-cloudflare-tunnel/01-cloudflare-tunnel.md': 'u9',
};

// 這兩個小節是素材包延伸內容，保留完整性與旁白驗證，但不冒充網站正式 source。
const packageOnlySources = new Set(['extension-ui-ux-pro-max', 'extension-deep-memory']);

// 期望的章節目錄 → 小節檔案清單（與 course-package/README.md 對照表一致）
const expected = {
  'ch01-env-and-ai-workflow': ['01-environment-setup.md', '02-project-scaffold.md', '03-when-to-use-ai.md', '04-why-crm.md', 'assignment-1.md'],
  'ch02-spring-mvc-rest-domain': ['01-spring-boot-mvc.md', '02-rest-api-design.md', '03-layered-architecture.md', '04-input-validation.md', '05-crm-domain-model.md', 'assignment-1.md'],
  'ch03-persistence-and-search': ['01-docker-database.md', '02-flyway-schema-versioning.md', '03-datasource-config.md', '04-orm-entities.md', '05-dynamic-query.md', '06-crm-data-model-integration.md', 'assignment-1.md'],
  'ch04-security-jwt-openapi': ['01-openapi-swagger.md', '02-exception-logging-aop.md', '03-spring-security-jwt.md', '04-crm-authorization.md', 'assignment-1.md'],
  'ch05-react-crm-workbench': ['01-react-project-setup.md', '02-jsx-basics.md', '03-frontend-visual-guidelines.md', '04-crm-workbench-design.md', 'assignment-1.md'],
  'ch06-spring-ai-sse-toolcalling': ['01-ai-chat-and-memory.md', '02-tool-calling.md', '03-sse-streaming.md', '04-business-value.md', 'assignment-1.md'],
  'ch07-rag-pgvector-mcp': ['01-rag-and-etl.md', '02-mcp-and-skills.md', '03-long-term-memory.md', '04-crm-knowledge-base.md', 'assignment-1.md'],
  'ch08-capstone-demo-day': ['01-full-demo.md', '02-testing-strategy.md'],
  'ch09-dev-skills': ['01-superpowers.md', '02-ui-ux-pro-max.md', '03-deep-memory.md'],
  'bonus-cloudflare-tunnel': ['01-cloudflare-tunnel.md'],
};

const errors = [];
let fileCount = 0;
let totalScriptChars = 0;
let narrationCount = 0;
let totalNarrationChars = 0;
const mappingPath = path.join(pkg, 'teaching-site-mapping.md');
const mappingText = fs.existsSync(mappingPath) ? fs.readFileSync(mappingPath, 'utf8') : '';
if (!mappingText) errors.push('缺少 course-package/teaching-site-mapping.md');

// 先驗證每個被拆出的教學小節仍然指向網站中存在的 source unit。
for (const [relative, sourceId] of Object.entries(sourceByFile)) {
  if (!sourceUnits.has(sourceId) && !packageOnlySources.has(sourceId)) errors.push(`${relative}：來源單元 ${sourceId} 不存在於 teaching-site/course-data.js`);
  if (!mappingText.includes(relative)) errors.push(`${relative}：未寫入 teaching-site-mapping.md`);
  const narrationRelative = relative.replace(/\.md$/, '-旁白稿.md');
  if (!mappingText.includes(narrationRelative)) errors.push(`${narrationRelative}：未寫入 teaching-site-mapping.md`);
}

for (const [dir, files] of Object.entries(expected)) {
  for (const f of files) {
    const fp = path.join(pkg, dir, f);
    if (!fs.existsSync(fp)) {
      errors.push(`缺少檔案：${dir}/${f}`);
      continue;
    }
    fileCount++;
    const text = fs.readFileSync(fp, 'utf8');

    const relative = path.relative(pkg, fp).replaceAll(path.sep, '/');
    if (!/^## 逐步操作與驗收/m.test(text)) errors.push(`${relative}：缺少「## 逐步操作與驗收」`);
    if (!/(預期結果與證據|交付證據|完成條件)/.test(text)) errors.push(`${relative}：逐步操作區缺少預期結果、交付證據或完成條件`);
    if (!/(失敗分流|銜接|完成條件|退件條件)/.test(text)) errors.push(`${relative}：逐步操作區缺少失敗分流、銜接、完成條件或退件條件`);
    if (sourceByFile[relative]) {
      const narrationPath = path.join(pkg, relative.replace(/\.md$/, '-旁白稿.md'));
      if (!fs.existsSync(narrationPath)) {
        errors.push(`${relative}：缺少旁白稿 ${path.basename(narrationPath)}`);
      } else {
        narrationCount++;
        const narration = fs.readFileSync(narrationPath, 'utf8');
        const narrationCjk = (narration.match(/[一-鿿]/g) || []).length;
        totalNarrationChars += narrationCjk;
        if (!/^## 邊念邊操作提示卡/m.test(narration)) errors.push(`${relative}：旁白稿缺少「## 邊念邊操作提示卡」`);
        if (!/(停|執行|畫面|保存|保留)/.test(narration)) errors.push(`${relative}：旁白提示卡缺少可執行的錄製節奏`);
        if (!/^建議檔名：/m.test(narration)) errors.push(`${relative}：旁白稿缺少「建議檔名」分段`);
        if ((narration.match(/^## \d+/gm) || []).length < 5) errors.push(`${relative}：旁白稿操作分段少於 5 段`);
        if (!/(我們現在|請執行|執行|打開|輸入|驗證|畫面|貼給 AI)/.test(narration)) errors.push(`${relative}：旁白稿缺少可邊念邊操作的指示語句`);
        if (narrationCjk < 800) errors.push(`${relative}：旁白稿僅 ${narrationCjk} 個中文字（門檻 800）`);
      }
    }

    // 必要節檢查
    if (!/^## 單元定位/m.test(text)) errors.push(`${dir}/${f}：缺少「## 單元定位」`);
    if (!/^## (教學素材|作業說明)/m.test(text)) errors.push(`${dir}/${f}：缺少「## 教學素材」或「## 作業說明」`);
    const m = text.match(/^## 口語稿\s*\n([\s\S]*?)(?=^## |\s*$(?![\s\S]))/m);
    if (!m) {
      errors.push(`${dir}/${f}：缺少「## 口語稿」`);
      continue;
    }
    // 口語稿中文字元數（不含標點與空白的粗略估計：計 CJK 字元）
    const cjk = (m[1].match(/[一-鿿]/g) || []).length;
    totalScriptChars += cjk;
    if (cjk < 500) errors.push(`${dir}/${f}：口語稿僅 ${cjk} 個中文字（門檻 500）`);
  }
}

console.log(`檢查檔案數：${fileCount}／預期 ${Object.values(expected).flat().length}`);
console.log(`口語稿合計中文字數：${totalScriptChars}`);
console.log(`旁白稿數：${narrationCount}／預期 ${Object.keys(sourceByFile).length}`);
console.log(`旁白稿合計中文字數：${totalNarrationChars}`);
if (errors.length) {
  console.error('\nFAIL：');
  for (const e of errors) console.error(' - ' + e);
  process.exit(1);
}
console.log('PASS：素材包完整，所有小節皆含口語稿。');
