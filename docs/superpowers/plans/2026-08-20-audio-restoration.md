# audio-restoration 技能實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 `audio-restoration` 技能，以「先體檢、再提案、確認後執行」的兩階段流程修復真人講課／直播音軌的底噪、音量不一致、齒音、低頻隆隆與削峰。

**Architecture:** 純 Python 編排 + ffmpeg 執行。`analyze.py` 用 ASR 詞級時間軸切語句、以 FFT 頻譜指紋分區、逐區診斷後產出 `plan.json`；`restore.py` 只吃 `plan.json`，依「先拉平、再降噪、後響度」的固定順序組裝 ffmpeg 濾鏡鏈並執行，最後自動重跑體檢做四指標驗證。所有訊號分析為純函式，用合成音檔即可測試，不需要真實素材。

**Tech Stack:** Python 3.11+、numpy、ffmpeg 8.0（`afftdn` / `deesser` / `highpass` / `loudnorm` / `alimiter` / `silencedetect` / `ebur128` / `astats`）、faster-whisper（僅在缺 `timeline.json` 時呼叫）、pytest。

---

## Global Constraints

- **原始碼位置**：`d:\GitHub\hahow-ai-full-stack\skills-src\audio-restoration\`（進版控）。`~/.claude` 不是 git repo，禁止直接在全域技能目錄開發。部署由 `deploy.ps1` 複製過去。
- **執行環境**：Windows 11 + PowerShell 7。所有指令須 PowerShell 7 相容。
- **venv**：`~/.audio-restoration/.venv`，只裝 `numpy`、`pytest`。**禁止引入 PyTorch**（AI 救援層為使用者明示同意後的獨立安裝，不屬本計畫範圍）。
- **註解**：所有函式須有中文函式級註解（docstring）；重要變數與物件亦須中文註解。此為專案 CLAUDE.md 強制規範。
- **響度目標**：`--target` 參數，預設 `-16.0`（LUFS）。真峰值固定 `-1.5` dBTP，`LRA=11`。
- **工作位準**：拉平階段的共同工作位準固定 `-20.0` LUFS。
- **單句增益上限**：預設 `6.0` dB，參數名 `max_gain_db`。
- **相鄰句增益差上限**：預設 `3.0` dB，參數名 `max_step_db`。
- **增益交界斜坡**：`200` ms。
- **噪音採樣窗最小長度**：`0.6` 秒。
- **timeline.json 時長容差**：`0.5` 秒。
- **指紋距離門檻初值**：`0.15`（餘弦距離，1/3 八度頻帶正規化後）。Task 14 以真實素材校準。
- **AI 救援觸發**：zone SNR < `10.0` dB。
- **處理順序不可調換**：拉平 → 降噪 → highpass → deesser → loudnorm → alimiter。
- **拉平只能用純增益**，禁止使用 `acompressor` 等壓縮器（壓縮會頂高底噪，破壞後續降噪的訊噪比前提）。
- **影片換揉**：固定 `-c:v copy -c:a aac -b:a 192k`，影像軌不得重編。
- **本專案產出位置**：`d:\GitHub\hahow-ai-full-stack\audio-restore\<檔名主幹>\`。

---

## File Structure

```
skills-src/audio-restoration/
├── SKILL.md                      # 技能說明（Task 13）
├── deploy.ps1                    # 部署到 ~/.claude/skills/（Task 13）
├── requirements.txt              # numpy, pytest（Task 0）
├── scripts/
│   ├── analyze.py                # 體檢 CLI 入口（Task 7）
│   ├── restore.py                # 修復 CLI 入口（Task 12）
│   └── ar/                       # 核心套件
│       ├── __init__.py
│       ├── ffmpeg_io.py          # ffmpeg/ffprobe 呼叫封裝（Task 0）
│       ├── probe.py              # 媒體規格盤點（Task 0）
│       ├── timeline.py           # ASR 詞級時間軸取得／驗證（Task 1）
│       ├── silence.py            # silencedetect 偵測與解析（Task 2）
│       ├── segments.py           # 語句／噪音窗／非語音雜訊三分類（Task 3）
│       ├── measure.py            # 逐區間 LUFS 量測（Task 4）
│       ├── fingerprint.py        # FFT 頻譜指紋與分區偵測（Task 5）
│       ├── diagnose.py           # 逐區診斷指標（Task 6）
│       ├── plan_io.py            # report.json / plan.json 讀寫（Task 7）
│       ├── gain.py               # 增益計算、限幅、平滑、volume 表達式（Task 8）
│       ├── chain.py              # ffmpeg 濾鏡鏈組裝（Task 9）
│       ├── render.py             # 分區渲染、串接、換揉、ASR WAV（Task 10）
│       ├── verify.py             # 四指標驗證（Task 11）
│       └── preview.py            # AB 試聽片段（Task 11）
├── tests/
│   ├── conftest.py               # 合成音檔 fixture（Task 0）
│   └── test_*.py                 # 每個模組一支
└── references/
    ├── filter-cookbook.md        # 濾鏡參數對照與門檻校準紀錄（Task 14）
    └── rescue-ai.md              # DeepFilterNet 救援層說明（Task 13）
```

每支模組單一職責，最大不超過約 200 行。訊號分析（`segments` / `fingerprint` / `gain` / `verify`）為純函式，不碰 I/O，可用合成資料完整測試。

---

## Task 0: 骨架、venv 與 ffmpeg 呼叫封裝

**Files:**
- Create: `skills-src/audio-restoration/requirements.txt`
- Create: `skills-src/audio-restoration/scripts/ar/__init__.py`
- Create: `skills-src/audio-restoration/scripts/ar/ffmpeg_io.py`
- Create: `skills-src/audio-restoration/scripts/ar/probe.py`
- Create: `skills-src/audio-restoration/tests/conftest.py`
- Test: `skills-src/audio-restoration/tests/test_probe.py`

**Interfaces:**
- Consumes: 無（第一個任務）
- Produces:
  - `ffmpeg_io.run_ffmpeg(args: list[str]) -> str`（回傳 stderr 全文）
  - `ffmpeg_io.run_ffprobe_json(path: Path) -> dict`
  - `ffmpeg_io.FFmpegError(Exception)`
  - `probe.MediaSpec`（dataclass：`path: Path`、`is_video: bool`、`duration: float`、`sample_rate: int`、`channels: int`、`audio_codec: str`）
  - `probe.probe(path: Path) -> MediaSpec`
  - conftest fixture：`synth_wav(tmp_path)` 產生 8 秒測試音檔

- [ ] **Step 1: 建立 venv 與依賴**

```powershell
python -m venv "$HOME\.audio-restoration\.venv"
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pip install --upgrade pip
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pip install numpy pytest
```

`requirements.txt` 內容：

```
numpy>=2.0
pytest>=8.0
```

- [ ] **Step 2: 寫合成音檔 fixture**

`tests/conftest.py`。用標準庫 `wave` 直接寫 PCM，不依賴 ffmpeg 生成，確保測試在任何機器可重跑。

```python
"""測試共用 fixture：以純 Python 合成可預測的測試音檔。"""
import math
import wave
from pathlib import Path

import numpy as np
import pytest

SAMPLE_RATE = 48000  # 取樣率，與實際講課素材一致


def _write_wav(path: Path, samples: np.ndarray, sample_rate: int = SAMPLE_RATE) -> None:
    """將 float32 (-1..1) 樣本寫成 16-bit 單聲道 PCM WAV。"""
    clipped = np.clip(samples, -1.0, 1.0)
    pcm = (clipped * 32767.0).astype(np.int16)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(pcm.tobytes())


def make_tone(duration: float, freq: float, amplitude: float,
              sample_rate: int = SAMPLE_RATE) -> np.ndarray:
    """產生指定時長、頻率、振幅的正弦波，用來模擬「一句話」。"""
    t = np.arange(int(duration * sample_rate)) / sample_rate
    return amplitude * np.sin(2 * math.pi * freq * t)


def make_noise(duration: float, amplitude: float,
               sample_rate: int = SAMPLE_RATE, seed: int = 0) -> np.ndarray:
    """產生固定亂數種子的白雜訊，用來模擬底噪（可重現）。"""
    rng = np.random.default_rng(seed)
    return amplitude * rng.standard_normal(int(duration * sample_rate))


@pytest.fixture
def synth_wav(tmp_path: Path) -> Path:
    """合成 8 秒測試音檔：兩句音量相差約 12dB，中間夾靜音（僅底噪）。

    時間軸配置（供各模組測試共用，數值不可隨意更動）：
      0.0-2.0s 靜音（僅底噪）
      2.0-4.0s 語句A（220Hz，振幅 0.5，約 -6 dBFS）
      4.0-5.0s 靜音（僅底噪）
      5.0-7.0s 語句B（220Hz，振幅 0.125，約 -18 dBFS）
      7.0-8.0s 靜音（僅底噪）
    """
    noise_amp = 0.002  # 底噪振幅，約 -54 dBFS
    parts = [
        make_noise(2.0, noise_amp, seed=1),
        make_tone(2.0, 220.0, 0.5) + make_noise(2.0, noise_amp, seed=2),
        make_noise(1.0, noise_amp, seed=3),
        make_tone(2.0, 220.0, 0.125) + make_noise(2.0, noise_amp, seed=4),
        make_noise(1.0, noise_amp, seed=5),
    ]
    path = tmp_path / "synth.wav"
    _write_wav(path, np.concatenate(parts))
    return path
```

- [ ] **Step 3: 寫 probe 的失敗測試**

`tests/test_probe.py`：

```python
"""probe 模組測試：驗證能正確讀出合成音檔的規格。"""
from pathlib import Path

from ar.probe import probe


def test_probe_reads_synth_wav_spec(synth_wav: Path):
    """合成音檔應被判定為非影片，且時長／取樣率／聲道數正確。"""
    spec = probe(synth_wav)
    assert spec.is_video is False
    assert spec.sample_rate == 48000
    assert spec.channels == 1
    assert abs(spec.duration - 8.0) < 0.05
    assert spec.audio_codec == "pcm_s16le"
```

- [ ] **Step 4: 執行測試確認失敗**

```powershell
cd skills-src\audio-restoration
$env:PYTHONPATH = "scripts"
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_probe.py -v
```

Expected: FAIL — `ModuleNotFoundError: No module named 'ar'`

- [ ] **Step 5: 實作 ffmpeg_io.py**

```python
"""ffmpeg / ffprobe 外部程序呼叫封裝。"""
import json
import shutil
import subprocess
from pathlib import Path


class FFmpegError(RuntimeError):
    """ffmpeg 或 ffprobe 執行失敗時拋出。"""


def _require(tool: str) -> str:
    """確認外部工具存在，回傳其絕對路徑；不存在則明確報錯。"""
    found = shutil.which(tool)
    if not found:
        raise FFmpegError(f"找不到 {tool}，請先安裝 ffmpeg 並加入 PATH")
    return found


def run_ffmpeg(args: list[str]) -> str:
    """執行 ffmpeg 並回傳 stderr 全文（濾鏡量測結果都印在 stderr）。"""
    cmd = [_require("ffmpeg"), "-hide_banner", "-nostdin", *args]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                            errors="replace")
    if result.returncode != 0:
        raise FFmpegError(f"ffmpeg 失敗（exit {result.returncode}）：\n{result.stderr[-2000:]}")
    return result.stderr


def run_ffprobe_json(path: Path) -> dict:
    """以 ffprobe 取得媒體的 format 與 streams 資訊（JSON）。"""
    cmd = [
        _require("ffprobe"), "-hide_banner", "-v", "error",
        "-print_format", "json", "-show_format", "-show_streams", str(path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                            errors="replace")
    if result.returncode != 0:
        raise FFmpegError(f"ffprobe 失敗：{result.stderr[-1000:]}")
    return json.loads(result.stdout)
```

- [ ] **Step 6: 實作 probe.py**

```python
"""媒體規格盤點：判斷是影片或純音檔，並取出音訊軌參數。"""
from dataclasses import dataclass
from pathlib import Path

from .ffmpeg_io import FFmpegError, run_ffprobe_json


@dataclass
class MediaSpec:
    """一個輸入媒體的規格摘要。"""
    path: Path
    is_video: bool          # 是否含影像軌（決定輸出要不要換揉）
    duration: float         # 總時長（秒）
    sample_rate: int        # 音訊取樣率
    channels: int           # 聲道數
    audio_codec: str        # 音訊編碼名稱


def probe(path: Path) -> MediaSpec:
    """讀取媒體規格；無音訊軌時明確報錯，因為本技能只處理人聲音軌。"""
    data = run_ffprobe_json(path)
    streams = data.get("streams", [])
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    if audio is None:
        raise FFmpegError(f"{path} 沒有音訊軌，無法進行音質修復")
    has_video = any(
        s.get("codec_type") == "video" and s.get("disposition", {}).get("attached_pic", 0) == 0
        for s in streams
    )
    return MediaSpec(
        path=path,
        is_video=has_video,
        duration=float(data["format"]["duration"]),
        sample_rate=int(audio["sample_rate"]),
        channels=int(audio["channels"]),
        audio_codec=audio["codec_name"],
    )
```

`scripts/ar/__init__.py` 留空檔即可（僅作為套件標記）。

- [ ] **Step 7: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_probe.py -v
```

Expected: PASS

- [ ] **Step 8: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 建立技能骨架與 ffmpeg 呼叫封裝"
```

---

## Task 1: ASR 詞級時間軸取得與驗證

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/timeline.py`
- Test: `skills-src/audio-restoration/tests/test_timeline.py`

**Interfaces:**
- Consumes: `probe.MediaSpec`、`ffmpeg_io.FFmpegError`
- Produces:
  - `timeline.Word`（dataclass：`start: float`、`end: float`、`text: str`）
  - `timeline.Segment`（dataclass：`start: float`、`end: float`、`text: str`、`words: list[Word]`）
  - `timeline.Timeline`（dataclass：`duration: float`、`segments: list[Segment]`、`all_words() -> list[Word]`）
  - `timeline.load_timeline(path: Path) -> Timeline`
  - `timeline.timeline_matches(tl: Timeline, media_duration: float, tolerance: float = 0.5) -> bool`
  - `timeline.find_asr_python() -> str`
  - `timeline.ensure_timeline(spec: MediaSpec, work_dir: Path) -> Timeline`

- [ ] **Step 1: 寫失敗測試**

`tests/test_timeline.py`：

```python
"""timeline 模組測試：載入、時長驗證、詞彙攤平。"""
import json
from pathlib import Path

from ar.timeline import load_timeline, timeline_matches


def _write_timeline(path: Path, duration: float) -> Path:
    """寫出一份與 faster-whisper 相同結構的 timeline.json 供測試使用。"""
    payload = {
        "info": {"language": "zh", "duration": duration, "model": "small"},
        "segments": [
            {
                "id": 1, "start": 2.0, "end": 4.0, "text": "語句A",
                "no_speech_prob": 0.05,
                "words": [
                    {"start": 2.0, "end": 3.0, "word": "語", "probability": 0.9},
                    {"start": 3.0, "end": 4.0, "word": "句A", "probability": 0.9},
                ],
            },
            {
                "id": 2, "start": 5.0, "end": 7.0, "text": "語句B",
                "no_speech_prob": 0.05,
                "words": [
                    {"start": 5.0, "end": 6.0, "word": "語", "probability": 0.8},
                    {"start": 6.0, "end": 7.0, "word": "句B", "probability": 0.8},
                ],
            },
        ],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return path


def test_load_timeline_parses_segments_and_words(tmp_path: Path):
    """載入後應保留兩個語句、共四個詞，且時間戳不變。"""
    tl = load_timeline(_write_timeline(tmp_path / "timeline.json", 8.0))
    assert len(tl.segments) == 2
    assert len(tl.all_words()) == 4
    assert tl.segments[0].words[0].start == 2.0
    assert tl.segments[1].end == 7.0


def test_timeline_matches_within_tolerance(tmp_path: Path):
    """時長誤差在 0.5 秒容差內視為匹配。"""
    tl = load_timeline(_write_timeline(tmp_path / "a.json", 8.0))
    assert timeline_matches(tl, 8.3) is True


def test_timeline_rejected_when_duration_drifts(tmp_path: Path):
    """時長誤差超過容差視為不匹配（例如音檔被剪過），須重跑 ASR。"""
    tl = load_timeline(_write_timeline(tmp_path / "b.json", 8.0))
    assert timeline_matches(tl, 9.2) is False
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_timeline.py -v
```

Expected: FAIL — `No module named 'ar.timeline'`

- [ ] **Step 3: 實作 timeline.py**

```python
"""ASR 詞級時間軸的取得與驗證。

不採用 silencedetect 單獨切句：它是音量門檻判斷，而本技能處理的問題正是音量
不一致，小聲句會整句被判為靜音而遺失。也不採用 SRT 字幕：其時間戳為閱讀體驗
調整過，與實際發聲邊界誤差可達數百毫秒。
"""
import json
import shutil
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from .ffmpeg_io import FFmpegError
from .probe import MediaSpec

DURATION_TOLERANCE = 0.5  # timeline.json 與音檔時長的容許誤差（秒）


@dataclass
class Word:
    """單一詞的時間戳。"""
    start: float
    end: float
    text: str


@dataclass
class Segment:
    """一個 ASR 語句（含其詞級明細）。"""
    start: float
    end: float
    text: str
    words: list[Word] = field(default_factory=list)


@dataclass
class Timeline:
    """整份時間軸。"""
    duration: float
    segments: list[Segment]

    def all_words(self) -> list[Word]:
        """把所有語句的詞攤平成單一有序清單。"""
        words: list[Word] = []
        for segment in self.segments:
            words.extend(segment.words)
        return sorted(words, key=lambda w: w.start)


def load_timeline(path: Path) -> Timeline:
    """讀取 faster-whisper 格式的 timeline.json。"""
    payload = json.loads(path.read_text(encoding="utf-8"))
    segments = [
        Segment(
            start=float(seg["start"]),
            end=float(seg["end"]),
            text=seg.get("text", ""),
            words=[
                Word(start=float(w["start"]), end=float(w["end"]), text=w.get("word", ""))
                for w in seg.get("words", [])
            ],
        )
        for seg in payload.get("segments", [])
    ]
    return Timeline(duration=float(payload["info"]["duration"]), segments=segments)


def timeline_matches(tl: Timeline, media_duration: float,
                     tolerance: float = DURATION_TOLERANCE) -> bool:
    """判斷既有 timeline.json 是否對應目前這支音檔（時長比對）。"""
    return abs(tl.duration - media_duration) <= tolerance


def find_asr_python() -> str:
    """尋找可用的 faster-whisper Python，依序三段 fallback。

    .tmp/asr-venv 為專案暫存目錄，隨時可能被清除，因此不可寫死。
    """
    candidates = [
        Path.cwd() / ".tmp" / "asr-venv" / "Scripts" / "python.exe",
        Path.home() / ".audio-restoration" / ".venv" / "Scripts" / "python.exe",
    ]
    for candidate in candidates:
        if candidate.exists() and _has_faster_whisper(str(candidate)):
            return str(candidate)
    fallback = shutil.which("python")
    if fallback and _has_faster_whisper(fallback):
        return fallback
    raise FFmpegError(
        "找不到含 faster-whisper 的 Python。請執行：\n"
        '  & "$HOME\\.audio-restoration\\.venv\\Scripts\\python" -m pip install faster-whisper'
    )


def _has_faster_whisper(python_exe: str) -> bool:
    """檢查指定 Python 是否已安裝 faster-whisper。"""
    result = subprocess.run([python_exe, "-c", "import faster_whisper"],
                            capture_output=True, text=True)
    return result.returncode == 0


def ensure_timeline(spec: MediaSpec, work_dir: Path) -> Timeline:
    """取得可用的時間軸：既有檔通過時長驗證就沿用，否則重跑 ASR。"""
    target = work_dir / "timeline.json"
    for candidate in (target, spec.path.parent / "timeline.json"):
        if candidate.exists():
            tl = load_timeline(candidate)
            if timeline_matches(tl, spec.duration):
                return tl
    return _run_asr(spec, work_dir)


def _run_asr(spec: MediaSpec, work_dir: Path) -> Timeline:
    """跑一次 faster-whisper small 產生詞級時間軸。

    本技能只需要時間軸，不要求文字正確性，故固定用 small 模型以節省時間。
    """
    work_dir.mkdir(parents=True, exist_ok=True)
    script = Path(__file__).resolve().parent.parent / "asr_timeline_runner.py"
    subprocess.run(
        [find_asr_python(), str(script), "--audio", str(spec.path),
         "--out-dir", str(work_dir), "--model", "small"],
        check=True,
    )
    return load_timeline(work_dir / "timeline.json")
```

- [ ] **Step 4: 建立 ASR 執行腳本**

`scripts/asr_timeline_runner.py`。獨立成檔的原因：它必須用**含 faster-whisper 的另一個 Python** 執行，不能與主程序共用直譯器。

```python
"""在具備 faster-whisper 的 Python 環境中產生詞級時間軸。

輸出格式與專案既有 .tmp/asr_transcribe.py 一致，可互相沿用。
"""
import argparse
import json
from pathlib import Path

from faster_whisper import WhisperModel


def transcribe(audio_path: Path, out_dir: Path, model_size: str) -> None:
    """轉錄音訊並輸出 timeline.json（僅時間軸，不產字幕檔）。"""
    out_dir.mkdir(parents=True, exist_ok=True)
    model = WhisperModel(model_size, device="cpu", compute_type="int8")
    raw_segments, info = model.transcribe(
        str(audio_path), language="zh", beam_size=5,
        vad_filter=True, word_timestamps=True,
    )
    segments = [
        {
            "id": segment.id,
            "start": segment.start,
            "end": segment.end,
            "text": segment.text.strip(),
            "no_speech_prob": segment.no_speech_prob,
            "words": [
                {"start": w.start, "end": w.end, "word": w.word, "probability": w.probability}
                for w in (segment.words or [])
            ],
        }
        for segment in raw_segments
    ]
    payload = {
        "info": {
            "language": info.language,
            "language_probability": info.language_probability,
            "duration": info.duration,
            "duration_after_vad": info.duration_after_vad,
            "model": model_size,
        },
        "segments": segments,
    }
    (out_dir / "timeline.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def main() -> None:
    """解析參數並啟動轉錄。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--audio", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--model", default="small")
    args = parser.parse_args()
    transcribe(args.audio, args.out_dir, args.model)


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_timeline.py -v
```

Expected: PASS（3 passed）

- [ ] **Step 6: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入 ASR 詞級時間軸取得與時長驗證"
```

---

## Task 2: 靜音偵測與解析

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/silence.py`
- Test: `skills-src/audio-restoration/tests/test_silence.py`

**Interfaces:**
- Consumes: `ffmpeg_io.run_ffmpeg`
- Produces:
  - `silence.Interval`（dataclass：`start: float`、`end: float`、`duration` property）
  - `silence.parse_silencedetect(stderr: str, total_duration: float) -> list[Interval]`
  - `silence.detect_silence(path: Path, total_duration: float, noise_db: float = -40.0, min_dur: float = 0.3) -> list[Interval]`

- [ ] **Step 1: 寫失敗測試**

`tests/test_silence.py`：

```python
"""silence 模組測試：解析 ffmpeg silencedetect 輸出。"""
from pathlib import Path

from ar.silence import detect_silence, parse_silencedetect

SAMPLE_STDERR = """
[silencedetect @ 000001] silence_start: 0
[silencedetect @ 000001] silence_end: 2.001 | silence_duration: 2.001
[silencedetect @ 000001] silence_start: 4.002
[silencedetect @ 000001] silence_end: 5.003 | silence_duration: 1.001
[silencedetect @ 000001] silence_start: 7.004
"""


def test_parse_silencedetect_extracts_intervals():
    """三段靜音都要解析出來。"""
    intervals = parse_silencedetect(SAMPLE_STDERR, total_duration=8.0)
    assert len(intervals) == 3
    assert intervals[0].start == 0.0
    assert abs(intervals[0].end - 2.001) < 1e-6


def test_parse_silencedetect_closes_trailing_interval():
    """最後一段 silence_start 沒有對應的 silence_end 時，用總時長收尾。"""
    intervals = parse_silencedetect(SAMPLE_STDERR, total_duration=8.0)
    assert abs(intervals[-1].end - 8.0) < 1e-6


def test_detect_silence_on_synth_wav(synth_wav: Path):
    """合成音檔的三段靜音（0-2、4-5、7-8 秒）應被偵測到。"""
    intervals = detect_silence(synth_wav, total_duration=8.0, noise_db=-40.0, min_dur=0.3)
    assert len(intervals) == 3
    assert abs(intervals[1].start - 4.0) < 0.15
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_silence.py -v
```

Expected: FAIL — `No module named 'ar.silence'`

- [ ] **Step 3: 實作 silence.py**

```python
"""靜音偵測。

僅作為 ASR 時間軸的交叉驗證使用：確認噪音採樣窗確實無人聲，以及找出
「有聲但 ASR 無詞」的非語音雜訊。不單獨用於切句。
"""
import re
from dataclasses import dataclass
from pathlib import Path

from .ffmpeg_io import run_ffmpeg

_START_RE = re.compile(r"silence_start:\s*(-?[\d.]+)")
_END_RE = re.compile(r"silence_end:\s*(-?[\d.]+)")


@dataclass
class Interval:
    """一段時間區間（秒）。"""
    start: float
    end: float

    @property
    def duration(self) -> float:
        """區間長度（秒）。"""
        return self.end - self.start


def parse_silencedetect(stderr: str, total_duration: float) -> list[Interval]:
    """解析 silencedetect 印在 stderr 的成對 start/end 標記。

    最後一段可能只有 silence_start（音檔以靜音結尾），此時用總時長收尾。
    """
    starts = [float(m) for m in _START_RE.findall(stderr)]
    ends = [float(m) for m in _END_RE.findall(stderr)]
    intervals: list[Interval] = []
    for index, start in enumerate(starts):
        end = ends[index] if index < len(ends) else total_duration
        intervals.append(Interval(start=max(0.0, start), end=min(end, total_duration)))
    return intervals


def detect_silence(path: Path, total_duration: float,
                   noise_db: float = -40.0, min_dur: float = 0.3) -> list[Interval]:
    """對整支音檔跑 silencedetect。"""
    stderr = run_ffmpeg([
        "-i", str(path),
        "-af", f"silencedetect=noise={noise_db}dB:d={min_dur}",
        "-f", "null", "-",
    ])
    return parse_silencedetect(stderr, total_duration)
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_silence.py -v
```

Expected: PASS（3 passed）

- [ ] **Step 5: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入靜音偵測與解析"
```

---

## Task 3: 語句／噪音窗／非語音雜訊三分類

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/segments.py`
- Test: `skills-src/audio-restoration/tests/test_segments.py`

**Interfaces:**
- Consumes: `timeline.Timeline`、`timeline.Word`、`silence.Interval`
- Produces:
  - `segments.Utterance`（dataclass：`index: int`、`start: float`、`end: float`）
  - `segments.Classification`（dataclass：`utterances: list[Utterance]`、`noise_windows: list[Interval]`、`nonspeech_events: list[Interval]`）
  - `segments.classify(tl: Timeline, silences: list[Interval], total_duration: float, min_noise_gap: float = 0.6) -> Classification`

**設計要點：** 語句邊界不直接用 ASR 的 segment 邊界，而是取**相鄰語句間 gap 的中點**作為切換點。增益切換發生在完全無聲處，才不會在人聲上聽到爆點。

- [ ] **Step 1: 寫失敗測試**

`tests/test_segments.py`：

```python
"""segments 模組測試：三分類與語句邊界外擴。"""
from ar.segments import classify
from ar.silence import Interval
from ar.timeline import Segment, Timeline, Word


def _timeline() -> Timeline:
    """建立與 conftest 合成音檔對應的時間軸：2-4 秒與 5-7 秒各一句。"""
    return Timeline(
        duration=8.0,
        segments=[
            Segment(2.0, 4.0, "語句A", [Word(2.0, 3.0, "語"), Word(3.0, 4.0, "句A")]),
            Segment(5.0, 7.0, "語句B", [Word(5.0, 6.0, "語"), Word(6.0, 7.0, "句B")]),
        ],
    )


def _silences() -> list[Interval]:
    """對應的靜音區間。"""
    return [Interval(0.0, 2.0), Interval(4.0, 5.0), Interval(7.0, 8.0)]


def test_utterance_boundaries_land_at_gap_midpoint():
    """兩句之間的切換點應落在 4.0-5.0 的中點 4.5，而非緊貼語句邊界。"""
    result = classify(_timeline(), _silences(), total_duration=8.0)
    assert len(result.utterances) == 2
    assert abs(result.utterances[0].end - 4.5) < 1e-6
    assert abs(result.utterances[1].start - 4.5) < 1e-6


def test_first_and_last_utterance_span_whole_file():
    """首尾語句應延伸到檔案兩端，確保增益覆蓋整個時間軸不留空隙。"""
    result = classify(_timeline(), _silences(), total_duration=8.0)
    assert result.utterances[0].start == 0.0
    assert abs(result.utterances[-1].end - 8.0) < 1e-6


def test_noise_windows_require_min_gap_and_silence_agreement():
    """噪音採樣窗須同時滿足：詞間 gap >= 0.6 秒、且 silencedetect 也認為靜音。"""
    result = classify(_timeline(), _silences(), total_duration=8.0, min_noise_gap=0.6)
    assert len(result.noise_windows) == 3
    assert all(w.duration >= 0.6 for w in result.noise_windows)


def test_short_gap_is_not_used_as_noise_window():
    """詞間 gap 只有 0.3 秒時不得採為噪音窗，避免採到語音殘響尾巴。"""
    tl = Timeline(
        duration=8.0,
        segments=[
            Segment(2.0, 3.0, "A", [Word(2.0, 3.0, "A")]),
            Segment(3.3, 4.0, "B", [Word(3.3, 4.0, "B")]),
        ],
    )
    result = classify(tl, [Interval(3.0, 3.3)], total_duration=8.0, min_noise_gap=0.6)
    assert result.noise_windows == []


def test_nonspeech_event_detected_when_sound_without_words():
    """silencedetect 認為有聲、但 ASR 完全沒詞的區段 = 咳嗽／翻頁等雜訊。

    這類事件必須標出來，因為它們要被壓低而非拉平；誤當語句處理會把咳嗽聲
    放大到與人聲同等音量。
    """
    tl = Timeline(duration=8.0, segments=[
        Segment(2.0, 4.0, "A", [Word(2.0, 3.0, "語"), Word(3.0, 4.0, "A")]),
    ])
    # 5.0-5.4 秒有聲音（不在任何靜音區間內），但沒有任何 ASR 詞
    silences = [Interval(0.0, 2.0), Interval(4.0, 5.0), Interval(5.4, 8.0)]
    result = classify(tl, silences, total_duration=8.0)
    assert len(result.nonspeech_events) == 1
    assert abs(result.nonspeech_events[0].start - 5.0) < 1e-6
    assert abs(result.nonspeech_events[0].end - 5.4) < 1e-6
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_segments.py -v
```

Expected: FAIL — `No module named 'ar.segments'`

- [ ] **Step 3: 實作 segments.py**

```python
"""把時間軸切成三類區間：語句、噪音採樣窗、非語音雜訊事件。

三類的用途各不相同：
  語句     → 逐句增益補償的作用範圍
  噪音窗   → 降噪基準與頻譜指紋的採樣來源（必須完全無人聲）
  雜訊事件 → 咳嗽／翻頁／椅子聲，要壓低而非拉平
"""
from dataclasses import dataclass

from .silence import Interval
from .timeline import Timeline

MIN_NOISE_GAP = 0.6  # 噪音採樣窗的最小長度（秒）


@dataclass
class Utterance:
    """一個語句的增益作用範圍（邊界已外擴至靜音中點）。"""
    index: int
    start: float
    end: float


@dataclass
class Classification:
    """三分類結果。"""
    utterances: list[Utterance]
    noise_windows: list[Interval]
    nonspeech_events: list[Interval]


def classify(tl: Timeline, silences: list[Interval], total_duration: float,
             min_noise_gap: float = MIN_NOISE_GAP) -> Classification:
    """執行三分類。"""
    return Classification(
        utterances=_build_utterances(tl, total_duration),
        noise_windows=_build_noise_windows(tl, silences, total_duration, min_noise_gap),
        nonspeech_events=_build_nonspeech_events(tl, silences, total_duration),
    )


def _build_utterances(tl: Timeline, total_duration: float) -> list[Utterance]:
    """把 ASR 語句邊界外擴到相鄰語句間 gap 的中點。

    增益切換若發生在人聲上會產生可聽的爆點，故一律移到完全無聲處。
    首句起點與末句終點延伸到檔案兩端，確保增益覆蓋無空隙。
    """
    spans = [(seg.start, seg.end) for seg in tl.segments]
    if not spans:
        return [Utterance(index=0, start=0.0, end=total_duration)]

    utterances: list[Utterance] = []
    for index, (start, end) in enumerate(spans):
        left = 0.0 if index == 0 else (spans[index - 1][1] + start) / 2.0
        right = total_duration if index == len(spans) - 1 else (end + spans[index + 1][0]) / 2.0
        utterances.append(Utterance(index=index, start=left, end=right))
    return utterances


def _build_noise_windows(tl: Timeline, silences: list[Interval], total_duration: float,
                         min_noise_gap: float) -> list[Interval]:
    """取出「詞間 gap 夠長」且「silencedetect 也認為靜音」的雙重確認區間。

    採樣窗若混入人聲，降噪會把人聲的頻譜特徵當成噪音消掉，這是修音最常見的
    災難，故採雙重確認而非單一判準。
    """
    gaps = _word_gaps(tl, total_duration)
    windows: list[Interval] = []
    for gap in gaps:
        for silence in silences:
            overlap_start = max(gap.start, silence.start)
            overlap_end = min(gap.end, silence.end)
            if overlap_end - overlap_start >= min_noise_gap:
                windows.append(Interval(overlap_start, overlap_end))
    return windows


def _word_gaps(tl: Timeline, total_duration: float) -> list[Interval]:
    """算出所有詞與詞之間的空隙（含檔頭與檔尾）。"""
    words = tl.all_words()
    if not words:
        return [Interval(0.0, total_duration)]
    gaps = [Interval(0.0, words[0].start)]
    for previous, current in zip(words, words[1:]):
        if current.start > previous.end:
            gaps.append(Interval(previous.end, current.start))
    gaps.append(Interval(words[-1].end, total_duration))
    return [gap for gap in gaps if gap.duration > 0]


def _build_nonspeech_events(tl: Timeline, silences: list[Interval],
                            total_duration: float) -> list[Interval]:
    """找出 silencedetect 判定有聲、但 ASR 完全沒詞的區段。"""
    sounding = _invert(silences, total_duration)
    words = tl.all_words()
    events: list[Interval] = []
    for span in sounding:
        has_word = any(word.start < span.end and word.end > span.start for word in words)
        if not has_word:
            events.append(span)
    return events


def _invert(intervals: list[Interval], total_duration: float) -> list[Interval]:
    """把靜音區間清單反轉成有聲區間清單。"""
    ordered = sorted(intervals, key=lambda i: i.start)
    result: list[Interval] = []
    cursor = 0.0
    for interval in ordered:
        if interval.start > cursor:
            result.append(Interval(cursor, interval.start))
        cursor = max(cursor, interval.end)
    if cursor < total_duration:
        result.append(Interval(cursor, total_duration))
    return result
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_segments.py -v
```

Expected: PASS（5 passed）

- [ ] **Step 5: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入語句/噪音窗/雜訊三分類"
```

---

## Task 4: 逐區間響度量測

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/measure.py`
- Test: `skills-src/audio-restoration/tests/test_measure.py`

**Interfaces:**
- Consumes: `ffmpeg_io.run_ffmpeg`、`silence.Interval`、`segments.Utterance`
- Produces:
  - `measure.LoudnessStats`（dataclass：`lufs: float`、`rms_db: float`、`peak_db: float`）
  - `measure.measure_interval(path: Path, start: float, end: float) -> LoudnessStats`
  - `measure.measure_utterances(path: Path, utterances: list[Utterance]) -> list[LoudnessStats]`
  - `measure.measure_intervals(path: Path, intervals: list[Interval]) -> list[LoudnessStats]`

- [ ] **Step 1: 寫失敗測試**

`tests/test_measure.py`：

```python
"""measure 模組測試：逐區間 LUFS / RMS / peak 量測。"""
from pathlib import Path

from ar.measure import measure_interval


def test_loud_utterance_measures_higher_than_quiet_one(synth_wav: Path):
    """合成音檔語句A（振幅 0.5）應比語句B（振幅 0.125）大約 12dB。"""
    loud = measure_interval(synth_wav, 2.2, 3.8)
    quiet = measure_interval(synth_wav, 5.2, 6.8)
    delta = loud.lufs - quiet.lufs
    assert 10.0 < delta < 14.0


def test_silence_measures_much_quieter_than_speech(synth_wav: Path):
    """靜音段（僅底噪）的 RMS 應遠低於語句段。"""
    silence = measure_interval(synth_wav, 0.2, 1.8)
    speech = measure_interval(synth_wav, 2.2, 3.8)
    assert speech.rms_db - silence.rms_db > 30.0


def test_peak_reflects_amplitude(synth_wav: Path):
    """語句A 振幅 0.5，峰值應接近 -6 dBFS。"""
    stats = measure_interval(synth_wav, 2.2, 3.8)
    assert -7.5 < stats.peak_db < -4.5
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_measure.py -v
```

Expected: FAIL — `No module named 'ar.measure'`

- [ ] **Step 3: 實作 measure.py**

```python
"""逐區間響度量測（LUFS / RMS / 峰值）。"""
import re
from dataclasses import dataclass
from pathlib import Path

from .ffmpeg_io import run_ffmpeg
from .segments import Utterance
from .silence import Interval

_I_RE = re.compile(r"^\s*I:\s*(-?[\d.]+|-inf)\s*LUFS", re.MULTILINE)
_RMS_RE = re.compile(r"RMS level dB:\s*(-?[\d.]+|-inf)")
_PEAK_RE = re.compile(r"Peak level dB:\s*(-?[\d.]+|-inf)")

SILENT_FLOOR = -120.0  # 量到 -inf 時採用的替代值，避免後續運算出現無限大


@dataclass
class LoudnessStats:
    """一段區間的響度量測結果。"""
    lufs: float     # EBU R128 integrated loudness
    rms_db: float   # RMS 位準
    peak_db: float  # 峰值位準


def _to_float(value: str | None) -> float:
    """把 ffmpeg 輸出的數值字串轉為 float，-inf 以地板值取代。"""
    if value is None or value == "-inf":
        return SILENT_FLOOR
    return float(value)


def measure_interval(path: Path, start: float, end: float) -> LoudnessStats:
    """量測單一區間的響度。

    ebur128 與 astats 一次跑完，避免對長檔案重複解碼。
    """
    duration = max(0.05, end - start)
    stderr = run_ffmpeg([
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(path),
        "-af", "ebur128=peak=true,astats=measure_perchannel=none",
        "-f", "null", "-",
    ])
    i_match = _I_RE.search(stderr)
    rms_match = _RMS_RE.search(stderr)
    peak_match = _PEAK_RE.search(stderr)
    return LoudnessStats(
        lufs=_to_float(i_match.group(1) if i_match else None),
        rms_db=_to_float(rms_match.group(1) if rms_match else None),
        peak_db=_to_float(peak_match.group(1) if peak_match else None),
    )


def measure_utterances(path: Path, utterances: list[Utterance]) -> list[LoudnessStats]:
    """逐句量測。"""
    return [measure_interval(path, u.start, u.end) for u in utterances]


def measure_intervals(path: Path, intervals: list[Interval]) -> list[LoudnessStats]:
    """逐區間量測（供噪音窗使用）。"""
    return [measure_interval(path, i.start, i.end) for i in intervals]
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_measure.py -v
```

Expected: PASS（3 passed）

- [ ] **Step 5: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入逐區間響度量測"
```

---

## Task 5: 頻譜指紋與分區偵測

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/fingerprint.py`
- Test: `skills-src/audio-restoration/tests/test_fingerprint.py`

**Interfaces:**
- Consumes: `silence.Interval`、`ffmpeg_io.run_ffmpeg`
- Produces:
  - `fingerprint.Zone`（dataclass：`index: int`、`start: float`、`end: float`、`noise_window_indices: list[int]`）
  - `fingerprint.read_samples(path: Path, start: float, end: float, sample_rate: int = 16000) -> np.ndarray`
  - `fingerprint.spectral_fingerprint(samples: np.ndarray, sample_rate: int) -> np.ndarray`
  - `fingerprint.cosine_distance(a: np.ndarray, b: np.ndarray) -> float`
  - `fingerprint.detect_zones(fingerprints: list[np.ndarray], windows: list[Interval], total_duration: float, threshold: float = 0.15) -> list[Zone]`

**設計要點：** 分區依據是**底噪的頻譜形狀**而非音量。換麥克風時音量可能不變，但噪音頻譜必變。指紋作法：FFT 功率譜 → 依 1/3 八度頻帶聚合 → 正規化為機率分布 → 取相鄰餘弦距離。

- [ ] **Step 1: 寫失敗測試**

`tests/test_fingerprint.py`：

```python
"""fingerprint 模組測試：頻譜指紋與分區切點。"""
import numpy as np

from ar.fingerprint import cosine_distance, detect_zones, spectral_fingerprint
from ar.silence import Interval

SR = 16000


def _white_noise(seed: int) -> np.ndarray:
    """白雜訊：全頻段能量平坦。"""
    rng = np.random.default_rng(seed)
    return rng.standard_normal(SR)


def _low_passed_noise(seed: int) -> np.ndarray:
    """低頻為主的雜訊，模擬換了另一支麥克風後的底噪形狀。"""
    rng = np.random.default_rng(seed)
    noise = rng.standard_normal(SR)
    kernel = np.ones(64) / 64.0  # 移動平均 = 簡易低通
    return np.convolve(noise, kernel, mode="same")


def test_fingerprint_is_normalized_distribution():
    """指紋應為總和為 1 的非負分布，才能用餘弦距離比較形狀而非音量。"""
    fp = spectral_fingerprint(_white_noise(1), SR)
    assert fp.ndim == 1
    assert np.all(fp >= 0)
    assert abs(float(fp.sum()) - 1.0) < 1e-6


def test_same_noise_type_has_small_distance():
    """同一種底噪（不同亂數樣本）的指紋距離應很小。"""
    a = spectral_fingerprint(_white_noise(1), SR)
    b = spectral_fingerprint(_white_noise(2), SR)
    assert cosine_distance(a, b) < 0.05


def test_different_noise_type_has_large_distance():
    """白雜訊與低頻雜訊的指紋距離應明顯超過門檻。"""
    a = spectral_fingerprint(_white_noise(1), SR)
    b = spectral_fingerprint(_low_passed_noise(3), SR)
    assert cosine_distance(a, b) > 0.15


def test_fingerprint_ignores_volume_difference():
    """同一種噪音放大 10 倍後指紋幾乎不變 —— 分區看形狀不看音量。"""
    base = _white_noise(1)
    a = spectral_fingerprint(base, SR)
    b = spectral_fingerprint(base * 10.0, SR)
    assert cosine_distance(a, b) < 1e-6


def test_detect_zones_splits_at_noise_change():
    """前兩窗白雜訊、後兩窗低頻雜訊，應切成兩個 zone。"""
    fps = [
        spectral_fingerprint(_white_noise(1), SR),
        spectral_fingerprint(_white_noise(2), SR),
        spectral_fingerprint(_low_passed_noise(3), SR),
        spectral_fingerprint(_low_passed_noise(4), SR),
    ]
    windows = [Interval(0, 1), Interval(10, 11), Interval(20, 21), Interval(30, 31)]
    zones = detect_zones(fps, windows, total_duration=40.0, threshold=0.15)
    assert len(zones) == 2
    assert zones[0].start == 0.0
    assert abs(zones[0].end - zones[1].start) < 1e-6
    assert abs(zones[1].end - 40.0) < 1e-6


def test_detect_zones_returns_single_zone_when_uniform():
    """底噪一致時只應有一個 zone，不得無故切割。"""
    fps = [spectral_fingerprint(_white_noise(s), SR) for s in (1, 2, 3)]
    windows = [Interval(0, 1), Interval(10, 11), Interval(20, 21)]
    zones = detect_zones(fps, windows, total_duration=30.0, threshold=0.15)
    assert len(zones) == 1
    assert zones[0].start == 0.0
    assert abs(zones[0].end - 30.0) < 1e-6
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_fingerprint.py -v
```

Expected: FAIL — `No module named 'ar.fingerprint'`

- [ ] **Step 3: 實作 fingerprint.py**

```python
"""底噪頻譜指紋與錄音條件分區偵測。

分區依據是底噪的頻譜形狀而非音量：換麥克風時音量可能不變，但噪音頻譜必變。
指紋正規化為機率分布後取餘弦距離，因此對整體音量完全不敏感。
"""
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from .ffmpeg_io import FFmpegError, _require
from .silence import Interval

ZONE_THRESHOLD = 0.15  # 指紋餘弦距離門檻，超過視為錄音條件改變
BAND_RATIO = 2.0 ** (1.0 / 3.0)  # 1/3 八度頻帶的頻率比
BAND_START_HZ = 40.0  # 最低頻帶起點


@dataclass
class Zone:
    """一個錄音條件一致的區段。"""
    index: int
    start: float
    end: float
    noise_window_indices: list[int] = field(default_factory=list)


def read_samples(path: Path, start: float, end: float, sample_rate: int = 16000) -> np.ndarray:
    """用 ffmpeg 把指定區間解碼成單聲道 float32 樣本陣列。"""
    duration = max(0.05, end - start)
    cmd = [
        _require("ffmpeg"), "-hide_banner", "-nostdin", "-v", "error",
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(path),
        "-ac", "1", "-ar", str(sample_rate), "-f", "f32le", "-",
    ]
    result = subprocess.run(cmd, capture_output=True)
    if result.returncode != 0:
        raise FFmpegError(f"解碼樣本失敗：{result.stderr.decode('utf-8', 'replace')[-500:]}")
    return np.frombuffer(result.stdout, dtype=np.float32)


def _band_edges(sample_rate: int) -> np.ndarray:
    """產生 1/3 八度頻帶邊界，上限為 Nyquist。"""
    nyquist = sample_rate / 2.0
    edges = [BAND_START_HZ]
    while edges[-1] * BAND_RATIO < nyquist:
        edges.append(edges[-1] * BAND_RATIO)
    edges.append(nyquist)
    return np.array(edges)


def spectral_fingerprint(samples: np.ndarray, sample_rate: int) -> np.ndarray:
    """計算頻譜指紋：功率譜依 1/3 八度聚合後正規化為機率分布。

    正規化使指紋只反映頻譜形狀、不反映音量，這是分區判斷的前提。
    """
    if samples.size < 256:
        raise ValueError("樣本太短，無法計算頻譜指紋")
    windowed = samples.astype(np.float64) * np.hanning(samples.size)
    power = np.abs(np.fft.rfft(windowed)) ** 2
    freqs = np.fft.rfftfreq(samples.size, d=1.0 / sample_rate)
    edges = _band_edges(sample_rate)
    bands = np.zeros(len(edges) - 1)
    for index in range(len(edges) - 1):
        mask = (freqs >= edges[index]) & (freqs < edges[index + 1])
        bands[index] = power[mask].sum()
    total = bands.sum()
    if total <= 0:
        return np.full(bands.size, 1.0 / bands.size)
    return bands / total


def cosine_distance(a: np.ndarray, b: np.ndarray) -> float:
    """兩個指紋的餘弦距離（0 = 完全相同，越大越不同）。"""
    denominator = float(np.linalg.norm(a) * np.linalg.norm(b))
    if denominator == 0.0:
        return 0.0
    return float(1.0 - np.dot(a, b) / denominator)


def detect_zones(fingerprints: list[np.ndarray], windows: list[Interval],
                 total_duration: float, threshold: float = ZONE_THRESHOLD) -> list[Zone]:
    """依相鄰指紋距離切分 zone。

    切點取在「距離超標的兩個噪音窗」之間的中點，因為錄音條件的實際改變點
    必然落在這兩次採樣之間。
    """
    if not fingerprints:
        return [Zone(index=0, start=0.0, end=total_duration, noise_window_indices=[])]

    cut_points: list[float] = []
    cut_after: list[int] = []
    for index in range(len(fingerprints) - 1):
        if cosine_distance(fingerprints[index], fingerprints[index + 1]) > threshold:
            midpoint = (windows[index].end + windows[index + 1].start) / 2.0
            cut_points.append(midpoint)
            cut_after.append(index)

    bounds = [0.0, *cut_points, total_duration]
    zones: list[Zone] = []
    for index in range(len(bounds) - 1):
        zone = Zone(index=index, start=bounds[index], end=bounds[index + 1])
        zone.noise_window_indices = [
            window_index for window_index, window in enumerate(windows)
            if zone.start <= (window.start + window.end) / 2.0 < zone.end
        ]
        zones.append(zone)
    return zones
```

註：`_require` 需從 `ffmpeg_io` 匯出，將其改名為 `require_tool` 並更新 `ffmpeg_io.py` 內三處呼叫，避免跨模組使用底線前綴的私有名稱。

- [ ] **Step 4: 把 ffmpeg_io._require 改名為 require_tool**

修改 `scripts/ar/ffmpeg_io.py`：函式定義改為 `def require_tool(tool: str) -> str:`，`run_ffmpeg` 與 `run_ffprobe_json` 內的兩處 `_require(...)` 一併改為 `require_tool(...)`；`fingerprint.py` 的 import 改為 `from .ffmpeg_io import FFmpegError, require_tool`，內文呼叫同步更新。

- [ ] **Step 5: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/ -v
```

Expected: PASS（全部通過，含前面任務的測試）

- [ ] **Step 6: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入頻譜指紋與錄音條件分區偵測"
```

---

## Task 6: 逐區診斷指標

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/diagnose.py`
- Test: `skills-src/audio-restoration/tests/test_diagnose.py`

**Interfaces:**
- Consumes: `fingerprint.Zone`、`measure.LoudnessStats`、`fingerprint.read_samples`、`timeline.Timeline`
- Produces:
  - `diagnose.ZoneDiagnosis`（dataclass：`zone_index: int`、`start: float`、`end: float`、`noise_lufs: float`、`speech_lufs: float`、`snr_db: float`、`sibilance_ratio: float`、`rumble_ratio: float`、`clipped_ratio: float`、`reverb_slope: float`、`denoise_db: int`、`needs_deesser: bool`、`needs_highpass: bool`、`needs_ai_rescue: bool`、`issues: list[str]`）
  - `diagnose.band_energy_ratio(samples: np.ndarray, sample_rate: int, low_hz: float, high_hz: float) -> float`
  - `diagnose.clipped_ratio(samples: np.ndarray) -> float`
  - `diagnose.reverb_slope(tail_samples: np.ndarray, sample_rate: int) -> float`（輸入必須是「從語句結束時刻起算的尾段」，函式不自行切窗）
  - `diagnose.zone_reverb_slope(path: Path, zone: Zone, utterance_ends: list[float], sample_rate: int = 16000, window: float = 0.3) -> float`
  - `diagnose.pick_denoise_level(snr_db: float) -> int`
  - `diagnose.diagnose_zone(path: Path, zone: Zone, noise_stats: LoudnessStats, speech_stats: LoudnessStats, utterance_ends: list[float], sample_rate: int = 16000) -> ZoneDiagnosis`

**門檻定義（Task 14 以真實素材校準）：**

| 指標 | 門檻 | 動作 |
|---|---|---|
| SNR < 10 dB | AI 救援 | 標記 `needs_ai_rescue` |
| SNR < 20 dB | 重度降噪 | `denoise_db = 24` |
| SNR < 30 dB | 中度降噪 | `denoise_db = 18` |
| SNR ≥ 30 dB | 輕度降噪 | `denoise_db = 12` |
| 5–8kHz 能量佔比 > 0.18 | 齒音過重 | `needs_deesser = True` |
| < 80Hz 能量佔比 > 0.10 | 低頻隆隆 | `needs_highpass = True` |
| 削峰樣本比例 > 0.001 | 削峰 | 記入 `issues` |
| 衰減斜率 > −60 dB/s | 殘響重 | 記入 `issues`，且 SNR 低時併發 AI 救援 |

- [ ] **Step 1: 寫失敗測試**

`tests/test_diagnose.py`：

```python
"""diagnose 模組測試：各項診斷指標的數值行為。"""
import numpy as np

from ar.diagnose import (band_energy_ratio, clipped_ratio, pick_denoise_level,
                         reverb_slope)

SR = 16000


def _tone(freq: float, duration: float = 1.0, amplitude: float = 0.5) -> np.ndarray:
    """產生純音，用來精確驗證頻帶能量歸屬。"""
    t = np.arange(int(duration * SR)) / SR
    return amplitude * np.sin(2 * np.pi * freq * t)


def test_band_energy_ratio_isolates_sibilance_band():
    """6kHz 純音的 5-8kHz 能量佔比應接近 1。"""
    assert band_energy_ratio(_tone(6000.0), SR, 5000.0, 8000.0) > 0.9


def test_band_energy_ratio_low_for_out_of_band_tone():
    """220Hz 純音在 5-8kHz 頻帶的佔比應接近 0。"""
    assert band_energy_ratio(_tone(220.0), SR, 5000.0, 8000.0) < 0.05


def test_band_energy_ratio_detects_rumble():
    """50Hz 純音的 <80Hz 佔比應接近 1。"""
    assert band_energy_ratio(_tone(50.0), SR, 0.0, 80.0) > 0.9


def test_clipped_ratio_counts_saturated_samples():
    """人為造出 10% 削峰樣本，應被算出約 0.1。"""
    samples = np.concatenate([np.full(1000, 0.999), np.full(9000, 0.2)])
    assert 0.09 < clipped_ratio(samples) < 0.11


def test_clipped_ratio_zero_for_clean_signal():
    """未削峰的訊號應回傳 0。"""
    assert clipped_ratio(_tone(220.0, amplitude=0.5)) == 0.0


def _clean_stop_tail() -> np.ndarray:
    """乾淨環境的語句尾段：人聲在 20ms 內結束，其餘只剩底噪。

    這個窗代表「語句結束時刻起算的 300ms」，是 reverb_slope 的契約輸入。
    """
    rng = np.random.default_rng(7)
    voice = _tone(220.0, 0.02, amplitude=0.5)
    floor = 0.002 * rng.standard_normal(int(0.28 * SR))
    return np.concatenate([voice, floor])


def _reverberant_tail() -> np.ndarray:
    """殘響重的語句尾段：300ms 內能量僅衰減約 15dB，拖著長尾。"""
    decay = np.exp(np.linspace(0.0, -15.0 / 20.0 * np.log(10.0), int(0.3 * SR)))
    return _tone(220.0, 0.3, amplitude=0.5) * decay


def test_reverb_slope_steep_for_clean_stop():
    """人聲在 20ms 內跌到底噪，T20 應算出極陡的斜率。

    預期約 -1000 dB/s：20dB 降幅在第 2 幀（0.02 秒）內達成。
    """
    assert reverb_slope(_clean_stop_tail(), SR) < -500.0


def test_reverb_slope_shallow_for_reverberant_tail():
    """殘響尾段 300ms 僅衰減 15dB，未達 20dB 目標，改用總降幅外推約 -50 dB/s。"""
    slope = reverb_slope(_reverberant_tail(), SR)
    assert -60.0 < slope < -40.0


def test_reverberant_tail_is_flatter_than_clean_stop():
    """殘響尾段必須明顯比乾淨結束平緩 —— 這是本指標的鑑別力所在。

    用整段線性迴歸時兩者只差 5.6 dB/s（指標形同失效），T20 法下差距達
    數百 dB/s。這個測試就是在防止有人把 T20 改回迴歸法。
    """
    assert reverb_slope(_reverberant_tail(), SR) > reverb_slope(_clean_stop_tail(), SR) + 400.0


def test_reverb_slope_returns_zero_for_digital_silence():
    """整段數位靜音時回傳 0.0 表示無法判斷，不得偽裝成「衰減極慢」。"""
    assert reverb_slope(np.zeros(int(0.3 * SR)), SR) == 0.0


def test_reverb_slope_returns_zero_when_too_few_frames():
    """樣本不足三幀時回傳 0.0，不做無意義的迴歸。"""
    assert reverb_slope(np.ones(int(0.02 * SR)) * 0.1, SR) == 0.0


def test_pick_denoise_level_maps_snr_to_strength():
    """SNR 越差降噪越強，且分級邊界明確。"""
    assert pick_denoise_level(35.0) == 12
    assert pick_denoise_level(25.0) == 18
    assert pick_denoise_level(15.0) == 24
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_diagnose.py -v
```

Expected: FAIL — `No module named 'ar.diagnose'`

- [ ] **Step 3: 實作 diagnose.py**

```python
"""逐區診斷：算出各項指標並決定該區要掛哪些濾鏡。"""
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from .fingerprint import Zone, read_samples
from .measure import LoudnessStats

AI_RESCUE_SNR = 10.0        # SNR 低於此值，ffmpeg 濾鏡鏈救不回
SIBILANCE_THRESHOLD = 0.18  # 5-8kHz 能量佔比上限
RUMBLE_THRESHOLD = 0.10     # <80Hz 能量佔比上限
CLIP_THRESHOLD = 0.001      # 削峰樣本比例上限
# 衰減斜率門檻（dB/秒），高於此值（更接近 0，衰減更慢）代表殘響重。
# 物理依據：RT60 是能量衰減 60dB 所需秒數，斜率 = -60/RT60。
# 講課教室典型 RT60 約 0.5-1.0 秒，對應 -120 至 -60 dB/s；
# RT60 達 2 秒（明顯回音）對應 -30 dB/s。取 -60 為初值，Task 14 以真實素材校準。
REVERB_SLOPE_THRESHOLD = -60.0


@dataclass
class ZoneDiagnosis:
    """一個 zone 的完整診斷結果與處理決策。"""
    zone_index: int
    start: float
    end: float
    noise_lufs: float        # 該區底噪響度
    speech_lufs: float       # 該區人聲響度
    snr_db: float            # 訊噪比
    sibilance_ratio: float   # 5-8kHz 能量佔比
    rumble_ratio: float      # <80Hz 能量佔比
    clipped_ratio: float     # 削峰樣本比例
    reverb_slope: float      # 語句結束後的能量衰減斜率（dB/秒）
    denoise_db: int          # afftdn 降噪強度
    needs_deesser: bool
    needs_highpass: bool
    needs_ai_rescue: bool
    issues: list[str] = field(default_factory=list)


def band_energy_ratio(samples: np.ndarray, sample_rate: int,
                      low_hz: float, high_hz: float) -> float:
    """指定頻帶能量佔總能量的比例。"""
    if samples.size < 256:
        return 0.0
    windowed = samples.astype(np.float64) * np.hanning(samples.size)
    power = np.abs(np.fft.rfft(windowed)) ** 2
    freqs = np.fft.rfftfreq(samples.size, d=1.0 / sample_rate)
    total = power.sum()
    if total <= 0:
        return 0.0
    mask = (freqs >= low_hz) & (freqs < high_hz)
    return float(power[mask].sum() / total)


def clipped_ratio(samples: np.ndarray) -> float:
    """接近滿刻度的樣本比例（削峰指標）。"""
    if samples.size == 0:
        return 0.0
    return float(np.count_nonzero(np.abs(samples) >= 0.99) / samples.size)


SILENCE_RMS_FLOOR = 1e-6   # 低於此 RMS 視為數位靜音，無法據以判斷衰減
RMS_CLAMP = 1e-20          # log10 的數值保護下限，必須遠低於 SILENCE_RMS_FLOOR
DECAY_TARGET_DB = 20.0     # T20 量測的目標降幅（dB）
FRAME_SECONDS = 0.01       # RMS 分析幀長（秒）


def reverb_slope(tail_samples: np.ndarray, sample_rate: int) -> float:
    """量測一段「語句結束後尾段」的能量衰減速率（dB/秒）。

    採聲學的 T20 概念：找出能量自起始位準下降 DECAY_TARGET_DB 所需的時間 t，
    斜率 = -DECAY_TARGET_DB / t。這讓數值有精確的物理對應 —— 斜率恰好等於
    -60 / RT60，因此門檻可直接以 RT60 推導。

    為何不用整段線性迴歸：語句結束後的尾段是「陡降 + 底噪平台」的階梯形狀，
    對整段做單一迴歸會被後面的長平台稀釋。實測顯示「20ms 內跌 45dB 後平坦」
    與「300ms 內線性衰減 15dB」用迴歸法算出 -55.6 與 -50.0 —— 幾乎相同，
    指標完全失去鑑別力。改用 T20 後兩者為 -1000 與 -49.9。

    重要契約：傳入的樣本**必須**是從語句結束時刻起算的尾段，本函式不自行
    切窗。早期版本自行取 `samples[-tail_seconds:]`，在呼叫端傳入整段 zone
    時會量到「該段最後 0.3 秒」——那可能正在講話中間，量到的根本不是衰減。
    切窗職責交給知道語句邊界的呼叫端（見 zone_reverb_slope）。

    回傳 0.0 代表「無法判斷」：整段都在數位靜音地板、幀數不足、或窗內根本
    沒有衰減（能量持平甚至上升，通常代表語句其實還沒結束）。
    呼叫端不得把 0.0 當成「衰減極慢、殘響很重」。
    """
    frame = max(1, int(FRAME_SECONDS * sample_rate))
    frame_count = tail_samples.size // frame
    if frame_count < 3:
        return 0.0
    frames = tail_samples[: frame_count * frame].reshape(frame_count, frame)
    # clamp 值須遠低於靜音門檻，否則全零訊號算出的 RMS 會恰好等於門檻而漏判
    rms = np.sqrt(np.maximum((frames ** 2).mean(axis=1), RMS_CLAMP))
    if float(rms.max()) < SILENCE_RMS_FLOOR:
        return 0.0

    db = 20.0 * np.log10(rms)
    # 起始位準取前三幀最大值，避免單一幀落在過零點而低估起點
    start_db = float(db[:3].max())
    step = frame / sample_rate

    for index in range(1, frame_count):
        if db[index] <= start_db - DECAY_TARGET_DB:
            return -DECAY_TARGET_DB / (index * step)

    # 整個窗內都沒降滿 DECAY_TARGET_DB：用實際總降幅外推
    total_drop = start_db - float(db[-1])
    elapsed = (frame_count - 1) * step
    if total_drop <= 0.0:
        return 0.0
    return -total_drop / elapsed


def zone_reverb_slope(path: Path, zone: Zone, utterance_ends: list[float],
                      sample_rate: int = 16000, window: float = 0.3) -> float:
    """對 zone 內各語句結束點量測衰減斜率，取中位數代表該區。

    取中位數而非平均：個別語句可能被下一句搶拍、被雜訊蓋掉或落在檔尾被截斷，
    這些離群值會嚴重拉偏平均。無可用語句結束點時回傳 0.0（無法判斷）。
    """
    slopes: list[float] = []
    for end_time in utterance_ends:
        if end_time + window > zone.end:
            continue  # 窗超出 zone 範圍，跳過以免量到下一區的聲學條件
        tail = read_samples(path, end_time, end_time + window, sample_rate)
        slope = reverb_slope(tail, sample_rate)
        if slope != 0.0:  # 0.0 是「無法判斷」的哨兵，不納入統計
            slopes.append(slope)
    if not slopes:
        return 0.0
    return float(np.median(slopes))


def pick_denoise_level(snr_db: float) -> int:
    """依訊噪比選擇 afftdn 降噪強度（dB）。"""
    if snr_db < 20.0:
        return 24
    if snr_db < 30.0:
        return 18
    return 12


def diagnose_zone(path: Path, zone: Zone, noise_stats: LoudnessStats,
                  speech_stats: LoudnessStats, utterance_ends: list[float],
                  sample_rate: int = 16000) -> ZoneDiagnosis:
    """對單一 zone 執行全部診斷並決定處理策略。

    utterance_ends 是落在此 zone 內的各語句結束時刻（秒），用於量測殘響。
    """
    samples = read_samples(path, zone.start, min(zone.end, zone.start + 30.0), sample_rate)
    snr = speech_stats.lufs - noise_stats.lufs
    sibilance = band_energy_ratio(samples, sample_rate, 5000.0, 8000.0)
    rumble = band_energy_ratio(samples, sample_rate, 0.0, 80.0)
    clipped = clipped_ratio(samples)
    slope = zone_reverb_slope(path, zone, utterance_ends, sample_rate)

    issues: list[str] = []
    if clipped > CLIP_THRESHOLD:
        issues.append(f"削峰樣本比例 {clipped:.4f} 超標（修復無法還原已削掉的波形）")
    # slope == 0.0 是「無法判斷」的哨兵，不得當成衰減極慢而誤報殘響
    if slope != 0.0 and slope > REVERB_SLOPE_THRESHOLD:
        issues.append(f"衰減斜率 {slope:.1f} dB/s 過於平緩，空間殘響重")
    if sibilance > SIBILANCE_THRESHOLD:
        issues.append(f"5-8kHz 能量佔比 {sibilance:.3f}，齒音過重")
    if rumble > RUMBLE_THRESHOLD:
        issues.append(f"<80Hz 能量佔比 {rumble:.3f}，低頻隆隆明顯")
    needs_rescue = snr < AI_RESCUE_SNR
    if needs_rescue:
        issues.append(f"SNR 僅 {snr:.1f} dB，ffmpeg 濾鏡鏈無法救回，建議改用 AI 修復")

    return ZoneDiagnosis(
        zone_index=zone.index,
        start=zone.start,
        end=zone.end,
        noise_lufs=noise_stats.lufs,
        speech_lufs=speech_stats.lufs,
        snr_db=snr,
        sibilance_ratio=sibilance,
        rumble_ratio=rumble,
        clipped_ratio=clipped,
        reverb_slope=slope,
        denoise_db=pick_denoise_level(snr),
        needs_deesser=sibilance > SIBILANCE_THRESHOLD,
        needs_highpass=rumble > RUMBLE_THRESHOLD,
        needs_ai_rescue=needs_rescue,
        issues=issues,
    )
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_diagnose.py -v
```

Expected: PASS（8 passed）

- [ ] **Step 5: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入逐區診斷指標"
```

---

## Task 7: report/plan 讀寫與 analyze.py CLI

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/plan_io.py`
- Create: `skills-src/audio-restoration/scripts/analyze.py`
- Test: `skills-src/audio-restoration/tests/test_plan_io.py`
- Test: `skills-src/audio-restoration/tests/test_analyze_cli.py`

**Interfaces:**
- Consumes: 前六個任務的全部模組
- Produces:
  - `plan_io.write_report(work_dir: Path, spec, classification, diagnoses, utterance_stats) -> Path`
  - `plan_io.write_plan(work_dir: Path, spec, diagnoses, utterance_gains, zone_gains, target_lufs) -> Path`
  - `plan_io.load_plan(path: Path) -> dict`
  - `plan_io.PLAN_SCHEMA_VERSION = 1`
  - `analyze.py` CLI：`--input`（必要）、`--work-dir`、`--target`（預設 −16.0）

**這是第一個端到端交付**：跑完可對真實講課音檔產出可讀的體檢報告。

- [ ] **Step 1: 寫 plan_io 的失敗測試**

`tests/test_plan_io.py`：

```python
"""plan_io 模組測試：plan.json 的結構與往返一致性。"""
from pathlib import Path

from ar.plan_io import PLAN_SCHEMA_VERSION, load_plan, write_plan
from ar.diagnose import ZoneDiagnosis
from ar.probe import MediaSpec


def _spec(tmp_path: Path) -> MediaSpec:
    """測試用媒體規格。"""
    return MediaSpec(path=tmp_path / "in.wav", is_video=False, duration=8.0,
                     sample_rate=48000, channels=1, audio_codec="pcm_s16le")


def _diagnosis() -> ZoneDiagnosis:
    """測試用診斷結果。"""
    return ZoneDiagnosis(
        zone_index=0, start=0.0, end=8.0, noise_lufs=-55.0, speech_lufs=-20.0,
        snr_db=35.0, sibilance_ratio=0.05, rumble_ratio=0.02, clipped_ratio=0.0,
        reverb_slope=-60.0, denoise_db=12, needs_deesser=False,
        needs_highpass=False, needs_ai_rescue=False, issues=[],
    )


def test_plan_roundtrip_preserves_values(tmp_path: Path):
    """寫出再讀回的 plan 應保留 zone 決策與逐句增益。"""
    path = write_plan(tmp_path, _spec(tmp_path), [_diagnosis()],
                      utterance_gains=[(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)],
                      zone_gains=[3.0], target_lufs=-16.0)
    plan = load_plan(path)
    assert plan["schema_version"] == PLAN_SCHEMA_VERSION
    assert plan["target_lufs"] == -16.0
    assert plan["zones"][0]["denoise_db"] == 12
    assert plan["zones"][0]["gain_db"] == 3.0
    assert len(plan["utterances"]) == 2
    assert plan["utterances"][1]["gain_db"] == -1.5


def test_plan_is_human_editable_json(tmp_path: Path):
    """plan.json 須為縮排 JSON 且含中文說明欄位，使用者要能手動改。"""
    path = write_plan(tmp_path, _spec(tmp_path), [_diagnosis()],
                      utterance_gains=[(0.0, 8.0, 0.0)], zone_gains=[0.0],
                      target_lufs=-16.0)
    text = path.read_text(encoding="utf-8")
    assert "\n  " in text
    assert "說明" in text
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_plan_io.py -v
```

Expected: FAIL — `No module named 'ar.plan_io'`

- [ ] **Step 3: 實作 plan_io.py**

```python
"""report.json 與 plan.json 的讀寫。

plan.json 是使用者可手動編輯的處理計畫。restore.py 只認 plan.json，
不會自行推測參數 —— 這是「先體檢、再提案、確認後執行」的強制實現。
"""
import json
from dataclasses import asdict
from pathlib import Path

from .diagnose import ZoneDiagnosis
from .probe import MediaSpec

PLAN_SCHEMA_VERSION = 1


def write_report(work_dir: Path, spec: MediaSpec, classification, diagnoses,
                 utterance_stats) -> Path:
    """輸出完整診斷結果（供人工檢閱與修復後比對）。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "schema_version": PLAN_SCHEMA_VERSION,
        "input": str(spec.path),
        "media": {
            "is_video": spec.is_video, "duration": spec.duration,
            "sample_rate": spec.sample_rate, "channels": spec.channels,
            "audio_codec": spec.audio_codec,
        },
        "counts": {
            "utterances": len(classification.utterances),
            "noise_windows": len(classification.noise_windows),
            "nonspeech_events": len(classification.nonspeech_events),
            "zones": len(diagnoses),
        },
        "zones": [asdict(d) for d in diagnoses],
        "utterances": [
            {"index": u.index, "start": u.start, "end": u.end,
             "lufs": s.lufs, "rms_db": s.rms_db, "peak_db": s.peak_db}
            for u, s in zip(classification.utterances, utterance_stats)
        ],
        "nonspeech_events": [
            {"start": e.start, "end": e.end} for e in classification.nonspeech_events
        ],
        # 噪音採樣窗須寫入報告：修復後驗證底噪降幅時，只有這些區間是
        # 經雙重確認、確定不含人聲的量測位置
        "noise_windows": [
            {"start": w.start, "end": w.end} for w in classification.noise_windows
        ],
    }
    path = work_dir / "report.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def write_plan(work_dir: Path, spec: MediaSpec, diagnoses: list[ZoneDiagnosis],
               utterance_gains: list[tuple[float, float, float]],
               zone_gains: list[float], target_lufs: float,
               nonspeech_events: list[tuple[float, float]] | None = None) -> Path:
    """輸出處理計畫。

    utterance_gains 每項為 (start, end, gain_db)。
    nonspeech_events 每項為 (start, end)，這些區間會被額外壓低。
    """
    work_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "schema_version": PLAN_SCHEMA_VERSION,
        "說明": "此檔為處理計畫，可手動編輯後再交給 restore.py。"
                "zones[].gain_db 為區級增益、utterances[].gain_db 為句級增益，"
                "兩者相加即該句實際增益。修改 zone 邊界請同步調整 start/end。",
        "input": str(spec.path),
        "is_video": spec.is_video,
        "target_lufs": target_lufs,
        "zones": [
            {
                "index": d.zone_index, "start": d.start, "end": d.end,
                "gain_db": zone_gains[d.zone_index],
                "denoise_db": d.denoise_db,
                "needs_highpass": d.needs_highpass,
                "needs_deesser": d.needs_deesser,
                "needs_ai_rescue": d.needs_ai_rescue,
                "issues": d.issues,
            }
            for d in diagnoses
        ],
        "utterances": [
            {"start": start, "end": end, "gain_db": gain}
            for start, end, gain in utterance_gains
        ],
        "nonspeech_events": [
            {"start": start, "end": end} for start, end in (nonspeech_events or [])
        ],
    }
    path = work_dir / "plan.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def load_plan(path: Path) -> dict:
    """讀取 plan.json 並驗證 schema 版本。"""
    plan = json.loads(path.read_text(encoding="utf-8"))
    if plan.get("schema_version") != PLAN_SCHEMA_VERSION:
        raise ValueError(
            f"plan.json schema 版本不符（檔案 {plan.get('schema_version')}，"
            f"程式 {PLAN_SCHEMA_VERSION}），請重新執行 analyze.py"
        )
    return plan
```

- [ ] **Step 4: 寫 analyze CLI 的失敗測試**

`tests/test_analyze_cli.py`：

```python
"""analyze.py 端到端測試：對合成音檔跑完整體檢流程。"""
import json
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def _fake_timeline(work_dir: Path) -> None:
    """預先放好時間軸，讓測試不需要真的跑 ASR。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "info": {"language": "zh", "duration": 8.0, "model": "small"},
        "segments": [
            {"id": 1, "start": 2.0, "end": 4.0, "text": "A", "no_speech_prob": 0.0,
             "words": [{"start": 2.0, "end": 4.0, "word": "A", "probability": 0.9}]},
            {"id": 2, "start": 5.0, "end": 7.0, "text": "B", "no_speech_prob": 0.0,
             "words": [{"start": 5.0, "end": 7.0, "word": "B", "probability": 0.9}]},
        ],
    }
    (work_dir / "timeline.json").write_text(json.dumps(payload), encoding="utf-8")


def test_analyze_produces_report_and_plan(synth_wav: Path, tmp_path: Path):
    """跑完 analyze 應產出 report.json 與 plan.json，且逐句增益方向正確。"""
    work_dir = tmp_path / "work"
    _fake_timeline(work_dir)
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "analyze.py"),
         "--input", str(synth_wav), "--work-dir", str(work_dir)],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    plan = json.loads((work_dir / "plan.json").read_text(encoding="utf-8"))
    assert (work_dir / "report.json").exists()
    assert plan["target_lufs"] == -16.0
    # 語句B（小聲）的增益必須大於語句A（大聲）的增益 —— 這是拉平的方向性驗證
    gains = [u["gain_db"] for u in plan["utterances"]]
    assert gains[1] > gains[0]
```

- [ ] **Step 5: 實作 analyze.py**

```python
"""音質體檢 CLI：產出 report.json 與 plan.json，不修改任何音檔。"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ar.diagnose import diagnose_zone
from ar.fingerprint import detect_zones, read_samples, spectral_fingerprint
from ar.gain import compute_utterance_gains, compute_zone_gains
from ar.measure import measure_interval, measure_intervals, measure_utterances
from ar.plan_io import write_plan, write_report
from ar.probe import probe
from ar.segments import classify
from ar.silence import detect_silence
from ar.timeline import ensure_timeline

DEFAULT_TARGET_LUFS = -16.0
WORKING_LUFS = -20.0  # 拉平階段的共同工作位準


def main() -> None:
    """解析參數並執行體檢流程。"""
    parser = argparse.ArgumentParser(description="講課音軌音質體檢")
    parser.add_argument("--input", required=True, type=Path, help="音檔或影片")
    parser.add_argument("--work-dir", required=True, type=Path, help="產出目錄")
    parser.add_argument("--target", type=float, default=DEFAULT_TARGET_LUFS,
                        help="最終響度目標（LUFS），預設 -16")
    args = parser.parse_args()

    spec = probe(args.input)
    timeline = ensure_timeline(spec, args.work_dir)
    silences = detect_silence(args.input, spec.duration)
    classification = classify(timeline, silences, spec.duration)

    if not classification.noise_windows:
        raise SystemExit("找不到可用的噪音採樣窗（可能整段都有人聲），無法建立降噪基準")

    fingerprints = [
        spectral_fingerprint(read_samples(args.input, w.start, w.end), 16000)
        for w in classification.noise_windows
    ]
    zones = detect_zones(fingerprints, classification.noise_windows, spec.duration)

    noise_stats = measure_intervals(args.input, classification.noise_windows)
    utterance_stats = measure_utterances(args.input, classification.utterances)

    diagnoses = []
    for zone in zones:
        indices = zone.noise_window_indices or [0]
        zone_noise = min((noise_stats[i] for i in indices), key=lambda s: s.lufs)
        zone_speech = measure_interval(args.input, zone.start, min(zone.end, zone.start + 60.0))
        # 落在此 zone 內的語句結束時刻，供殘響量測使用
        zone_ends = [
            seg.end for seg in timeline.segments
            if zone.start <= seg.end < zone.end
        ]
        diagnoses.append(
            diagnose_zone(args.input, zone, zone_noise, zone_speech, zone_ends)
        )

    zone_gains = compute_zone_gains(diagnoses, WORKING_LUFS)
    utterance_gains = compute_utterance_gains(
        classification.utterances, utterance_stats, zones, zone_gains, WORKING_LUFS
    )

    write_report(args.work_dir, spec, classification, diagnoses, utterance_stats)
    plan_path = write_plan(
        args.work_dir, spec, diagnoses, utterance_gains, zone_gains, args.target,
        nonspeech_events=[(e.start, e.end) for e in classification.nonspeech_events],
    )
    _print_summary(diagnoses, classification, plan_path)


def _print_summary(diagnoses, classification, plan_path: Path) -> None:
    """印出人類可讀的體檢摘要。"""
    print(f"語句 {len(classification.utterances)} 句／"
          f"噪音採樣窗 {len(classification.noise_windows)} 個／"
          f"非語音雜訊 {len(classification.nonspeech_events)} 段／"
          f"錄音條件分區 {len(diagnoses)} 區")
    for diagnosis in diagnoses:
        print(f"\n[Zone {diagnosis.zone_index}] "
              f"{diagnosis.start:.1f}s – {diagnosis.end:.1f}s")
        print(f"  SNR {diagnosis.snr_db:.1f} dB／降噪 {diagnosis.denoise_db} dB"
              f"／highpass {'是' if diagnosis.needs_highpass else '否'}"
              f"／deesser {'是' if diagnosis.needs_deesser else '否'}")
        for issue in diagnosis.issues:
            print(f"  ⚠ {issue}")
    print(f"\n處理計畫已寫入：{plan_path}")
    print("請檢視並視需要手動調整後，執行 restore.py --plan 進行修復。")


if __name__ == "__main__":
    main()
```

- [ ] **Step 6: 執行測試確認通過**

Task 8 的 `gain.py` 尚未存在，`analyze.py` 會 import 失敗。**先跳到 Task 8 完成 `gain.py`，再回來執行本步驟。** 此依賴為刻意安排：`gain` 的介面由 `analyze` 的使用方式決定。

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_plan_io.py tests/test_analyze_cli.py -v
```

Expected: PASS（3 passed）

- [ ] **Step 7: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入 report/plan 讀寫與體檢 CLI"
```

---

## Task 8: 增益計算、限幅與平滑

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/gain.py`
- Test: `skills-src/audio-restoration/tests/test_gain.py`

**Interfaces:**
- Consumes: `segments.Utterance`、`measure.LoudnessStats`、`fingerprint.Zone`、`diagnose.ZoneDiagnosis`
- Produces:
  - `gain.compute_zone_gains(diagnoses: list[ZoneDiagnosis], working_lufs: float = -20.0) -> list[float]`
  - `gain.compute_utterance_gains(utterances, stats, zones, zone_gains, working_lufs, max_gain_db=6.0, max_step_db=3.0) -> list[tuple[float, float, float]]`
  - `gain.build_volume_expression(utterance_gains: list[tuple[float, float, float]], nonspeech_events: list[tuple[float, float]] | None = None, attenuation_db: float = -6.0, ramp_ms: int = 200) -> str`
  - `gain.NONSPEECH_ATTENUATION_DB = -6.0`

**設計要點：** 拉平只能用純增益。壓縮器會連帶頂高底噪，破壞後續降噪賴以判斷的訊噪比前提。

**非語音雜訊的處置：** 咳嗽、翻頁、椅子聲落在語句的增益區間內，會跟著人聲一起被拉高。必須在這些區間額外疊加負增益，讓它們相對於人聲被壓下去。ffmpeg 的 `between` 條件項相加，故雜訊區間的實際增益 = 該句增益 + 衰減值。

- [ ] **Step 1: 寫失敗測試**

`tests/test_gain.py`：

```python
"""gain 模組測試：增益方向、限幅、平滑、表達式生成。"""
from ar.diagnose import ZoneDiagnosis
from ar.fingerprint import Zone
from ar.gain import (build_volume_expression, compute_utterance_gains,
                     compute_zone_gains)
from ar.measure import LoudnessStats
from ar.segments import Utterance


def _diagnosis(index: int, speech_lufs: float) -> ZoneDiagnosis:
    """建立指定人聲響度的診斷結果。"""
    return ZoneDiagnosis(
        zone_index=index, start=index * 10.0, end=(index + 1) * 10.0,
        noise_lufs=-55.0, speech_lufs=speech_lufs, snr_db=35.0,
        sibilance_ratio=0.05, rumble_ratio=0.02, clipped_ratio=0.0,
        reverb_slope=-60.0, denoise_db=12, needs_deesser=False,
        needs_highpass=False, needs_ai_rescue=False, issues=[],
    )


def test_zone_gain_lifts_quiet_zone_to_working_level():
    """人聲 -28 LUFS 的區段，補到 -20 工作位準應得 +8dB。"""
    gains = compute_zone_gains([_diagnosis(0, -28.0)], working_lufs=-20.0)
    assert abs(gains[0] - 8.0) < 1e-6


def test_zone_gain_attenuates_loud_zone():
    """人聲 -14 LUFS 的區段應被壓低 6dB。"""
    gains = compute_zone_gains([_diagnosis(0, -14.0)], working_lufs=-20.0)
    assert abs(gains[0] + 6.0) < 1e-6


def _setup(lufs_values: list[float]):
    """建立單一 zone、多句的測試情境。"""
    utterances = [Utterance(index=i, start=i * 2.0, end=(i + 1) * 2.0)
                  for i in range(len(lufs_values))]
    stats = [LoudnessStats(lufs=v, rms_db=v, peak_db=v + 10) for v in lufs_values]
    zones = [Zone(index=0, start=0.0, end=len(lufs_values) * 2.0)]
    return utterances, stats, zones


def test_utterance_gain_is_clamped_to_max():
    """一句只有 -40 LUFS（例如咳嗽），增益須被限在 +6dB，不得拉到人聲音量。"""
    utterances, stats, zones = _setup([-20.0, -40.0])
    result = compute_utterance_gains(utterances, stats, zones, [0.0], -20.0,
                                     max_gain_db=6.0, max_step_db=99.0)
    assert result[1][2] <= 6.0


def test_adjacent_gain_step_is_limited():
    """相鄰兩句的增益差不得超過 max_step_db，避免可聽的音量跳動。"""
    utterances, stats, zones = _setup([-20.0, -30.0, -20.0])
    result = compute_utterance_gains(utterances, stats, zones, [0.0], -20.0,
                                     max_gain_db=6.0, max_step_db=3.0)
    gains = [g for _, _, g in result]
    assert abs(gains[1] - gains[0]) <= 3.0 + 1e-6
    assert abs(gains[2] - gains[1]) <= 3.0 + 1e-6


def test_quiet_utterance_gets_positive_gain():
    """小聲句的增益方向必須為正。"""
    utterances, stats, zones = _setup([-20.0, -26.0])
    result = compute_utterance_gains(utterances, stats, zones, [0.0], -20.0)
    assert result[1][2] > 0.0


def test_volume_expression_covers_all_utterances():
    """volume 表達式須包含每一句的時間條件與增益值。"""
    expression = build_volume_expression([(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)])
    assert "between(t,0.000,4.500)" in expression
    assert "between(t,4.500,8.000)" in expression
    assert "2.000" in expression
    assert "-1.500" in expression


def test_volume_expression_is_single_line():
    """表達式必須是單行，換行會讓 ffmpeg 參數解析失敗。"""
    expression = build_volume_expression([(0.0, 1.0, 1.0), (1.0, 2.0, 2.0)])
    assert "\n" not in expression


def test_nonspeech_event_gets_extra_attenuation():
    """咳嗽／翻頁區間須額外疊加負增益，否則會跟著人聲一起被拉高。"""
    expression = build_volume_expression(
        [(0.0, 8.0, 3.0)], nonspeech_events=[(5.0, 5.4)], attenuation_db=-6.0
    )
    assert "between(t,5.000,5.400)*-6.000" in expression


def test_no_attenuation_terms_when_no_events():
    """沒有雜訊事件時不得出現多餘的負增益項。"""
    expression = build_volume_expression([(0.0, 8.0, 3.0)], nonspeech_events=[])
    assert "-6.000" not in expression
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_gain.py -v
```

Expected: FAIL — `No module named 'ar.gain'`

- [ ] **Step 3: 實作 gain.py**

```python
"""增益計算：區級補償、句級補償、限幅、平滑、ffmpeg 表達式生成。

只使用純增益，不使用壓縮器。壓縮會連帶把底噪頂高，破壞後續降噪賴以判斷的
訊噪比前提；純增益只搬動位準，噪音與人聲的比例不變。
"""
from .diagnose import ZoneDiagnosis
from .fingerprint import Zone
from .measure import LoudnessStats
from .segments import Utterance

MAX_GAIN_DB = 6.0   # 單句增益上限，避免把咳嗽、翻頁聲拉到人聲音量
MAX_STEP_DB = 3.0   # 相鄰句增益差上限，避免可聽的音量跳動
RAMP_MS = 200       # 增益交界的線性斜坡長度（毫秒）
NONSPEECH_ATTENUATION_DB = -6.0  # 非語音雜訊區間的額外衰減


def compute_zone_gains(diagnoses: list[ZoneDiagnosis],
                       working_lufs: float = -20.0) -> list[float]:
    """算出每個 zone 補到共同工作位準所需的純增益。"""
    return [working_lufs - d.speech_lufs for d in diagnoses]


def _zone_index_for(time: float, zones: list[Zone]) -> int:
    """判斷某時間點落在哪個 zone。"""
    for zone in zones:
        if zone.start <= time < zone.end:
            return zone.index
    return zones[-1].index if zones else 0


def compute_utterance_gains(utterances: list[Utterance], stats: list[LoudnessStats],
                            zones: list[Zone], zone_gains: list[float],
                            working_lufs: float = -20.0,
                            max_gain_db: float = MAX_GAIN_DB,
                            max_step_db: float = MAX_STEP_DB
                            ) -> list[tuple[float, float, float]]:
    """算出逐句增益，並套用上限與相鄰差限幅。

    回傳每項為 (start, end, gain_db)，此 gain 為**句級增益**，
    實際套用時會與該句所屬 zone 的區級增益相加。
    """
    raw: list[float] = []
    for utterance, stat in zip(utterances, stats):
        zone_index = _zone_index_for((utterance.start + utterance.end) / 2.0, zones)
        applied = zone_gains[zone_index] if zone_index < len(zone_gains) else 0.0
        # 區級增益已補償一部分，句級只需補剩下的差額
        residual = working_lufs - (stat.lufs + applied)
        raw.append(max(-max_gain_db, min(max_gain_db, residual)))

    smoothed = _limit_steps(raw, max_step_db)
    return [(u.start, u.end, g) for u, g in zip(utterances, smoothed)]


def _limit_steps(gains: list[float], max_step_db: float) -> list[float]:
    """限制相鄰句的增益差。

    正反各掃一次，確保任一方向的跳變都被壓平（單向掃描會在下坡段失效）。
    """
    if not gains:
        return []
    forward = list(gains)
    for index in range(1, len(forward)):
        delta = forward[index] - forward[index - 1]
        if abs(delta) > max_step_db:
            forward[index] = forward[index - 1] + max_step_db * (1 if delta > 0 else -1)
    for index in range(len(forward) - 2, -1, -1):
        delta = forward[index] - forward[index + 1]
        if abs(delta) > max_step_db:
            forward[index] = forward[index + 1] + max_step_db * (1 if delta > 0 else -1)
    return forward


def build_volume_expression(utterance_gains: list[tuple[float, float, float]],
                            nonspeech_events: list[tuple[float, float]] | None = None,
                            attenuation_db: float = NONSPEECH_ATTENUATION_DB,
                            ramp_ms: int = RAMP_MS) -> str:
    """把逐句增益組成 ffmpeg volume 濾鏡的時間條件表達式。

    ffmpeg 的 volume 濾鏡以 dB 為單位需搭配 eval=frame。每句一個 between 條件，
    未涵蓋的時間點增益為 0dB（原樣通過）。ramp_ms 目前用於文件記錄；實際
    平滑已由 _limit_steps 在增益值層面完成，且切換點落在靜音中點，不需要
    額外的時間域斜坡。

    非語音雜訊區間額外疊加負增益：between 條件項相加，故該區間的實際增益
    等於「所在語句的增益 + attenuation_db」，達成相對於人聲被壓低的效果。
    """
    if not utterance_gains:
        return "0"
    terms = [
        f"between(t,{start:.3f},{end:.3f})*{gain:.3f}"
        for start, end, gain in utterance_gains
    ]
    for start, end in (nonspeech_events or []):
        terms.append(f"between(t,{start:.3f},{end:.3f})*{attenuation_db:.3f}")
    return "+".join(terms)
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_gain.py -v
```

Expected: PASS（9 passed）

- [ ] **Step 5: 回頭完成 Task 7 Step 6**

`gain.py` 就緒後，執行 Task 7 Step 6 的測試指令，確認 `analyze.py` 端到端通過。

- [ ] **Step 6: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入增益計算、限幅與平滑"
```

---

## Task 9: ffmpeg 濾鏡鏈組裝

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/chain.py`
- Test: `skills-src/audio-restoration/tests/test_chain.py`

**Interfaces:**
- Consumes: `gain.build_volume_expression`
- Produces:
  - `chain.build_zone_chain(zone_plan: dict, volume_expression: str) -> str`
  - `chain.build_loudnorm_measure_chain(target_lufs: float) -> str`
  - `chain.build_loudnorm_apply_chain(target_lufs: float, measured: dict) -> str`

**濾鏡順序（不可調換）：** `volume`（拉平）→ `afftdn`（降噪）→ `highpass` → `deesser` → 後續由 loudnorm/alimiter 於全檔階段處理。

- [ ] **Step 1: 寫失敗測試**

`tests/test_chain.py`：

```python
"""chain 模組測試：濾鏡順序與條件掛載。"""
from ar.chain import (build_loudnorm_apply_chain, build_loudnorm_measure_chain,
                      build_zone_chain)


def _zone_plan(**overrides) -> dict:
    """建立測試用 zone 計畫。"""
    base = {"gain_db": 3.0, "denoise_db": 18, "needs_highpass": True,
            "needs_deesser": True}
    base.update(overrides)
    return base


def test_zone_chain_orders_filters_correctly():
    """順序必須是 volume → afftdn → highpass → deesser。"""
    chain = build_zone_chain(_zone_plan(), "between(t,0.000,4.000)*2.000")
    positions = [chain.index(name) for name in ("volume", "afftdn", "highpass", "deesser")]
    assert positions == sorted(positions)


def test_zone_chain_includes_zone_and_utterance_gain():
    """區級增益與句級表達式都要出現在 volume 濾鏡中。"""
    chain = build_zone_chain(_zone_plan(gain_db=3.0), "between(t,0.000,4.000)*2.000")
    assert "3.000" in chain
    assert "between(t,0.000,4.000)*2.000" in chain
    assert "eval=frame" in chain


def test_zone_chain_omits_highpass_when_not_needed():
    """不需要時不得掛 highpass —— 無差別套用會削掉男聲低頻。"""
    chain = build_zone_chain(_zone_plan(needs_highpass=False), "0")
    assert "highpass" not in chain


def test_zone_chain_omits_deesser_when_not_needed():
    """不需要時不得掛 deesser。"""
    chain = build_zone_chain(_zone_plan(needs_deesser=False), "0")
    assert "deesser" not in chain


def test_zone_chain_uses_planned_denoise_strength():
    """降噪強度必須取自計畫值，不得寫死。"""
    assert "nr=24" in build_zone_chain(_zone_plan(denoise_db=24), "0")


def test_no_compressor_in_chain():
    """禁止出現壓縮器 —— 壓縮會頂高底噪，破壞降噪前提。"""
    chain = build_zone_chain(_zone_plan(), "0")
    assert "acompressor" not in chain
    assert "dynaudnorm" not in chain
    assert "speechnorm" not in chain


def test_loudnorm_measure_chain_requests_json():
    """第一段量測必須輸出 JSON 才能餵給第二段。"""
    chain = build_loudnorm_measure_chain(-16.0)
    assert "print_format=json" in chain
    assert "I=-16.0" in chain
    assert "TP=-1.5" in chain


def test_loudnorm_apply_chain_uses_measured_values():
    """第二段必須帶入第一段量到的四個 measured 值並開啟 linear。"""
    measured = {"input_i": "-23.1", "input_tp": "-5.2",
                "input_lra": "8.3", "input_thresh": "-33.4",
                "target_offset": "0.4"}
    chain = build_loudnorm_apply_chain(-16.0, measured)
    for value in ("-23.1", "-5.2", "8.3", "-33.4", "0.4"):
        assert value in chain
    assert "linear=true" in chain
    assert "alimiter" in chain
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_chain.py -v
```

Expected: FAIL — `No module named 'ar.chain'`

- [ ] **Step 3: 實作 chain.py**

```python
"""ffmpeg 濾鏡鏈組裝。

順序固定：volume（拉平）→ afftdn（降噪）→ highpass → deesser。
不可調換：afftdn 是門檻式運算，訊號位準決定何者被判為噪音，故必須先拉平。
highpass 與 deesser 只在該區診斷確有需要時才掛，無差別套用會削掉男聲低頻
或讓咬字變鈍。
"""
TRUE_PEAK = -1.5  # 目標真峰值（dBTP）
LRA = 11          # 目標響度範圍


def build_zone_chain(zone_plan: dict, volume_expression: str) -> str:
    """組出單一 zone 的濾鏡鏈（不含最終響度處理）。"""
    zone_gain = float(zone_plan["gain_db"])
    # 區級增益為常數，句級增益為時間條件表達式，兩者相加
    filters = [f"volume=volume='{zone_gain:.3f}+({volume_expression})':eval=frame"]
    filters.append(f"afftdn=nr={int(zone_plan['denoise_db'])}:nf=-40:tn=1")
    if zone_plan.get("needs_highpass"):
        filters.append("highpass=f=80")
    if zone_plan.get("needs_deesser"):
        filters.append("deesser=i=0.4:m=0.5:f=0.5")
    return ",".join(filters)


def build_loudnorm_measure_chain(target_lufs: float) -> str:
    """兩段式 loudnorm 的第一段：量測，輸出 JSON。"""
    return (f"loudnorm=I={target_lufs}:TP={TRUE_PEAK}:LRA={LRA}"
            f":print_format=json")


def build_loudnorm_apply_chain(target_lufs: float, measured: dict) -> str:
    """兩段式 loudnorm 的第二段：帶入量測值套用，並以 alimiter 收尾。

    linear=true 讓 loudnorm 走線性增益而非動態壓縮，避免破壞已拉平的動態。
    """
    return (
        f"loudnorm=I={target_lufs}:TP={TRUE_PEAK}:LRA={LRA}"
        f":measured_I={measured['input_i']}"
        f":measured_TP={measured['input_tp']}"
        f":measured_LRA={measured['input_lra']}"
        f":measured_thresh={measured['input_thresh']}"
        f":offset={measured['target_offset']}"
        f":linear=true:print_format=summary,"
        f"alimiter=limit={10 ** (TRUE_PEAK / 20):.4f}"
    )
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_chain.py -v
```

Expected: PASS（8 passed）

- [ ] **Step 5: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入 ffmpeg 濾鏡鏈組裝"
```

---

## Task 10: 分區渲染、串接與輸出

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/render.py`
- Test: `skills-src/audio-restoration/tests/test_render.py`

**Interfaces:**
- Consumes: `chain.*`、`ffmpeg_io.run_ffmpeg`、`probe.MediaSpec`
- Produces:
  - `render.render_zones(input_path: Path, plan: dict, work_dir: Path) -> list[Path]`
  - `render.concat_zones(parts: list[Path], out_path: Path) -> Path`
  - `render.apply_loudnorm(input_path: Path, out_path: Path, target_lufs: float) -> Path`
  - `render.mux_video(video_path: Path, audio_path: Path, out_path: Path) -> Path`
  - `render.export_asr_wav(input_path: Path, out_path: Path) -> Path`

- [ ] **Step 1: 寫失敗測試**

`tests/test_render.py`：

```python
"""render 模組測試：分區渲染、串接、響度收斂、ASR WAV。"""
from pathlib import Path

from ar.measure import measure_interval
from ar.probe import probe
from ar.render import apply_loudnorm, concat_zones, export_asr_wav, render_zones


def _plan(input_path: Path) -> dict:
    """建立涵蓋整支合成音檔的兩區計畫。"""
    return {
        "schema_version": 1,
        "input": str(input_path),
        "is_video": False,
        "target_lufs": -16.0,
        "zones": [
            {"index": 0, "start": 0.0, "end": 4.5, "gain_db": 0.0, "denoise_db": 12,
             "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
            {"index": 1, "start": 4.5, "end": 8.0, "gain_db": 6.0, "denoise_db": 12,
             "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
        ],
        "utterances": [
            {"start": 0.0, "end": 4.5, "gain_db": 0.0},
            {"start": 4.5, "end": 8.0, "gain_db": 3.0},
        ],
    }


def test_render_zones_produces_one_file_per_zone(synth_wav: Path, tmp_path: Path):
    """兩個 zone 應產出兩個中繼檔。"""
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    assert len(parts) == 2
    assert all(p.exists() for p in parts)


def test_concat_preserves_total_duration(synth_wav: Path, tmp_path: Path):
    """串接後總時長應與原檔一致（誤差 0.1 秒內）。"""
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    assert abs(probe(merged).duration - 8.0) < 0.1


def test_zone_gain_actually_raises_level(synth_wav: Path, tmp_path: Path):
    """zone 1 設 +6dB 區級增益 +3dB 句級，該段響度應明顯高於處理前。"""
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    before = measure_interval(synth_wav, 5.2, 6.8)
    after = measure_interval(merged, 5.2, 6.8)
    assert after.lufs - before.lufs > 6.0


def test_apply_loudnorm_converges_to_target(synth_wav: Path, tmp_path: Path):
    """兩段式 loudnorm 後整體響度應收斂到目標 ±1.5 LUFS 內。"""
    out = apply_loudnorm(synth_wav, tmp_path / "norm.wav", target_lufs=-16.0)
    stats = measure_interval(out, 0.0, 8.0)
    assert abs(stats.lufs - (-16.0)) < 1.5


def test_nonspeech_event_is_attenuated(synth_wav: Path, tmp_path: Path):
    """標記為非語音雜訊的區間，響度應低於同一句的其他部分約 6dB。"""
    plan = _plan(synth_wav)
    plan["nonspeech_events"] = [{"start": 2.5, "end": 3.2}]
    parts = render_zones(synth_wav, plan, tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    attenuated = measure_interval(merged, 2.6, 3.1)
    normal = measure_interval(merged, 3.4, 3.9)
    assert 4.0 < normal.lufs - attenuated.lufs < 8.0


def test_export_asr_wav_is_16k_mono(synth_wav: Path, tmp_path: Path):
    """供 ASR 使用的 WAV 必須是 16kHz 單聲道。"""
    out = export_asr_wav(synth_wav, tmp_path / "asr.wav")
    spec = probe(out)
    assert spec.sample_rate == 16000
    assert spec.channels == 1
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_render.py -v
```

Expected: FAIL — `No module named 'ar.render'`

- [ ] **Step 3: 實作 render.py**

```python
"""實際執行 ffmpeg：分區渲染、串接、響度收斂、影片換揉、ASR WAV 匯出。"""
import json
import re
from pathlib import Path

from .chain import (build_loudnorm_apply_chain, build_loudnorm_measure_chain,
                    build_zone_chain)
from .ffmpeg_io import FFmpegError, run_ffmpeg
from .gain import build_volume_expression

_JSON_RE = re.compile(r"\{[^{}]*\"input_i\"[^{}]*\}", re.DOTALL)


def render_zones(input_path: Path, plan: dict, work_dir: Path) -> list[Path]:
    """逐 zone 套用濾鏡鏈，各自輸出成中繼 WAV。

    分區處理而非單一濾鏡鏈的原因：每個 zone 的降噪基準與濾鏡組合不同，
    ffmpeg 無法在單一濾鏡鏈中對不同時間段套用不同的 afftdn 參數。
    中繼一律用 WAV（無損），避免多次有損編碼累積失真。
    """
    work_dir.mkdir(parents=True, exist_ok=True)
    parts: list[Path] = []
    for zone in plan["zones"]:
        # 只保留與此 zone 重疊的句級增益，並把時間平移到該段的相對時間軸
        local_gains = [
            (max(0.0, u["start"] - zone["start"]),
             min(zone["end"], u["end"]) - zone["start"],
             u["gain_db"])
            for u in plan["utterances"]
            if u["start"] < zone["end"] and u["end"] > zone["start"]
        ]
        # 落在此 zone 內的非語音雜訊，同樣平移到相對時間軸後額外壓低
        local_events = [
            (max(0.0, e["start"] - zone["start"]),
             min(zone["end"], e["end"]) - zone["start"])
            for e in plan.get("nonspeech_events", [])
            if e["start"] < zone["end"] and e["end"] > zone["start"]
        ]
        expression = build_volume_expression(local_gains, local_events)
        out_path = work_dir / f"zone-{zone['index']:03d}.wav"
        run_ffmpeg([
            "-y", "-ss", f"{zone['start']:.3f}",
            "-t", f"{zone['end'] - zone['start']:.3f}",
            "-i", str(input_path),
            "-af", build_zone_chain(zone, expression),
            "-ac", "1", "-c:a", "pcm_s16le", str(out_path),
        ])
        parts.append(out_path)
    return parts


def concat_zones(parts: list[Path], out_path: Path) -> Path:
    """把各 zone 的中繼檔按序串接。"""
    list_file = out_path.parent / "concat-list.txt"
    list_file.write_text(
        "\n".join(f"file '{part.as_posix()}'" for part in parts), encoding="utf-8"
    )
    run_ffmpeg([
        "-y", "-f", "concat", "-safe", "0", "-i", str(list_file),
        "-c", "copy", str(out_path),
    ])
    return out_path


def apply_loudnorm(input_path: Path, out_path: Path, target_lufs: float) -> Path:
    """兩段式 loudnorm：先量測再套用，最後以 alimiter 收尾。

    單段式 loudnorm 走的是動態壓縮路徑，會破壞前面辛苦拉平的動態關係；
    兩段式帶入 measured 值後可走 linear 模式，只做線性增益搬移。
    """
    measure_stderr = run_ffmpeg([
        "-i", str(input_path), "-af", build_loudnorm_measure_chain(target_lufs),
        "-f", "null", "-",
    ])
    match = _JSON_RE.search(measure_stderr)
    if not match:
        raise FFmpegError("loudnorm 量測失敗：找不到 JSON 輸出")
    measured = json.loads(match.group(0))
    run_ffmpeg([
        "-y", "-i", str(input_path),
        "-af", build_loudnorm_apply_chain(target_lufs, measured),
        "-c:a", "pcm_s16le", str(out_path),
    ])
    return out_path


def mux_video(video_path: Path, audio_path: Path, out_path: Path) -> Path:
    """把修復後的音軌換揉回原影片，影像軌直接複製不重編。"""
    run_ffmpeg([
        "-y", "-i", str(video_path), "-i", str(audio_path),
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
        "-shortest", str(out_path),
    ])
    return out_path


def export_asr_wav(input_path: Path, out_path: Path) -> Path:
    """匯出 16kHz 單聲道 WAV，供 faster-whisper 等 ASR 使用。"""
    run_ffmpeg([
        "-y", "-i", str(input_path), "-ac", "1", "-ar", "16000",
        "-c:a", "pcm_s16le", str(out_path),
    ])
    return out_path
```

- [ ] **Step 4: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_render.py -v
```

Expected: PASS（6 passed）

- [ ] **Step 5: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入分區渲染、串接與輸出"
```

---

## Task 11: 四指標驗證與 AB 試聽片段

**Files:**
- Create: `skills-src/audio-restoration/scripts/ar/verify.py`
- Create: `skills-src/audio-restoration/scripts/ar/preview.py`
- Test: `skills-src/audio-restoration/tests/test_verify.py`
- Test: `skills-src/audio-restoration/tests/test_preview.py`

**Interfaces:**
- Consumes: `measure.LoudnessStats`、`diagnose.ZoneDiagnosis`、`ffmpeg_io.run_ffmpeg`
- Produces:
  - `verify.Check`（dataclass：`name: str`、`passed: bool`、`detail: str`）
  - `verify.VerifyResult`（dataclass：`passed: bool`、`checks: list[Check]`）
  - `verify.verify(before: dict, after: dict, target_lufs: float) -> VerifyResult`
  - `preview.pick_preview_start(plan: dict) -> float`
  - `preview.build_ab_preview(before_path: Path, after_path: Path, start: float, out_path: Path, duration: float = 30.0) -> Path`

**四項驗證指標（全部須通過）：**

| 指標 | 合格條件 |
|---|---|
| 底噪下降 | `after.noise_lufs < before.noise_lufs` 且降幅 ≤ 25dB |
| 響度收斂 | `abs(after.integrated_lufs - target) <= 0.5` |
| 真峰值 | `after.true_peak <= -1.5` |
| 拉平生效 | `after.utterance_lufs_stdev < before.utterance_lufs_stdev` |

- [ ] **Step 1: 寫 verify 的失敗測試**

`tests/test_verify.py`：

```python
"""verify 模組測試：四項硬指標的通過與失敗判定。"""
from ar.verify import verify


def _before() -> dict:
    """修復前的指標。"""
    return {"noise_lufs": -45.0, "integrated_lufs": -24.0,
            "true_peak": -3.0, "utterance_lufs_stdev": 6.0}


def _after(**overrides) -> dict:
    """修復後的指標（可覆寫單項以測試失敗情境）。"""
    base = {"noise_lufs": -58.0, "integrated_lufs": -16.1,
            "true_peak": -1.6, "utterance_lufs_stdev": 1.8}
    base.update(overrides)
    return base


def test_all_checks_pass_for_good_result():
    """四項全數達標時應通過。"""
    result = verify(_before(), _after(), target_lufs=-16.0)
    assert result.passed is True
    assert len(result.checks) == 4


def test_excessive_noise_reduction_fails():
    """底噪降幅超過 25dB 視為降噪過頭，把人聲一併削掉了。"""
    result = verify(_before(), _after(noise_lufs=-75.0), target_lufs=-16.0)
    assert result.passed is False
    assert any("降噪過頭" in c.detail for c in result.checks if not c.passed)


def test_loudness_off_target_fails():
    """響度未收斂到目標 ±0.5 應失敗。"""
    result = verify(_before(), _after(integrated_lufs=-14.0), target_lufs=-16.0)
    assert result.passed is False


def test_true_peak_over_limit_fails():
    """真峰值超過 -1.5 dBTP 應失敗。"""
    result = verify(_before(), _after(true_peak=-0.5), target_lufs=-16.0)
    assert result.passed is False


def test_flattening_not_effective_fails():
    """句間標準差沒有變小代表拉平未生效。"""
    result = verify(_before(), _after(utterance_lufs_stdev=6.5), target_lufs=-16.0)
    assert result.passed is False
    assert any("拉平" in c.detail for c in result.checks if not c.passed)


def test_failed_check_names_the_stage():
    """失敗時必須指出是哪個環節，不得只回傳布林值。"""
    result = verify(_before(), _after(integrated_lufs=-20.0), target_lufs=-16.0)
    failed = [c for c in result.checks if not c.passed]
    assert len(failed) == 1
    assert failed[0].name == "響度收斂"
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_verify.py -v
```

Expected: FAIL — `No module named 'ar.verify'`

- [ ] **Step 3: 實作 verify.py**

```python
"""修復結果的四項硬指標驗證。

不靠耳朵主觀判斷。任一項不合格即報告失敗並指出失效環節，不得靜默通過。
"""
from dataclasses import dataclass, field

MAX_NOISE_DROP_DB = 25.0   # 底噪降幅上限，超過代表降噪把人聲也削了
LOUDNESS_TOLERANCE = 0.5   # 響度收斂容差（LUFS）
TRUE_PEAK_LIMIT = -1.5     # 真峰值上限（dBTP）


@dataclass
class Check:
    """單項驗證結果。"""
    name: str
    passed: bool
    detail: str


@dataclass
class VerifyResult:
    """整體驗證結果。"""
    passed: bool
    checks: list[Check] = field(default_factory=list)


def verify(before: dict, after: dict, target_lufs: float) -> VerifyResult:
    """比對修復前後指標，輸出四項檢查結果。"""
    checks = [
        _check_noise(before, after),
        _check_loudness(after, target_lufs),
        _check_true_peak(after),
        _check_flattening(before, after),
    ]
    return VerifyResult(passed=all(c.passed for c in checks), checks=checks)


def _check_noise(before: dict, after: dict) -> Check:
    """底噪應下降，但降幅過大代表降噪過頭。"""
    drop = before["noise_lufs"] - after["noise_lufs"]
    if drop <= 0:
        return Check("底噪下降", False, f"底噪未下降（變化 {drop:.1f} dB），降噪未生效")
    if drop > MAX_NOISE_DROP_DB:
        return Check("底噪下降", False,
                     f"底噪下降 {drop:.1f} dB 超過 {MAX_NOISE_DROP_DB} dB 上限，"
                     f"降噪過頭，人聲可能一併被削除，請調低 denoise_db 重跑")
    return Check("底噪下降", True, f"底噪下降 {drop:.1f} dB")


def _check_loudness(after: dict, target_lufs: float) -> Check:
    """整體響度須收斂到目標值容差內。"""
    delta = abs(after["integrated_lufs"] - target_lufs)
    passed = delta <= LOUDNESS_TOLERANCE
    return Check("響度收斂", passed,
                 f"實測 {after['integrated_lufs']:.1f} LUFS，"
                 f"目標 {target_lufs:.1f}，誤差 {delta:.2f}"
                 + ("" if passed else "，loudnorm 未收斂"))


def _check_true_peak(after: dict) -> Check:
    """真峰值不得超標。"""
    passed = after["true_peak"] <= TRUE_PEAK_LIMIT
    return Check("真峰值", passed,
                 f"實測 {after['true_peak']:.1f} dBTP，上限 {TRUE_PEAK_LIMIT}"
                 + ("" if passed else "，alimiter 失效"))


def _check_flattening(before: dict, after: dict) -> Check:
    """句間響度標準差變小是拉平成功的量化證據。"""
    passed = after["utterance_lufs_stdev"] < before["utterance_lufs_stdev"]
    return Check("拉平生效", passed,
                 f"句間標準差 {before['utterance_lufs_stdev']:.2f} → "
                 f"{after['utterance_lufs_stdev']:.2f}"
                 + ("" if passed else "，拉平未生效"))
```

- [ ] **Step 4: 寫 preview 的失敗測試**

`tests/test_preview.py`：

```python
"""preview 模組測試：AB 試聽片段挑選與生成。"""
from pathlib import Path

from ar.preview import build_ab_preview, pick_preview_start
from ar.probe import probe


def _plan(denoise_levels: list[int]) -> dict:
    """建立指定各區降噪強度的計畫。"""
    return {
        "zones": [
            {"index": i, "start": i * 3.0, "end": (i + 1) * 3.0, "denoise_db": level}
            for i, level in enumerate(denoise_levels)
        ]
    }


def test_pick_preview_start_returns_noisiest_zone_start():
    """應挑出降噪最重（即 SNR 最差）的區段起點，那裡最能看出修復效果。"""
    assert pick_preview_start(_plan([12, 24, 18])) == 3.0


def test_pick_preview_start_handles_uniform_plan():
    """各區強度相同時取第一區，不得因無最大值而失敗。"""
    assert pick_preview_start(_plan([12, 12, 12])) == 0.0


def test_ab_preview_is_concatenation_of_both(synth_wav: Path, tmp_path: Path):
    """AB 片段長度應為兩段之和（前 2 秒 + 後 2 秒 = 4 秒）。"""
    out = build_ab_preview(synth_wav, synth_wav, start=2.0,
                           out_path=tmp_path / "ab.wav", duration=2.0)
    assert abs(probe(out).duration - 4.0) < 0.2
```

- [ ] **Step 5: 實作 preview.py**

```python
"""AB 試聽片段：把修復前後的同一段接在一起，供人耳直接比對。"""
from pathlib import Path

from .ffmpeg_io import run_ffmpeg


def pick_preview_start(plan: dict) -> float:
    """挑出試聽起點：降噪強度最高的區段起點。

    denoise_db 由 SNR 反推而來，取最高者即取 SNR 最差的區段 —— 那裡最能
    看出修復是否有效。以 plan 而非診斷物件為輸入，因為 restore 階段只有
    plan.json 可用（使用者可能已手動調整過）。
    """
    zone = max(plan["zones"], key=lambda z: z["denoise_db"])
    return float(zone["start"])


def build_ab_preview(before_path: Path, after_path: Path, start: float,
                     out_path: Path, duration: float = 30.0) -> Path:
    """輸出「修復前 → 修復後」串接的試聽片段。"""
    run_ffmpeg([
        "-y",
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(before_path),
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(after_path),
        "-filter_complex", "[0:a][1:a]concat=n=2:v=0:a=1[out]",
        "-map", "[out]", "-ac", "1", "-c:a", "pcm_s16le", str(out_path),
    ])
    return out_path
```

- [ ] **Step 6: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_verify.py tests/test_preview.py -v
```

Expected: PASS（9 passed）

- [ ] **Step 7: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入四指標驗證與 AB 試聽片段"
```

---

## Task 12: restore.py CLI 與計畫閘門

**Files:**
- Create: `skills-src/audio-restoration/scripts/restore.py`
- Create: `skills-src/audio-restoration/scripts/ar/metrics.py`
- Test: `skills-src/audio-restoration/tests/test_metrics.py`
- Test: `skills-src/audio-restoration/tests/test_restore_cli.py`

**Interfaces:**
- Consumes: 全部既有模組
- Produces:
  - `metrics.collect_metrics(path: Path, plan: dict, report_path: Path | None = None) -> dict`（回傳 `verify.verify` 需要的四個鍵）
  - `restore.py` CLI：`--plan`（必要）、`--out`、`--work-dir`

**這是第二個端到端交付**：跑完可對真實講課音檔完成修復並輸出驗證結果。

- [ ] **Step 1: 寫失敗測試**

`tests/test_metrics.py`：

```python
"""metrics 模組測試：底噪必須取自 report.json 的噪音採樣窗。"""
import json
from pathlib import Path

from ar.metrics import collect_metrics


def _metrics_plan() -> dict:
    """utterances 邊界相接的計畫（analyze.py 的實際輸出形態）。"""
    return {
        "schema_version": 1, "is_video": False, "target_lufs": -16.0,
        "zones": [{"index": 0, "start": 0.0, "end": 8.0, "gain_db": 0.0,
                   "denoise_db": 12, "needs_highpass": False,
                   "needs_deesser": False, "needs_ai_rescue": False, "issues": []}],
        "utterances": [{"start": 0.0, "end": 4.5, "gain_db": 0.0},
                       {"start": 4.5, "end": 8.0, "gain_db": 0.0}],
    }


def _metrics_report(path: Path) -> Path:
    """含噪音採樣窗的 report.json。"""
    payload = {"schema_version": 1,
               "noise_windows": [{"start": 0.2, "end": 1.8},
                                 {"start": 4.1, "end": 4.9}]}
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def test_noise_measured_from_report_windows(synth_wav: Path, tmp_path: Path):
    """底噪應取自噪音採樣窗，而非退化成整體響度。

    合成音檔底噪約 -54 dBFS、整體約 -20 LUFS，兩者差距極大；若量到接近
    整體的值，代表採樣窗沒有被使用。
    """
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=_metrics_report(tmp_path / "report.json"))
    assert metrics["noise_lufs"] < -40.0
    assert metrics["noise_lufs"] < metrics["integrated_lufs"] - 20.0


def test_utterance_stdev_reflects_level_difference(synth_wav: Path, tmp_path: Path):
    """兩句相差約 12dB，句間標準差應明顯大於 0。"""
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=_metrics_report(tmp_path / "r.json"))
    assert metrics["utterance_lufs_stdev"] > 3.0


def test_noise_falls_back_when_report_missing(synth_wav: Path, tmp_path: Path):
    """沒有 report.json 時退回整體響度，不得拋例外中斷修復流程。"""
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=tmp_path / "nonexistent.json")
    assert metrics["noise_lufs"] == metrics["integrated_lufs"]
```

`tests/test_restore_cli.py`：

```python
"""restore.py 端到端測試：閘門、輸出、驗證。"""
import json
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def _write_plan(path: Path, input_path: Path) -> Path:
    """寫出可執行的處理計畫。"""
    plan = {
        "schema_version": 1,
        "input": str(input_path),
        "is_video": False,
        "target_lufs": -16.0,
        "zones": [
            {"index": 0, "start": 0.0, "end": 4.5, "gain_db": 0.0, "denoise_db": 12,
             "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
            {"index": 1, "start": 4.5, "end": 8.0, "gain_db": 6.0, "denoise_db": 12,
             "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
        ],
        "utterances": [
            {"start": 0.0, "end": 4.5, "gain_db": 0.0},
            {"start": 4.5, "end": 8.0, "gain_db": 3.0},
        ],
    }
    path.write_text(json.dumps(plan, ensure_ascii=False), encoding="utf-8")
    return path


def test_restore_refuses_without_plan(tmp_path: Path):
    """沒有 plan.json 時必須拒絕執行 —— 這是體檢閘門的強制實現。"""
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(tmp_path / "missing.json"), "--out", str(tmp_path / "o.wav")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode != 0
    assert "plan" in (result.stderr + result.stdout).lower()


def test_restore_produces_output_and_verification(synth_wav: Path, tmp_path: Path):
    """正常執行應產出修復檔、ASR WAV、AB 片段與 verify.json。"""
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    out_path = tmp_path / "restored.wav"
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(out_path),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    assert out_path.exists()
    assert (tmp_path / "work" / "verify.json").exists()
    assert (tmp_path / "work" / "restored-16k.wav").exists()
    assert (tmp_path / "work" / "preview-ab.wav").exists()


def test_restore_output_hits_target_loudness(synth_wav: Path, tmp_path: Path):
    """修復後整體響度應落在目標 ±1.0 LUFS。"""
    from ar.measure import measure_interval
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    out_path = tmp_path / "restored.wav"
    subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(out_path),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, check=True,
    )
    assert abs(measure_interval(out_path, 0.0, 8.0).lufs - (-16.0)) < 1.0
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_restore_cli.py -v
```

Expected: FAIL — restore.py 不存在

- [ ] **Step 3: 實作 metrics.py**

```python
"""收集驗證所需的四項指標。"""
import json
import statistics
from pathlib import Path

from .measure import measure_interval


def collect_metrics(path: Path, plan: dict, report_path: Path | None = None) -> dict:
    """量測整體響度、真峰值、底噪與句間標準差。

    底噪取自 report.json 記錄的噪音採樣窗 —— 那是唯一經雙重確認、確定不含
    人聲的區間。不可改從 plan.json 的 utterances 推算空隙：那些邊界已外擴至
    靜音中點、彼此相接，中間沒有任何空隙可用。
    """
    overall = measure_interval(path, 0.0, _total_duration(plan))
    utterance_lufs = [
        measure_interval(path, u["start"], u["end"]).lufs for u in plan["utterances"]
    ]
    windows = _noise_windows(report_path)
    if windows:
        noise_lufs = min(measure_interval(path, start, end).lufs
                         for start, end in windows)
    else:
        noise_lufs = overall.lufs
    return {
        "noise_lufs": noise_lufs,
        "integrated_lufs": overall.lufs,
        "true_peak": overall.peak_db,
        "utterance_lufs_stdev": (
            statistics.pstdev(utterance_lufs) if len(utterance_lufs) > 1 else 0.0
        ),
    }


def _total_duration(plan: dict) -> float:
    """從計畫推得總時長。"""
    return max(zone["end"] for zone in plan["zones"])


def _noise_windows(report_path: Path | None) -> list[tuple[float, float]]:
    """從 report.json 取出噪音採樣窗；沒有報告時回傳空清單。"""
    if report_path is None or not report_path.exists():
        return []
    report = json.loads(report_path.read_text(encoding="utf-8"))
    return [(w["start"], w["end"]) for w in report.get("noise_windows", [])]
```

- [ ] **Step 4: 實作 restore.py**

```python
"""音質修復 CLI：只吃 plan.json，不自行推測參數。"""
import argparse
import json
import sys
from dataclasses import asdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ar.metrics import collect_metrics
from ar.plan_io import load_plan
from ar.preview import build_ab_preview, pick_preview_start
from ar.probe import probe
from ar.render import (apply_loudnorm, concat_zones, export_asr_wav, mux_video,
                       render_zones)
from ar.verify import verify

PREVIEW_SECONDS = 30.0


def main() -> None:
    """解析參數並執行修復流程。"""
    parser = argparse.ArgumentParser(description="講課音軌音質修復")
    parser.add_argument("--plan", required=True, type=Path, help="analyze.py 產出的 plan.json")
    parser.add_argument("--out", required=True, type=Path, help="輸出檔路徑")
    parser.add_argument("--work-dir", type=Path, help="中繼檔目錄，預設為輸出檔同層 work/")
    args = parser.parse_args()

    if not args.plan.exists():
        raise SystemExit(
            f"找不到 plan：{args.plan}\n"
            "restore.py 不會自行推測參數，請先執行 analyze.py 產出處理計畫。"
        )
    plan = load_plan(args.plan)
    work_dir = args.work_dir or args.out.parent / "work"
    work_dir.mkdir(parents=True, exist_ok=True)

    input_path = Path(plan["input"])
    spec = probe(input_path)

    report_path = args.plan.parent / "report.json"
    before = collect_metrics(input_path, plan, report_path=report_path)

    parts = render_zones(input_path, plan, work_dir)
    merged = concat_zones(parts, work_dir / "merged.wav")
    normalized = apply_loudnorm(merged, work_dir / "normalized.wav", plan["target_lufs"])

    if spec.is_video:
        mux_video(input_path, normalized, args.out)
    else:
        args.out.write_bytes(normalized.read_bytes())

    export_asr_wav(normalized, work_dir / "restored-16k.wav")

    build_ab_preview(input_path, normalized, pick_preview_start(plan),
                     work_dir / "preview-ab.wav", PREVIEW_SECONDS)

    after = collect_metrics(normalized, plan, report_path=report_path)
    result = verify(before, after, plan["target_lufs"])
    (work_dir / "verify.json").write_text(
        json.dumps({"before": before, "after": after, "result": asdict(result)},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    _print_result(before, after, result, args.out, work_dir)
    if not result.passed:
        raise SystemExit(1)


def _print_result(before: dict, after: dict, result, out_path: Path, work_dir: Path) -> None:
    """印出前後指標對照與驗證結論。"""
    print(f"\n輸出：{out_path}")
    print(f"AB 試聽：{work_dir / 'preview-ab.wav'}")
    print(f"ASR 用 WAV：{work_dir / 'restored-16k.wav'}\n")
    print("修復前後指標：")
    for key, label in (("noise_lufs", "底噪 LUFS"), ("integrated_lufs", "整體 LUFS"),
                       ("true_peak", "真峰值 dBTP"), ("utterance_lufs_stdev", "句間標準差")):
        print(f"  {label}: {before[key]:.2f} → {after[key]:.2f}")
    print("\n驗證結果：")
    for check in result.checks:
        print(f"  [{'通過' if check.passed else '失敗'}] {check.name}：{check.detail}")
    if not result.passed:
        print("\n驗證未通過。請依上列失效環節調整 plan.json 後重跑。")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: 執行測試確認通過**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/ -v
```

Expected: PASS（全部通過）

- [ ] **Step 6: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入修復 CLI 與計畫閘門"
```

---

## Task 13: 批次模式、SKILL.md 與部署腳本

**Files:**
- Create: `skills-src/audio-restoration/scripts/batch.py`
- Create: `skills-src/audio-restoration/SKILL.md`
- Create: `skills-src/audio-restoration/deploy.ps1`
- Create: `skills-src/audio-restoration/references/rescue-ai.md`
- Test: `skills-src/audio-restoration/tests/test_batch.py`

**Interfaces:**
- Consumes: `analyze.py`、`restore.py`
- Produces：`batch.py` CLI：`--input-dir`、`--out-dir`、`--target`、`--restore`

`--restore` 不繞過閘門：它只處理**已存在 `plan.json`** 的子目錄，也就是已經體檢過的檔案。沒有 plan 的檔案會被跳過並列在總表中，不會被自動處理。

- [ ] **Step 1: 寫失敗測試**

`tests/test_batch.py`：

```python
"""batch 模組測試：批次體檢與總表產出。"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def test_batch_analyzes_every_media_file(synth_wav: Path, tmp_path: Path):
    """目錄下兩支音檔都應各自產出 report.json，並匯總成一份總表。"""
    media_dir = tmp_path / "media"
    media_dir.mkdir()
    shutil.copy(synth_wav, media_dir / "a.wav")
    shutil.copy(synth_wav, media_dir / "b.wav")
    for name in ("a", "b"):
        work = tmp_path / "out" / name
        work.mkdir(parents=True)
        payload = {
            "info": {"language": "zh", "duration": 8.0, "model": "small"},
            "segments": [
                {"id": 1, "start": 2.0, "end": 4.0, "text": "A", "no_speech_prob": 0.0,
                 "words": [{"start": 2.0, "end": 4.0, "word": "A", "probability": 0.9}]},
                {"id": 2, "start": 5.0, "end": 7.0, "text": "B", "no_speech_prob": 0.0,
                 "words": [{"start": 5.0, "end": 7.0, "word": "B", "probability": 0.9}]},
            ],
        }
        (work / "timeline.json").write_text(json.dumps(payload), encoding="utf-8")

    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "batch.py"),
         "--input-dir", str(media_dir), "--out-dir", str(tmp_path / "out")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    assert (tmp_path / "out" / "a" / "report.json").exists()
    assert (tmp_path / "out" / "b" / "report.json").exists()
    summary = json.loads((tmp_path / "out" / "summary.json").read_text(encoding="utf-8"))
    assert len(summary["files"]) == 2


def test_batch_skips_non_media_files(synth_wav: Path, tmp_path: Path):
    """非音影格式（.txt）不得被當成素材處理。"""
    media_dir = tmp_path / "media"
    media_dir.mkdir()
    shutil.copy(synth_wav, media_dir / "a.wav")
    (media_dir / "note.txt").write_text("不是音檔", encoding="utf-8")
    work = tmp_path / "out" / "a"
    work.mkdir(parents=True)
    payload = {
        "info": {"language": "zh", "duration": 8.0, "model": "small"},
        "segments": [
            {"id": 1, "start": 2.0, "end": 4.0, "text": "A", "no_speech_prob": 0.0,
             "words": [{"start": 2.0, "end": 4.0, "word": "A", "probability": 0.9}]},
            {"id": 2, "start": 5.0, "end": 7.0, "text": "B", "no_speech_prob": 0.0,
             "words": [{"start": 5.0, "end": 7.0, "word": "B", "probability": 0.9}]},
        ],
    }
    (work / "timeline.json").write_text(json.dumps(payload), encoding="utf-8")

    subprocess.run(
        [sys.executable, str(SCRIPTS / "batch.py"),
         "--input-dir", str(media_dir), "--out-dir", str(tmp_path / "out")],
        capture_output=True, text=True, check=True,
    )
    summary = json.loads((tmp_path / "out" / "summary.json").read_text(encoding="utf-8"))
    assert len(summary["files"]) == 1


def test_batch_restore_skips_files_without_plan(synth_wav: Path, tmp_path: Path):
    """--restore 只處理已有 plan.json 的子目錄，沒體檢過的必須跳過並記錄。

    這是體檢閘門在批次模式下的延伸：批次不得成為繞過人工確認的後門。
    """
    import shutil
    media_dir = tmp_path / "media"
    media_dir.mkdir()
    shutil.copy(synth_wav, media_dir / "a.wav")
    (tmp_path / "out" / "a").mkdir(parents=True)  # 有目錄但沒有 plan.json

    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "batch.py"), "--restore",
         "--input-dir", str(media_dir), "--out-dir", str(tmp_path / "out")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    summary = json.loads(
        (tmp_path / "out" / "restore-summary.json").read_text(encoding="utf-8")
    )
    assert summary["files"][0]["skipped"] is True
    assert "plan" in summary["files"][0]["reason"]
```

- [ ] **Step 2: 執行測試確認失敗**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/test_batch.py -v
```

Expected: FAIL — batch.py 不存在

- [ ] **Step 3: 實作 batch.py**

```python
"""批次體檢：對整個目錄的音影檔各自跑一次 analyze，並匯總成總表。

只做體檢不做修復 —— 修復仍須逐檔確認 plan.json，這是刻意的設計。
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

MEDIA_SUFFIXES = {".wav", ".mp3", ".m4a", ".flac", ".aac", ".mp4", ".mov", ".mkv"}


def main() -> None:
    """解析參數並批次執行體檢。"""
    parser = argparse.ArgumentParser(description="批次音質體檢")
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--target", type=float, default=-16.0)
    parser.add_argument("--restore", action="store_true",
                        help="對已有 plan.json 的檔案執行修復並匯總驗證結果")
    args = parser.parse_args()

    analyze = Path(__file__).resolve().parent / "analyze.py"
    media_files = sorted(
        p for p in args.input_dir.iterdir()
        if p.is_file() and p.suffix.lower() in MEDIA_SUFFIXES
    )
    if not media_files:
        raise SystemExit(f"{args.input_dir} 下沒有可處理的音影檔")

    if args.restore:
        _batch_restore(media_files, args.out_dir)
        return

    entries = []
    for media in media_files:
        work_dir = args.out_dir / media.stem
        print(f"\n=== 體檢 {media.name} ===")
        result = subprocess.run(
            [sys.executable, str(analyze), "--input", str(media),
             "--work-dir", str(work_dir), "--target", str(args.target)],
            text=True,
        )
        entries.append(_summarize(media, work_dir, result.returncode))

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "summary.json").write_text(
        json.dumps({"target_lufs": args.target, "files": entries},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    _print_summary(entries)


def _summarize(media: Path, work_dir: Path, returncode: int) -> dict:
    """摘要單一檔案的體檢結果。"""
    report_path = work_dir / "report.json"
    if returncode != 0 or not report_path.exists():
        return {"file": media.name, "ok": False, "reason": "體檢失敗"}
    report = json.loads(report_path.read_text(encoding="utf-8"))
    zones = report["zones"]
    return {
        "file": media.name,
        "ok": True,
        "duration": report["media"]["duration"],
        "zones": len(zones),
        "utterances": report["counts"]["utterances"],
        "min_snr_db": min(z["snr_db"] for z in zones),
        "needs_ai_rescue": any(z["needs_ai_rescue"] for z in zones),
        "issues": [issue for z in zones for issue in z["issues"]],
    }


def _batch_restore(media_files: list[Path], out_dir: Path) -> None:
    """對已體檢過（有 plan.json）的檔案執行修復，並匯總前後指標。

    沒有 plan.json 的檔案一律跳過 —— 批次不得成為繞過人工確認的後門。
    """
    restore = Path(__file__).resolve().parent / "restore.py"
    entries: list[dict] = []
    for media in media_files:
        work_dir = out_dir / media.stem
        plan_path = work_dir / "plan.json"
        if not plan_path.exists():
            entries.append({"file": media.name, "skipped": True,
                            "reason": "沒有 plan.json，尚未體檢或尚未確認"})
            print(f"跳過 {media.name}：沒有 plan.json")
            continue
        out_path = work_dir / f"{media.stem}-restored{media.suffix}"
        print(f"\n=== 修復 {media.name} ===")
        result = subprocess.run(
            [sys.executable, str(restore), "--plan", str(plan_path),
             "--out", str(out_path), "--work-dir", str(work_dir / "work")],
            text=True,
        )
        entries.append(_summarize_restore(media, work_dir, result.returncode))

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "restore-summary.json").write_text(
        json.dumps({"files": entries}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    _print_restore_summary(entries)


def _summarize_restore(media: Path, work_dir: Path, returncode: int) -> dict:
    """摘要單一檔案的修復前後指標。"""
    verify_path = work_dir / "work" / "verify.json"
    if not verify_path.exists():
        return {"file": media.name, "skipped": False, "ok": False,
                "reason": "修復失敗，未產出 verify.json"}
    payload = json.loads(verify_path.read_text(encoding="utf-8"))
    return {
        "file": media.name,
        "skipped": False,
        "ok": returncode == 0 and payload["result"]["passed"],
        "before": payload["before"],
        "after": payload["after"],
        "failed_checks": [
            c["name"] for c in payload["result"]["checks"] if not c["passed"]
        ],
    }


def _print_restore_summary(entries: list[dict]) -> None:
    """印出修復前後指標總表。"""
    print("\n=== 批次修復總表 ===")
    for entry in entries:
        if entry.get("skipped"):
            print(f"  {entry['file']}: 跳過（{entry['reason']}）")
            continue
        if not entry["ok"]:
            failed = "、".join(entry.get("failed_checks", [])) or entry.get("reason", "")
            print(f"  {entry['file']}: 未通過（{failed}）")
            continue
        before, after = entry["before"], entry["after"]
        print(f"  {entry['file']}: 底噪 {before['noise_lufs']:.1f} → "
              f"{after['noise_lufs']:.1f} LUFS／整體 {before['integrated_lufs']:.1f} → "
              f"{after['integrated_lufs']:.1f}／句間標準差 "
              f"{before['utterance_lufs_stdev']:.2f} → {after['utterance_lufs_stdev']:.2f}")


def _print_summary(entries: list[dict]) -> None:
    """印出體檢總表。"""
    print("\n=== 批次體檢總表 ===")
    for entry in entries:
        if not entry["ok"]:
            print(f"  {entry['file']}: {entry['reason']}")
            continue
        flag = "⚠ 需 AI 救援" if entry["needs_ai_rescue"] else ""
        print(f"  {entry['file']}: {entry['zones']} 區／"
              f"最差 SNR {entry['min_snr_db']:.1f} dB {flag}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 撰寫 SKILL.md**

```markdown
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
```

- [ ] **Step 5: 撰寫 references/rescue-ai.md**

```markdown
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
3. 把處理結果放回 `plan.json` 對應 zone 的位置，將該 zone 的 `denoise_db` 設為 0（避免二次降噪）
4. 重跑 `restore.py`，其餘流程不變

## 判斷是否值得

先聽 `preview-ab.wav`。若 ffmpeg 路徑的結果已經可接受，就不要動 AI —— 音色改變是不可逆的代價。
```

- [ ] **Step 6: 撰寫 deploy.ps1**

```powershell
# 將技能從專案原始碼部署到全域技能目錄。
# ~/.claude 不是 git repo，故原始碼在專案內維護，部署只是複製。
param(
    [string]$Target = "$HOME\.claude\skills\audio-restoration"
)

$ErrorActionPreference = "Stop"
$source = $PSScriptRoot

# 只部署執行時需要的檔案，測試與開發用檔案不進全域目錄
$include = @("SKILL.md", "scripts", "references")

if (Test-Path $Target) {
    Remove-Item -Recurse -Force $Target
}
New-Item -ItemType Directory -Force -Path $Target | Out-Null

foreach ($item in $include) {
    $sourcePath = Join-Path $source $item
    if (Test-Path $sourcePath) {
        Copy-Item -Recurse -Force $sourcePath (Join-Path $Target $item)
    }
}

# 清掉複製過來的 __pycache__
Get-ChildItem -Path $Target -Recurse -Directory -Filter "__pycache__" |
    Remove-Item -Recurse -Force

Write-Host "已部署至 $Target"
```

- [ ] **Step 7: 執行測試並部署**

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/ -v
.\deploy.ps1
```

Expected: 全部測試 PASS，且 `~/.claude/skills/audio-restoration/SKILL.md` 存在

- [ ] **Step 8: Commit**

```powershell
git add skills-src/audio-restoration
git commit -m "feat(audio-restoration): 加入批次模式、SKILL.md 與部署腳本"
```

---

## Task 14: 真實素材門檻校準

**Files:**
- Modify: `skills-src/audio-restoration/scripts/ar/fingerprint.py`（`ZONE_THRESHOLD` 校準值）
- Modify: `skills-src/audio-restoration/scripts/ar/diagnose.py`（`REVERB_SLOPE_THRESHOLD` 校準值）
- Create: `skills-src/audio-restoration/references/filter-cookbook.md`

**Interfaces:**
- Consumes: 全部既有模組（本任務只調整常數與撰寫文件，不改動任何介面）
- Produces: 無新介面。`ZONE_THRESHOLD` 與 `REVERB_SLOPE_THRESHOLD` 由設計初值改為實測值。

**背景：** 兩個門檻在設計階段只能給初值，因為它們依賴麥克風型號、房間聲學與取樣率。合成音檔測不出真實分布，必須用真實講課素材校準。

- [ ] **Step 1: 對真實素材跑體檢**

素材選 `幻燈片8.mp4`：已知為 1273 秒真人講解錄音，且 `subtitles/幻燈片8/timeline.json` 已存在（3064 個詞級時間戳），可直接沿用不必重跑 ASR。

```powershell
Copy-Item "subtitles\幻燈片8	imeline.json" `
  "audio-restore\幻燈片8	imeline.json" -Force

& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "skills-srcudio-restoration\scriptsnalyze.py" `
  --input "D:\GitHub\hahow-ai-full-stack\幻燈片8.mp4" `
  --work-dir "D:\GitHub\hahow-ai-full-stackudio-restore\幻燈片8"
```

若 `videos/` 下另有真人素材，一併跑批次體檢取得更多樣本：

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "skills-srcudio-restoration\scriptsatch.py" `
  --input-dir "D:\GitHub\hahow-ai-full-stackideos" `
  --out-dir "D:\GitHub\hahow-ai-full-stackudio-restore"
```

- [ ] **Step 2: 校準 ZONE_THRESHOLD**

從 `report.json` 讀出分區結果，判斷是否合理：

- 一支錄音條件一致的講解，應該只得到 **1 個 zone**。若切出多個 zone 但實際聽起來條件相同 → `ZONE_THRESHOLD`（初值 0.15）過低，往上調。
- 若明知中途換過設備或場地卻只有 1 個 zone → 往下調。

用這段指令列出實際的相鄰指紋距離分布，據此挑門檻（取「同條件距離的最大值」與「換條件距離的最小值」之間）：

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" -c @'
import json, sys
sys.path.insert(0, "skills-src/audio-restoration/scripts")
from pathlib import Path
from ar.fingerprint import cosine_distance, read_samples, spectral_fingerprint
report = json.loads(Path("audio-restore/幻燈片8/report.json").read_text(encoding="utf-8"))
windows = report["noise_windows"]
media = Path(report["input"])
fps = [spectral_fingerprint(read_samples(media, w["start"], w["end"]), 16000) for w in windows]
dists = [cosine_distance(a, b) for a, b in zip(fps, fps[1:])]
dists_sorted = sorted(dists)
print(f"採樣窗 {len(windows)} 個，相鄰距離 {len(dists)} 筆")
print(f"最小 {dists_sorted[0]:.4f}／中位 {dists_sorted[len(dists_sorted)//2]:.4f}／最大 {dists_sorted[-1]:.4f}")
print("前十大：", [f"{d:.4f}" for d in dists_sorted[-10:]])
'@
```

把選定值寫回 `fingerprint.py` 的 `ZONE_THRESHOLD`。

- [ ] **Step 3: 校準 REVERB_SLOPE_THRESHOLD**

從同一份 `report.json` 讀出各 zone 的 `reverb_slope` 實測值（該值是 zone 內各語句結束點的斜率中位數）。若該素材聽起來殘響正常卻被標記為「殘響重」，代表 `-60.0` 這個門檻過於寬鬆，須往下調（更負）。反之若明顯有回音卻沒被標記，則往上調。

換算參考：本指標採 T20 量測，斜率精確等於 -60 / RT60。−120 dB/s ≈ RT60 0.5 秒（吸音良好）、−60 dB/s ≈ RT60 1 秒、−30 dB/s ≈ RT60 2 秒（明顯回音）。語句結束後 20dB 內就跌到底噪的乾淨錄音會算出數百至上千 dB/s。

注意 `reverb_slope` 為 `0.0` 代表「無法判斷」而非「衰減極慢」，統計時須排除。

- [ ] **Step 4: 重跑測試確認校準未破壞既有行為**

```powershell
cd skills-srcudio-restoration
& "$HOME\.audio-restoration\.venv\Scripts\python" -m pytest tests/ -v
```

Expected: PASS（全部通過）。若 `test_detect_zones_splits_at_noise_change` 因新門檻而失敗，代表門檻調得過高，須重新檢視 Step 2 的判斷。

- [ ] **Step 5: 完成端到端驗收**

檢視 `plan.json`，確認無誤後執行修復：

```powershell
& "$HOME\.audio-restoration\.venv\Scripts\python" `
  "skills-srcudio-restoration\scripts
estore.py" `
  --plan "D:\GitHub\hahow-ai-full-stackudio-restore\幻燈片8\plan.json" `
  --out "D:\GitHub\hahow-ai-full-stackudio-restore\幻燈片8\幻燈片8-restored.mp4"
```

四項驗證須全數通過，並實際聽 `preview-ab.wav` 確認無水聲、金屬感或呼吸感。

- [ ] **Step 6: 撰寫 references/filter-cookbook.md**

內容須包含：

- 各 ffmpeg 濾鏡的參數意義與本技能的取值理由：`afftdn` 的 `nr`/`nf`/`tn`、`deesser` 的 `i`/`m`/`f`、`highpass` 的 `f=80`、`loudnorm` 兩段式的必要性、`alimiter` 的 `limit` 由 dBTP 換算為線性值的公式
- 為何禁用 `acompressor` / `dynaudnorm` / `speechnorm`
- **本次校準紀錄**：素材名稱、時長、實測的指紋距離分布（最小／中位／最大／前十大）、實測的殘響斜率、最終採用的門檻值與判斷理由
- 端到端驗收的四項指標實測值

- [ ] **Step 7: 重新部署並提交**

```powershell
cd skills-srcudio-restoration
.\deploy.ps1
cd ..\..
git add skills-src/audio-restoration docs/superpowers audio-restore
git commit -m "chore(audio-restoration): 以真實素材校準分區與殘響門檻"
```

---


## 驗收標準

全部任務完成後，下列條件須同時成立：

1. `pytest tests/ -v` 全數通過
2. 對 `幻燈片8.mp4` 跑完 analyze → 檢視 plan → restore，四項驗證全數通過
3. `preview-ab.wav` 經人耳確認修復後聽感無異常（無水聲、無金屬感、無呼吸感）
4. `~/.claude/skills/audio-restoration/SKILL.md` 存在且可被技能系統載入
5. `references/filter-cookbook.md` 記錄了實測校準值，非預設值
