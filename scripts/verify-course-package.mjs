// 用途：驗證課程素材包（course-package/）完整性
//   1. 每個章節目錄的小節檔案齊全（對照 Hahow 官方 9 章 36 單元 + 7 作業 + 達標解鎖章）
//   2. 每個小節檔案都有「## 口語稿」節，且口語稿內容 >= 500 字（中文字元）
//   3. 每個小節檔案都有「## 單元定位」與「## 教學素材」（ch09 與作業檔允許無「示範與提示詞」）
// 執行方式：node scripts/verify-course-package.mjs
// 結果：全部通過印 PASS 並 exit 0；任何缺漏列出後 exit 1

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pkg = path.join(root, 'course-package');

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

for (const [dir, files] of Object.entries(expected)) {
  for (const f of files) {
    const fp = path.join(pkg, dir, f);
    if (!fs.existsSync(fp)) {
      errors.push(`缺少檔案：${dir}/${f}`);
      continue;
    }
    fileCount++;
    const text = fs.readFileSync(fp, 'utf8');

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
if (errors.length) {
  console.error('\nFAIL：');
  for (const e of errors) console.error(' - ' + e);
  process.exit(1);
}
console.log('PASS：素材包完整，所有小節皆含口語稿。');
