/**
 * 批次產生 course-package 的 Fish Audio 旁白語音。
 *
 * 這支腳本沿用 D:\GitHub\fish-audio-test 的 Fish Audio /v1/tts 請求方式，
 * 將每份旁白稿依建議檔名拆成獨立音檔，方便後續對齊投影片時間軸。
 * 預設略過已經完成的 00-course-orientation；若要重新檢查它，可使用 --include-orientation。
 *
 * 執行方式（於專案根目錄）：
 *   node course-package/scripts/generate-course-tts.mjs --dry-run
 *   node course-package/scripts/generate-course-tts.mjs
 *
 * 可選環境變數：
 *   FISH_API_KEY  Fish Audio API 金鑰；未設定時讀取 fish-audio-test/.env
 *   FISH_VOICE_ID 聲音模型 ID
 *   FISH_MODEL    TTS 模型，預設 s2.1-pro-free
 *   FISH_SPEED    語速，預設 1.0
 *   FISH_FORCE    設為 1 時重新合成既有音檔
 */
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { basename, dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

/** 專案根目錄；本腳本位於 course-package/scripts/ 下兩層。 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

/** 旁白稿根目錄。 */
const COURSE_DIR = join(ROOT, 'course-package');

/** 除 00 開場外的批次語音輸出根目錄。 */
const AUDIO_ROOT = join(ROOT, 'audio', 'course-package');

/** Fish Audio API 基底網址。 */
const FISH_API = 'https://api.fish.audio';

/** 預設使用的凱文大叔聲音模型。 */
const VOICE_ID = process.env.FISH_VOICE_ID || '54e274adf1ee45b9adf7ad2c5c33aee1';

/** 沿用 fish-audio-test 專案鎖定的免費 TTS 模型。 */
const MODEL = process.env.FISH_MODEL || 's2.1-pro-free';

/** 語速；1.0 代表自然語速。 */
const SPEED = Number(process.env.FISH_SPEED) || 1.0;

/** 是否強制重新呼叫 Fish Audio。 */
const FORCE = process.env.FISH_FORCE === '1' || process.argv.includes('--force');

/** 是否只檢查稿件解析結果，不呼叫 Fish Audio。 */
const DRY_RUN = process.argv.includes('--dry-run');

/** 是否連同已經完成的 00-course-orientation 一起檢查。 */
const INCLUDE_ORIENTATION = process.argv.includes('--include-orientation');

/**
 * 取得 Fish Audio API 金鑰。
 * 優先讀取目前環境的 FISH_API_KEY，再退回 fish-audio-test 專案的 .env。
 * @returns {string} Fish Audio API 金鑰
 */
function loadApiKey() {
  if (process.env.FISH_API_KEY) return process.env.FISH_API_KEY;

  const envPath = 'D:/GitHub/fish-audio-test/.env';
  if (existsSync(envPath)) {
    const envText = readFileSync(envPath, 'utf-8');
    const match = envText.match(/^\s*(?:FISH_)?API_KEY\s*=\s*(\S+)/m);
    if (match) return match[1];
  }

  throw new Error('找不到 API 金鑰：請設定 FISH_API_KEY，或確認 fish-audio-test/.env 存在');
}

/**
 * 遞迴找出 course-package 下所有旁白稿。
 * @param {string} directory 要掃描的目錄
 * @returns {string[]} 旁白稿絕對路徑
 */
function findNarrationScripts(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const entryPath = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...findNarrationScripts(entryPath));
      continue;
    }
    if (entry.isFile() && entry.name.endsWith('旁白稿.md')) files.push(entryPath);
  }
  return files.sort((a, b) => a.localeCompare(b, 'zh-Hant'));
}

/**
 * 解析單份旁白稿的各段文字與建議檔名。
 * 支援一般旁白稿與 00 開場稿的參考長度／人工重錄備註格式。
 * @param {string} markdown 旁白稿全文
 * @returns {{ filename: string, text: string }[]} 可合成的段落
 */
function parseSections(markdown) {
  const sections = [];
  const blocks = markdown.split(/^## /m).slice(1);

  for (const block of blocks) {
    const filenameMatch = block.match(/建議檔名：\x60([^\x60]+)\x60/);
    if (!filenameMatch) continue;

    // 從建議檔名後開始取稿，避免把段落標題或參考長度送進 TTS。
    let text = block.slice(filenameMatch.index + filenameMatch[0].length);
    text = text.replace(/^\s*參考長度：[^\n]*\n?/m, '');
    text = text.split(/\n\s*人工重錄備註\s*:/i, 1)[0].trim();

    if (!text) throw new Error('段落 ' + filenameMatch[1] + ' 沒有可合成的旁白文字');
    sections.push({ filename: filenameMatch[1], text });
  }

  return sections;
}

/**
 * 根據來源稿件建立輸出目錄，避免不同單元的 01、02 檔名互相覆蓋。
 * @param {string} scriptPath 旁白稿絕對路徑
 * @returns {string} 音檔輸出目錄
 */
function getOutputDirectory(scriptPath) {
  const scriptRelative = relative(COURSE_DIR, scriptPath);
  const scriptDirectory = dirname(scriptRelative);
  const scriptName = basename(scriptRelative, extname(scriptRelative));
  const unitName = scriptName.replace(/-旁白(?:重錄)?稿$/, '');

  if (unitName === '00-course-orientation') return join(ROOT, 'audio', 'course-orientation');
  return join(AUDIO_ROOT, scriptDirectory, unitName);
}

/**
 * 呼叫 Fish Audio /v1/tts 合成一段 MP3。
 * @param {string} apiKey Fish Audio API 金鑰
 * @param {string} text 要合成的旁白文字
 * @returns {Promise<Buffer>} Fish Audio 回傳的 MP3 位元組
 */
async function synthesize(apiKey, text) {
  const response = await fetch(FISH_API + '/v1/tts', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer ' + apiKey,
      'Content-Type': 'application/json',
      model: MODEL,
    },
    body: JSON.stringify({
      text,
      reference_id: VOICE_ID,
      format: 'mp3',
      mp3_bitrate: 128,
      normalize: true,
      latency: 'normal',
      prosody: { speed: SPEED, volume: 0 },
    }),
  });

  if (!response.ok) {
    throw new Error('Fish Audio 回傳 HTTP ' + response.status + '：' + await response.text());
  }
  return Buffer.from(await response.arrayBuffer());
}

/**
 * 將 Fish Audio 產生的 MP3 轉成單聲道 44.1 kHz WAV，尾端補 0.5 秒靜音。
 * @param {string} mp3Path MP3 來源路徑
 * @param {string} wavPath WAV 輸出路徑
 */
function convertToWav(mp3Path, wavPath) {
  execFileSync('ffmpeg', [
    '-y',
    '-i', mp3Path,
    '-af', 'apad=pad_dur=0.5',
    '-ar', '44100',
    '-ac', '1',
    wavPath,
  ], { stdio: 'pipe' });
}

/**
 * 處理單一稿件的所有段落，支援中斷後繼續執行。
 * @param {string} apiKey Fish Audio API 金鑰
 * @param {string} scriptPath 旁白稿路徑
 * @returns {Promise<{total: number, generated: number, converted: number, skipped: number}>}
 */
async function processScript(apiKey, scriptPath) {
  const sections = parseSections(readFileSync(scriptPath, 'utf-8'));
  const outputDirectory = getOutputDirectory(scriptPath);
  mkdirSync(outputDirectory, { recursive: true });
  const relativeScript = relative(ROOT, scriptPath);
  const result = { total: sections.length, generated: 0, converted: 0, skipped: 0 };

  console.log('\n📄 ' + relativeScript + '：' + sections.length + ' 段');
  for (const { filename, text } of sections) {
    const wavPath = join(outputDirectory, filename);
    const mp3Path = wavPath.replace(/\.wav$/i, '.mp3');
    const hasMp3 = existsSync(mp3Path) && statSync(mp3Path).size > 0;
    const hasWav = existsSync(wavPath) && statSync(wavPath).size > 0;

    if (!FORCE && hasMp3 && hasWav) {
      result.skipped += 1;
      console.log('  ⏭️ ' + filename + ' 已存在');
      continue;
    }

    if (DRY_RUN) {
      console.log('  🔎 ' + filename + '（' + text.length + ' 字）');
      continue;
    }

    if (!hasMp3 || FORCE) {
      console.log('  ▶ ' + filename + '（' + text.length + ' 字）');
      writeFileSync(mp3Path, await synthesize(apiKey, text));
      result.generated += 1;
    } else {
      console.log('  🔄 ' + filename + ' 已有 MP3，補轉 WAV');
    }

    convertToWav(mp3Path, wavPath);
    result.converted += 1;
    console.log('    ✅ ' + wavPath + '（' + (statSync(wavPath).size / 1024).toFixed(0) + ' KB）');
  }

  return result;
}

/**
 * 執行批次合成主流程。
 */
async function main() {
  const scripts = findNarrationScripts(COURSE_DIR).filter((scriptPath) => (
    INCLUDE_ORIENTATION || !basename(scriptPath).startsWith('00-course-orientation-')
  ));

  if (scripts.length === 0) throw new Error('找不到符合條件的旁白稿');

  const parsed = scripts.map((scriptPath) => ({
    scriptPath,
    sections: parseSections(readFileSync(scriptPath, 'utf-8')),
  }));
  const total = parsed.reduce((sum, item) => sum + item.sections.length, 0);
  console.log('共 ' + scripts.length + ' 份旁白稿、' + total + ' 段；聲音模型 ' + VOICE_ID + '；模型 ' + MODEL + '；語速 ' + SPEED);

  if (DRY_RUN) {
    for (const { scriptPath, sections } of parsed) {
      console.log('  🔎 ' + relative(ROOT, scriptPath) + '：' + sections.length + ' 段');
    }
    console.log('Dry-run 完成，未呼叫 Fish Audio。');
    return;
  }

  const apiKey = loadApiKey();
  const summary = { total: 0, generated: 0, converted: 0, skipped: 0 };
  for (const { scriptPath } of parsed) {
    const result = await processScript(apiKey, scriptPath);
    for (const key of Object.keys(summary)) summary[key] += result[key];
  }

  console.log('\n🎉 完成：共 ' + summary.total + ' 段，新增合成 ' + summary.generated + ' 段，轉成 WAV ' + summary.converted + ' 段，略過 ' + summary.skipped + ' 段。');
}

main().catch((error) => {
  console.error('\n❌ ' + error.message);
  process.exitCode = 1;
});
