# STT 本地方案基準測試 — 設計文件

- 日期：2026-07-09
- 狀態：已與開發者確認設計，進入實作
- 位置：`experiments/stt-benchmark/`

## 目標

用本專案真實中文素材，量化比較「本地 ONNX STT 方案」與工具包現行的 **WhisperX**，
用數據回答：**是否值得把 `video-skills-toolkit` 的轉錄後端從 WhisperX 換成 ONNX 模型（SenseVoice / Paraformer）。**

## 背景

`video-skills-toolkit`（`audio-to-subtitles`、`embedded-captions`、`hyperframes-media`）目前的本地轉錄：
- `embedded-captions/scripts/transcribe.cjs`：**WhisperX**（Whisper + wav2vec2 強制對齊）→ `whisper.cpp` fallback。
- 選 WhisperX 的理由是 wav2vec2 對齊提供 ~80ms 精度的字級時間戳，供逐字動畫使用。

公開數據（已查證）指出：中文場景 **SenseVoice-Small CER 7.81% vs Whisper-large-v3 20.02%**、速度快約 15×。
但這些是朗讀/長音檔基準，且時間戳精度未必達到工具包的動畫需求 → 需用**自己的素材**實測。

## 受測引擎

| 代號 | 引擎 / 模型 | 取得 | 角色 |
|---|---|---|---|
| `whisperx` | WhisperX，model `small`，`--language zh`，cpu/int8 | `uvx --from whisperx` | 基準（工具包現況） |
| `sensevoice` | SenseVoice-Small（int8） | sherpa-onnx 預訓練 ONNX | 候選 A（中文準確+速度） |
| `paraformer` | Paraformer-zh（帶時間戳） | sherpa-onnx 預訓練 ONNX | 候選 B（時間戳平衡） |

長音檔以 **silero-VAD** 切段後餵 SenseVoice / Paraformer（SenseVoice 單次僅吃短句）。

## 輸入（雙輸入設計）

1. **`videos/ai-crm-promo/audio/narration.wav`**（120s，有正確稿 `narration.txt`）
   → 量 **CER（準確率）**、RTFx、時間戳粒度。
2. **`幻燈片8.mp4`**（~21 分鐘真實演講；48kHz；mean −21dB 確有語音；**無正確稿**）
   → 量 **RTFx、字級時間戳漂移、長音檔 VAD 處理、逐字稿並排**。
   預設取**前 180 秒**對打（`config.json` 可調至全長）。
   因無正確稿且無法聆聽音檔，其 CER 改記為「**與 WhisperX 的一致度**」並明確標示為非準確率。

## 指標

1. **CER（字元錯誤率）**：字元級 Levenshtein / 參考長度。
   - **關鍵正規化**：SenseVoice/Paraformer 輸出**簡體**、正確稿為**繁體** → 先用 **OpenCC 統一字體**（雙方轉簡體），再去標點/空白/全半形統一，才計算。否則繁簡差異會灌爆 CER。
2. **字級時間戳漂移**：以 WhisperX 的 wav2vec2 對齊為**參考基準**，對齊各引擎詞/字序列後量平均與中位起點時間差；並標注各引擎給的是詞級/字級/段級粒度。
3. **速度 RTFx**：音訊時長 ÷ 實際處理牆鐘時間（單獨計時，排除下載/前處理）。
4. **質性**：各引擎逐字稿並排，供人工看中文可讀性與標點。

## 架構（`experiments/stt-benchmark/`，單一職責）

- `README.md` — 用途、安裝、執行、結果判讀、但書
- `package.json` — 依賴 `sherpa-onnx`
- `config.json` — 輸入、excerpt 秒數、模型路徑、引擎開關
- `scripts/prepare.mjs` — 下載並解壓 ONNX 模型至 `models/`（冪等）；ffmpeg 抽音、切段、轉 16k 單聲道
- `scripts/run-whisperx.mjs` — 跑 WhisperX（uvx）→ 正規化 `{text, words[]}`
- `scripts/run-sherpa.mjs` — sherpa-onnx-node 離線辨識（VAD+模型，參數化）→ 同格式 + 計時
  - Python fallback：node 綁定在 node24/win 無法載入時，改用 `sherpa-onnx` Python 跑同一批 ONNX 模型
- `scripts/metrics.py` — OpenCC 正規化 + CER + 漂移 + RTFx 彙整
- `scripts/benchmark.mjs` — 總指揮：跑三引擎 → 算指標 → 輸出 `results/summary.md` + `results/*.json`
- `models/`、`results/`（gitignore 模型與大檔）

## 資料流

```
輸入音檔 →(各引擎)→ 原始輸出 → 正規化 {text, words[]}
   → CER(vs narration.txt，OpenCC 統一字體)
   → 時間戳漂移(vs WhisperX words)
   → RTFx(dur / 處理時間)
   → results/summary.md 對照表
```

## 錯誤處理

- 模型下載失敗 / `uvx` 或 node 綁定不可用 → 明確報錯並**跳過該引擎、續跑其餘**。
- 所有下載、抽音、切段皆**冪等可重跑**（已存在則略過）。
- 沿用工具包的靜音幻覺意識：對近靜音輸入標注警告（本測試素材 −21dB 屬正常語音）。

## 但書（寫入 README）

- 時間戳漂移是「與 WhisperX 的一致度」，非絕對真值——但那是工具包現行黃金標準。
- CER 素材為 120s 乾淨口播、單一講者 → 結論為**方向性**；harness 支援加測 `human-raw-*.mp3` 與其他片段。
- CER 受正規化規則（數字、英文術語、繁簡）影響，規則於 `metrics.py` 明列。
- `幻燈片8.mp4` 無正確稿，不產生其可信 CER。

## 非目標（YAGNI）

- 不做網頁 / 圖表；只輸出 markdown 表 + JSON。
- 不改動 `video-skills-toolkit` 本體；換不換由本測試數據決定，屬後續獨立任務。
