# audio-restoration 效能優化計畫（方案 1+2+4）

- 日期：2026-08-20
- 前置：技能本體已完成（見 2026-08-20-audio-restoration.md），104 tests 全綠
- 目標：幻燈片8.mp4（1273s）全流程 55–60 分 → 約 9 分
- 使用者已批准：方案 1（verify 的 before 讀 report.json）、方案 2（量測改單次解碼 + numpy 聚合）、方案 4（診斷/指紋/殘響合併讀取）

## 瓶頸實測歸因

全流程約 **2,800 次**獨立 ffmpeg 行程，每次含行程啟動（Windows 0.2–0.5s）＋ -ss 定位＋短窗解碼：

| 呼叫點 | 次數 | 說明 |
|---|---|---|
| analyze：measure_utterances / measure_intervals | 344+79 | 逐句/逐窗 ebur128+astats |
| analyze：zone_reverb_slope 的 read_samples | ~340 | 每個語句尾 0.3s 各一次 |
| analyze：指紋 read_samples | 79 | 每個噪音窗一次 |
| restore collect_metrics（before + after 各一輪） | ~1,190×2 | overall 1 + 句 LUFS 344 + 窗 RMS 79×2 + 句 RMS 344/檔——**每句量兩次** |

純運算量極小：21 分鐘音訊全檔解碼一遍僅數十秒。

## 核心設計決策

1. **句級位準全面改 RMS**（原句級用 LUFS、區級早已用 RMS——本來就混搭）。
   句級增益與句間標準差都只在乎相對位準，RMS 等價且可純 numpy 計算。
   欄位改名：`utterance_lufs_stdev` → `utterance_rms_stdev`；report utterances 移除 `lufs` 欄。
   LUFS/真峰值只剩「整檔 integrated」需要 → 全檔單次 ebur128。
2. **雙 buffer**：
   - 原生取樣率 buffer（48kHz×1273s×4B≈244MB）：RMS/峰值量測。afftdn 的 nf 是絕對位準，16k 重採樣會截掉高頻噪音能量、讓嘶聲型底噪 RMS 偏低 → 必須原生率。
   - 16kHz buffer：指紋/診斷/殘響（ZONE_THRESHOLD 在 16k 下校準，不可換率）。
3. **report.json schema_version 1 → 2**：utterances 改 `{start,end,rms_db,peak_db}`；noise_windows 加 `rms_db`；新增 `overall: {integrated_lufs, true_peak_db}`。plan.json 同步 bump（量測路徑變了，舊 plan 應重新 analyze）。
4. **verify 的 before 從 report.json 組裝**（`metrics_from_report`）：方案 2 統一量測路徑後，report 的數字與重算完全相同，直接讀零風險。after 仍實測輸出檔。
5. **不動 render.py／preview.py**（非瓶頸，單一任務原則）。measure.py 的 `measure_interval` 保留（cookbook 引用、等價性測試基準），主流程不再呼叫。

## 任務拆解

### Task A：新模組 ar/bulk.py + 等價性測試
- `AudioBuffer`（samples: np.float32 mono、sample_rate、duration）＋ `load_audio(path, sample_rate=None)`：單次 ffmpeg 全檔解碼（None＝原生率）。
- `slice_samples(buf, start, end)`、`rms_db(samples)`（靜音回 SILENT_FLOOR）、`peak_db(samples)`。
- `measure_overall(path) -> (integrated_lufs, true_peak_db)`：全檔單次 ebur128（重用 measure.py 的 regex 與 MeasurementParseError 語意）。
- 等價性測試：合成 WAV 上 numpy rms_db 對 astats `RMS level dB` 差 < 0.1dB；peak 同理；measure_overall 對 measure_interval(全檔) 相符。

### Task B：analyze.py 管線改造（方案 2+4 的 analyze 側）
- 開頭各 load 一次原生率 buf 與 16k buf；所有逐句/逐窗/指紋/殘響/診斷量測改為切片。
- `diagnose_zone`、`zone_reverb_slope` 改收 16k buffer（不再收 path、不再 read_samples）。
- `compute_utterance_gains` 改收逐句 rms_db（list[float]），residual 基於 RMS。
- report schema v2（含 overall 量測）；plan_io 同步。
- 既有測試對應改寫；行為守恆項：zone 切點、denoise 分級、noise_floor 應與舊路徑一致（RMS 等價性由 Task A 保證）。

### Task C：metrics.py / verify.py / restore.py（方案 1+2 的 verify 側）
- `collect_metrics` 重寫：原生率 buf 一次 + measure_overall 一次，句/窗 RMS 全 numpy；`utterance_rms_stdev` 取代 LUFS 版。
- 新增 `metrics_from_report(report) -> dict`：組出與 collect_metrics 同構的 before。
- restore.py：before 改讀 report.json（缺檔或 schema 過舊 → 明確報錯要求重跑 analyze，不得退回重算原始檔——量測路徑不同會讓比對失真）。
- verify.py `_check_flattening` 改用 `utterance_rms_stdev`。

### Task D：文件同步 + 真實素材驗收 + 部署
- SKILL.md、design 文件、filter-cookbook.md 補記：量測架構（單次解碼 + numpy）、句級 RMS 決策、schema v2。
- 幻燈片8.mp4 重跑 analyze + restore 完整計時，比對四項驗證仍全過；記錄前後耗時於 cookbook。
- deploy.ps1 重新部署；提交（僅 `git add skills-src/audio-restoration` 與明確授權路徑）。

## 驗收標準

1. 104+ tests 全綠（含新等價性測試）。
2. 幻燈片8.mp4：analyze ≤ 5 分、restore（含驗證）≤ 6 分。
3. 四項驗證全過；RMS 類指標與舊 verify.json 相差 < 0.5dB（stdev 因改 RMS 數值不同屬預期，但趨勢一致：after < before）。

## 實施結果（2026-08-21）

### 實測數字（幻燈片8.mp4，1273 秒，344 句／79 窗／5 zone）

| 階段 | 改造前 | 改造後 | 驗收標準 |
|---|---|---|---|
| analyze | 29.5 分 | **36.2 秒** | ≤ 5 分（大幅超越） |
| restore（含驗證） | 28 分 | **173.5 秒** | ≤ 6 分（達成） |
| 全流程 | 55–60 分 | **約 3.5 分鐘（約 16 倍）** | — |

analyze 全程的 ffmpeg 行程數從約 2,800 次降到個位數。

### 驗收標準達成情況

1. **133 tests 全綠**（含 bulk.py 等價性測試：numpy `rms_db` 對 astats
   `RMS level dB` 實測差 < 0.00001 dB）。
2. 耗時遠低於標準（見上表）。
3. 四項驗證全過：降噪淨效果＝乾淨素材不適用（before SNR 47.6 dB ≥ 35）、
   整體 −16.30 LUFS（目標 −16 ± 0.5）、真峰值 −1.90 dBTP（上限 −1.5）、
   句間 RMS 標準差 2.48 → 1.54（after < before，趨勢與舊 LUFS 版一致）。

### 追加修正（計畫外、實施中發現的既存 bug）

`render.concat_zones` 的 concat 清單改寫**絕對路徑**：concat demuxer 會把
清單裡的相對路徑相對於**清單檔所在目錄**解析（而非行程工作目錄），導致
開檔失敗。與效能無關，但批次路徑重跑真實素材時暴露，一併修正。
