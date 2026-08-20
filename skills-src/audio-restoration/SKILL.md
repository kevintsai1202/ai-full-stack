---
name: audio-restoration
description: 修復真人錄製的講課／直播音軌音質問題 —— 底噪、句與句之間音量忽大忽小、換麥克風造成的段落不一致、齒音過重、低頻隆隆、削峰。採「先體檢、再提案、確認後執行」兩階段流程。觸發語包含：音質修復、修音、降噪、去雜音、音量不一致、拉平音量、講課錄音、直播音軌、錄音有底噪、聲音忽大忽小、audio restoration、denoise、loudness。不處理 TTS 合成語音（那屬 media-use），不做剪輯與配樂混音。
---

# audio-restoration

修復真人錄製的講課／直播人聲音軌。

## 使用時機

素材是**真人錄的**、且有下列任一症狀：底噪、音量忽大忽小、段落間音質不一致、齒音刺耳、低頻隆隆、爆音削峰。

TTS 合成語音不適用本技能 —— 它沒有底噪，問題型態完全不同。

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

- **處理順序不可調換**：拉平 → 降噪 → highpass → deesser → loudnorm → alimiter。afftdn 是門檻式運算，位準未拉平時門檻沒有單一意義。
- **拉平只能用純增益**，禁用 `acompressor` / `dynaudnorm` / `speechnorm`。壓縮會頂高底噪，破壞降噪前提。
- **切句依據是 ASR 詞級時間軸**，不是 silencedetect（音量門檻會讓小聲句整句消失），也不是 SRT 字幕（時間戳為閱讀調整過）。
- **噪音採樣窗需雙重確認**：詞間 gap ≥ 0.6 秒且 silencedetect 亦判定靜音。採樣窗混入人聲會讓降噪把人聲當噪音消掉。
- **`restore.py` 沒有 plan.json 不執行**，不得為了省事繞過閘門。

## 常見失敗與處置

| 驗證失敗項 | 原因 | 處置 |
|---|---|---|
| 底噪下降超過 25dB | 降噪過頭，人聲被削 | 調低 `plan.json` 的 `denoise_db` 重跑 |
| 響度未收斂 | loudnorm 兩段式量測異常 | 檢查中繼檔是否有無聲段落 |
| 拉平未生效 | 句級增益被限幅吃光 | 提高 `max_gain_db`，或先手動分割錄音條件差異過大的段落 |
| 找不到噪音採樣窗 | 整段都有人聲或 ASR 斷句過密 | 手動指定一段確定無人聲的區間 |

## AI 救援層

體檢標記 `needs_ai_rescue` 時，見 `references/rescue-ai.md`。需使用者明示同意才安裝 PyTorch 相關依賴。
