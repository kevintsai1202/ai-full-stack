# AI 救援層（DeepFilterNet）

## 何時使用

僅在體檢報告標記 `needs_ai_rescue`（zone SNR < 10 dB）時考慮。這代表底噪已與人聲糾纏到 ffmpeg 頻域濾鏡無法分離的程度。

## 代價

DeepFilterNet 會**重新合成**語音波形，講師的音色會有可察覺的改變。對教學影片而言這是扣分項 —— 學員可能覺得「聲音怪怪的」。

因此規則是：**只對標記的那一個 zone 使用，不對整檔使用**。其餘區段仍走 ffmpeg 路徑，最後串接起來。音色的不一致比全檔輕微失真更難接受，故若超過半數 zone 需要救援，建議直接重錄。

## 安裝（需使用者明示同意）

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pip install deepfilternet
```

安裝會一併帶入 PyTorch（約 2GB）。正常修復路徑不需要這個依賴。

## 單區處理流程

1. 從原檔切出該 zone 的時間範圍，匯出為 48kHz WAV
2. 跑 DeepFilterNet 處理該段
3. **目前無自動整合功能，需手動流程**：用外部剪輯工具（如 Audacity、ffmpeg 手動
   拼接）把 DeepFilterNet 處理後的該段音訊，替換掉原始檔（或修復後輸出）對應
   時間段的音軌，取代原本 ffmpeg 濾鏡鏈的處理結果。`restore.py` 尚不支援
   per-zone 音源覆寫，未來版本可能會加入這項功能。
4. 該 zone 的 `denoise_db` 可設為 0 避免對已由 DeepFilterNet 處理過的段落
   二次降噪，但仍需按上一步手動完成音源替換，`restore.py` 本身不會自動套用

## 判斷是否值得

先聽 `preview-ab.wav`。若 ffmpeg 路徑的結果已經可接受，就不要動 AI —— 音色改變是不可逆的代價。
