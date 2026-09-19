/**
 * 00-course-orientation 旁白語音生成腳本（Fish Audio 聲音克隆）
 *
 * 用途：解析 course-package/00-course-orientation-旁白重錄稿.md，
 *       逐段呼叫 Fish Audio /v1/tts（沿用 D:\GitHub\fish-audio-test 專案的做法），
 *       以已訓練的「凱文大叔」系列聲音模型（reference_id）合成語音，
 *       輸出 wav 到 audio/course-orientation/。
 *
 * 執行方式（於專案根目錄）：
 *   node course-package/scripts/generate-orientation-tts.mjs
 *
 * 可選環境變數：
 *   FISH_API_KEY   Fish Audio API 金鑰（未設定時改讀 D:/GitHub/fish-audio-test/.env）
 *   FISH_VOICE_ID  聲音模型 ID（預設：凱文大叔快板 54e274adf1ee45b9adf7ad2c5c33aee1）
 *   FISH_MODEL     TTS 模型（預設：s2.1-pro-free，免費試用模型）
 *   FISH_SPEED     語速（預設 1.0）
 *
 * 需求：Node 18+（內建 fetch）、ffmpeg（mp3 → wav 轉檔）
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

/** 專案根目錄（本腳本位於 course-package/scripts/ 下兩層） */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

/** 旁白稿路徑 */
const SCRIPT_MD = join(ROOT, 'course-package', '00-course-orientation-旁白重錄稿.md');

/** 語音輸出目錄 */
const OUT_DIR = join(ROOT, 'audio', 'course-orientation');

/** Fish Audio API 基底網址 */
const FISH_API = 'https://api.fish.audio';

/** 聲音模型 ID：預設「凱文大叔快板」（帳號內使用次數最多的克隆聲音） */
const VOICE_ID = process.env.FISH_VOICE_ID || '54e274adf1ee45b9adf7ad2c5c33aee1';

/** TTS 模型：沿用 fish-audio-test 專案鎖定的免費模型 */
const MODEL = process.env.FISH_MODEL || 's2.1-pro-free';

/** 語速（1.0 = 自然語速） */
const SPEED = Number(process.env.FISH_SPEED) || 1.0;

/**
 * 取得 Fish Audio API 金鑰。
 * 優先序：環境變數 FISH_API_KEY → fish-audio-test 專案的 .env（API_KEY=...）。
 * @returns {string} API 金鑰
 */
function loadApiKey() {
  if (process.env.FISH_API_KEY) return process.env.FISH_API_KEY;
  const envPath = 'D:/GitHub/fish-audio-test/.env';
  if (existsSync(envPath)) {
    const match = readFileSync(envPath, 'utf-8').match(/^\s*(?:FISH_)?API_KEY\s*=\s*(\S+)/m);
    if (match) return match[1];
  }
  console.error('找不到 API 金鑰：請設定 FISH_API_KEY，或確認 D:/GitHub/fish-audio-test/.env 存在');
  process.exit(1);
}

/**
 * 解析旁白重錄稿，抽出每段的建議檔名與旁白文字。
 * 段落格式：「## NN｜slug」→「建議檔名：`NN-slug.wav`」→ 旁白段落 →「人工重錄備註：」。
 * @param {string} md 旁白稿全文
 * @returns {{filename: string, text: string}[]} 各段資料
 */
function parseSections(md) {
  const sections = [];
  // 以 "## " 切段（跳過檔頭與「交付檔案命名」節）
  for (const block of md.split(/^## /m).slice(1)) {
    const nameMatch = block.match(/建議檔名：`([^`]+)`/);
    if (!nameMatch) continue; // 「交付檔案命名」節沒有建議檔名，略過

    // 旁白文字 = 「參考長度」行之後、「人工重錄備註」之前的段落
    const textMatch = block.match(/參考長度：[^\n]*\n+([\s\S]*?)\n+人工重錄備註/);
    if (!textMatch) continue;
    const text = textMatch[1].trim();
    if (text) sections.push({ filename: nameMatch[1], text });
  }
  return sections;
}

/**
 * 呼叫 Fish Audio /v1/tts 以聲音模型合成一段語音（JSON 請求 + reference_id）。
 * @param {string} apiKey API 金鑰
 * @param {string} text 要合成的旁白文字
 * @returns {Promise<Buffer>} mp3 位元組
 */
async function synthesize(apiKey, text) {
  const resp = await fetch(`${FISH_API}/v1/tts`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
      model: MODEL, // Fish Audio 以 header 指定模型
    },
    body: JSON.stringify({
      text,
      reference_id: VOICE_ID, // 已訓練的克隆聲音模型
      format: 'mp3',
      mp3_bitrate: 128,
      normalize: true,
      latency: 'normal',
      prosody: { speed: SPEED, volume: 0 },
    }),
  });
  if (!resp.ok) {
    throw new Error(`Fish Audio 回傳 HTTP ${resp.status}：${await resp.text()}`);
  }
  return Buffer.from(await resp.arrayBuffer());
}

// ── 主流程 ────────────────────────────────────────────────

const apiKey = loadApiKey();
const sections = parseSections(readFileSync(SCRIPT_MD, 'utf-8'));
if (sections.length === 0) {
  console.error('旁白稿解析不到任何段落，請確認格式');
  process.exit(1);
}
mkdirSync(OUT_DIR, { recursive: true });
console.log(`共 ${sections.length} 段，聲音模型 ${VOICE_ID}，模型 ${MODEL}，語速 ${SPEED}`);

/** 逐段合成（串行呼叫，避免對免費模型併發限流） */
for (const { filename, text } of sections) {
  const wavPath = join(OUT_DIR, filename);
  const mp3Path = wavPath.replace(/\.wav$/, '.mp3');

  console.log(`▶ ${filename}（${text.length} 字）...`);
  const mp3 = await synthesize(apiKey, text);
  writeFileSync(mp3Path, mp3);

  // mp3 → wav（44.1kHz 單聲道），並在结尾補 0.5 秒靜音符合交付規格
  execFileSync('ffmpeg', [
    '-y', '-i', mp3Path,
    '-af', 'apad=pad_dur=0.5',
    '-ar', '44100', '-ac', '1',
    wavPath,
  ], { stdio: 'pipe' });

  const kb = (statSync(wavPath).size / 1024).toFixed(0);
  console.log(`  ✅ ${wavPath}（${kb} KB）`);
}

console.log('🎉 全部段落生成完畢');
