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
