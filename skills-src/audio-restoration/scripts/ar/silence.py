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
