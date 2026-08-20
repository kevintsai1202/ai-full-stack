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

產出 `report.json`（完整診斷）與 `plan.json`（處理計畫）。

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

- **處理順序不可調換**：拉平 → 降噪 → highpass → deesser → 純線性正規化 → alimiter。afftdn 是門檻式運算，位準未拉平時門檻沒有單一意義。最終正規化刻意不用 loudnorm 套用（改為量測後直接算增益、套純線性 `volume`）——loudnorm 在低 LRA 素材上會靜默退回動態模式，把底噪連同人聲一起往上推，真實素材實測底噪因此不降反升 11.6dB。
- **拉平只能用純增益**，禁用 `acompressor` / `dynaudnorm` / `speechnorm`。壓縮會頂高底噪，破壞降噪前提。
- **切句依據是 ASR 詞級時間軸**，不是 silencedetect（音量門檻會讓小聲句整句消失），也不是 SRT 字幕（時間戳為閱讀調整過）。
- **噪音採樣窗需雙重確認**：詞間 gap ≥ 0.6 秒且 silencedetect 亦判定靜音。採樣窗混入人聲會讓降噪把人聲當噪音消掉。
- **`restore.py` 沒有 plan.json 不執行**，不得為了省事繞過閘門。

## 常見失敗與處置

四項驗證分別是「降噪淨效果」「響度收斂」「真峰值」「拉平生效」（見 `ar/verify.py`）。

| 驗證失敗項 | 原因 | 處置 |
|---|---|---|
| 降噪淨效果未過 | 配對淨壓制 ≥0（未改善）或 >25dB（降噪過頭） | 檢查 `plan.json` 的 `denoise_db` 是否需調整；SNR≥35 的乾淨素材會自動回報「不適用」，不會被誤判為失敗 |
| 響度未收斂 | 目標響度未收斂到 ±0.5 LUFS | 檢查 restore 輸出的增益值與 alimiter 是否大量觸發（通常代表素材峰值過高，限幅吃掉了大半增益） |
| 真峰值超標 | 最終輸出真峰值 > −1.5 dBTP | 通常是輸出經 AAC 等有損重編所致，確認處理目標 −2.0 dBTP 的 0.5dB 餘裕是否被吃掉（例如編碼位元率過低） |
| 拉平未生效 | 句間響度標準差未下降 | 檢查 `timeline.json` 是否涵蓋全檔（時長是否與媒體相符）、句數是否過少而無法反映拉平效果 |
| 找不到噪音採樣窗 | 整段都有人聲或 ASR 斷句過密 | 手動指定一段確定無人聲的區間 |

## AI 救援層

體檢標記 `needs_ai_rescue` 時，見 `references/rescue-ai.md`。需使用者明示同意才安裝 PyTorch 相關依賴。
