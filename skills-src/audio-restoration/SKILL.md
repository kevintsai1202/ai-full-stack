---
name: audio-restoration
description: 修復真人錄製的講課／直播音軌音質問題 —— 底噪、句與句之間音量忽大忽小、換麥克風造成的段落不一致、齒音過重、低頻隆隆、削峰。採「先體檢、再提案、確認後執行」兩階段流程。觸發語包含：音質修復、修音、降噪、去雜音、音量不一致、拉平音量、講課錄音、直播音軌、錄音有底噪、聲音忽大忽小、audio restoration、denoise、loudness。不處理 TTS 合成語音（那屬 media-use），不做剪輯與配樂混音。
---

# audio-restoration

修復真人錄製的講課／直播人聲音軌。

## 使用時機

素材是**真人錄的**、且有下列任一症狀：底噪、音量忽大忽小、段落間音質不一致、齒音刺耳、低頻隆隆、爆音削峰。

TTS 合成語音不適用本技能 —— 它沒有底噪，問題型態完全不同。

## 環境準備

以下所有指令都依賴 `$HOME\.audio-restoration\.venv`。第一次使用需先建立：

```powershell
python -m venv "$HOME\.audio-restoration\.venv"
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pip install -r "$HOME\.claude\skills\audio-restoration\requirements.txt"
```

`requirements.txt` 含 `faster-whisper`（`analyze.py` 在找不到既有 `timeline.json` 時
會自動跑一次 ASR 產生詞級時間軸，這是必要依賴，不是選用）。AI 救援層
（`references/rescue-ai.md`）需要的 `deepfilternet`／PyTorch 不在此列，須另外
取得使用者明示同意後安裝。

## 兩階段流程

### 第一階段：體檢

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "$HOME\.claude\skills\audio-restoration\scripts\analyze.py" `
  --input "D:\path\to\lecture.mp4" `
  --work-dir "D:\GitHub\hahow-ai-full-stack\audio-restore\lecture"
```

產出 `report.json`（完整診斷，同時是修復後驗證的 before 基準，請勿刪除）與 `plan.json`（處理計畫）。

**必須把體檢摘要呈現給使用者**，特別是標記 `needs_ai_rescue` 的區段與 `issues` 清單。使用者可直接編輯 `plan.json` 調整任何參數，包括 zone 邊界。

### 第二階段：修復

使用者確認 `plan.json` 後才執行：

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "$HOME\.claude\skills\audio-restoration\scripts\restore.py" `
  --plan "D:\GitHub\hahow-ai-full-stack\audio-restore\lecture\plan.json" `
  --out "D:\path\to\lecture-restored.mp4"
```

**修復後必須請使用者聽 `preview-ab.wav`**（修復前 30 秒 → 修復後 30 秒）。四項自動驗證只能證明數值達標，聽感仍須人耳確認。

### 第三階段（可選）：美化 mastering

修復完成後，若想再加一層 podcast 質感（胸腔感、臨場感、能量密度），對**修復後的檔案**執行：

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "$HOME\.claude\skills\audio-restoration\scripts\master.py" `
  --input "D:\path\to\lecture-restored.mp4" `
  --out "D:\path\to\lecture-mastered.mp4" `
  --preset podcast
```

三個 preset（2026-08-21 以真實素材 AB 試聽校準）：

- `conservative`：僅 EQ 塑形 + 輕度壓縮，音色改變最小。
- `podcast`（預設）：EQ + 中度壓縮 + 高頻激勵，貼耳的 podcast 質感。
- `rich`：最重的 EQ／壓縮／激勵，廣播式厚實音色。

**必須先做 AB 試聽再全檔套用**：跑完後請使用者聽 `work/master-preview-ab.wav`（美化前 30 秒 → 美化後 30 秒），確認質感符合預期才算完成。美化是主觀美學選擇，數值驗證（響度、峰值）過關不代表使用者喜歡這個音色。

壓縮器在此合法的一句話理由：mastering 作用在已完成降噪與正規化的乾淨訊號上，壓縮不再有「頂高底噪」的副作用，而是刻意的美學選擇（能量密度＝貼耳感）—— 與修復鏈的禁令並不矛盾。

### 批次體檢

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "$HOME\.claude\skills\audio-restoration\scripts\batch.py" `
  --input-dir "D:\videos" --out-dir "D:\GitHub\hahow-ai-full-stack\audio-restore"
```

預設只做體檢。待使用者確認各檔的 `plan.json` 後，加 `--restore` 執行批次修復並產出前後指標總表：

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "$HOME\.claude\skills\audio-restoration\scripts\batch.py" --restore `
  --input-dir "D:\videos" --out-dir "D:\GitHub\hahow-ai-full-stack\audio-restore"
```

`--restore` 只處理已有 `plan.json` 的檔案，其餘一律跳過並列在總表中。批次不是繞過體檢閘門的後門。

## 設計約束（修改程式碼前必讀）

- **處理順序不可調換**：拉平 → 降噪 → highpass → deesser → 純線性正規化 → alimiter。afftdn 是門檻式運算，位準未拉平時門檻沒有單一意義。最終正規化刻意不用 loudnorm 套用（改為量測後直接算增益、套純線性 `volume`）——loudnorm 在低 LRA 素材上會靜默退回動態模式，把底噪連同人聲一起往上推，真實素材實測底噪因此不降反升 11.6dB。正規化採**迭代收斂**：alimiter 會吃掉高峰值素材的部分增益能量（02.mp4 實測單遍只到 −16.6，誤差 0.6 超容差），每輪套完重新量測、殘差 >0.25 就補一輪（上限 3 輪，見 `apply_loudnorm`）。
- **拉平只能用純增益**，禁用 `acompressor` / `dynaudnorm` / `speechnorm`。壓縮會頂高底噪，破壞降噪前提。
- **切句依據是 ASR 詞級時間軸**，不是 silencedetect（音量門檻會讓小聲句整句消失），也不是 SRT 字幕（時間戳為閱讀調整過）。
- **噪音採樣窗需雙重確認**：詞間 gap ≥ 0.6 秒且 silencedetect 亦判定靜音。採樣窗混入人聲會讓降噪把人聲當噪音消掉。
- **量測走單次全檔解碼的批次路徑**（`ar/bulk.py`）：逐句／逐窗 RMS 用 numpy 切片計算，不逐區間 spawn ffmpeg（舊路徑全流程約 2,800 次行程，1273 秒素材的 analyze 要跑近 30 分鐘；改造後 36 秒）。感知響度（LUFS）與真峰值只在整檔層級量測（`measure_overall` 單次 ebur128），句級一律用 RMS —— 句級是相對補償，RMS 與 LUFS 等價。
- **修復鏈禁壓縮，mastering 允許壓縮，兩者不矛盾**：修復鏈禁壓縮是因為壓縮會頂高底噪、破壞 afftdn 門檻的單一意義；mastering（`master.py`）作用在已降噪且正規化完成的乾淨訊號上，壓縮是刻意的美學選擇。但 mastering 的壓縮器只允許出現在 `ar/master.py` 的 preset 裡，不得回流進修復鏈。
- **`restore.py` 沒有 plan.json 不執行**，不得為了省事繞過閘門。且驗證的 before 端直接讀 `report.json`（schema v2）—— report.json 缺席或屬舊版會明確報錯要求重跑 analyze，不得退回對原始檔重量測。`plan.json` 不可增刪 utterances 筆數（修復後驗證須與 report.json 逐句配對，restore 有長度防護）。

## 常見失敗與處置

四項驗證分別是「降噪淨效果」「響度收斂」「真峰值」「拉平生效」（見 `ar/verify.py`）。

| 驗證失敗項 | 原因 | 處置 |
|---|---|---|
| 降噪淨效果未過 | 配對淨壓制 ≥0（未改善）或 >25dB（降噪過頭） | 檢查 `plan.json` 的 `denoise_db` 是否需調整；SNR≥35 的乾淨素材會自動回報「不適用」，不會被誤判為失敗 |
| 響度未收斂 | 目標響度未收斂到 ±0.5 LUFS | 檢查 restore 輸出的增益值與 alimiter 是否大量觸發（通常代表素材峰值過高，限幅吃掉了大半增益） |
| 真峰值超標 | 最終輸出真峰值 > −1.5 dBTP | 通常是輸出經 AAC 等有損重編所致，確認處理目標 −2.0 dBTP 的 0.5dB 餘裕是否被吃掉（例如編碼位元率過低） |
| 拉平未生效 | 句間 RMS 標準差（`utterance_rms_stdev`）未下降 | 檢查 `timeline.json` 是否涵蓋全檔（時長是否與媒體相符）、句數是否過少而無法反映拉平效果 |
| 找不到噪音採樣窗 | 整段都有人聲或 ASR 斷句過密 | 手動指定一段確定無人聲的區間 |

## AI 救援層

體檢標記 `needs_ai_rescue` 時，見 `references/rescue-ai.md`。需使用者明示同意才安裝 PyTorch 相關依賴。
