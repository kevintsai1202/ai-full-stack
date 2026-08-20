"""底噪頻譜指紋與錄音條件分區偵測。

分區依據是底噪的頻譜形狀而非音量：換麥克風時音量可能不變，但噪音頻譜必變。
指紋正規化為機率分布後取餘弦距離，因此對整體音量完全不敏感。
"""
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from .ffmpeg_io import FFmpegError, require_tool
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
        require_tool("ffmpeg"), "-hide_banner", "-nostdin", "-v", "error",
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
    for index in range(len(fingerprints) - 1):
        if cosine_distance(fingerprints[index], fingerprints[index + 1]) > threshold:
            midpoint = (windows[index].end + windows[index + 1].start) / 2.0
            cut_points.append(midpoint)

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
