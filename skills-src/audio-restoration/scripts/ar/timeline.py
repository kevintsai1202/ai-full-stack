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
