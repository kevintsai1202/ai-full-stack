"""底噪頻譜指紋與錄音條件分區偵測。

分區依據是底噪的頻譜形狀而非音量：換麥克風時音量可能不變，但噪音頻譜必變。
指紋正規化為機率分布後取餘弦距離，因此對整體音量完全不敏感。
"""
import math
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from .ffmpeg_io import FFmpegError, require_tool
from .silence import Interval

# 趨勢比較的餘弦距離門檻。真實素材實測：門檻在 0.30-0.70 之間給出幾乎
# 相同的切點，代表這個設計對門檻不敏感——穩健的訊號。取中間值 0.40。
ZONE_THRESHOLD = 0.40
ZONE_TREND_WINDOWS = 5      # 變化點前後各取幾個噪音窗做趨勢比較
MIN_ZONE_SECONDS = 120.0    # 最小 zone 長度（秒）
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
    """產生 1/3 八度頻帶邊界，上限為 Nyquist。

    當取樣率過低（Nyquist 頻率不超過最低頻帶起點 BAND_START_HZ）時，
    無法產生任何有效頻帶，明確報錯而非讓後續計算靜默退化成單一元素陣列。
    """
    nyquist = sample_rate / 2.0
    if BAND_START_HZ >= nyquist:
        raise ValueError(
            f"取樣率 {sample_rate}Hz 過低：Nyquist 頻率 {nyquist:.1f}Hz "
            f"未超過最低頻帶起點 {BAND_START_HZ:.1f}Hz，無法產生任何頻帶"
        )
    edges = [BAND_START_HZ]
    while edges[-1] * BAND_RATIO < nyquist:
        edges.append(edges[-1] * BAND_RATIO)
    edges.append(nyquist)
    return np.array(edges)


def _min_samples_for_lowest_band(sample_rate: int) -> int:
    """計算「保證最低頻帶至少有一個 FFT bin 落入」所需的最小樣本數。

    FFT 的頻率解析度（相鄰 bin 間距）為 sample_rate / N。只要這個間距不超過
    最低頻帶的寬度，依鴿籠原理，該頻帶區間內必定至少涵蓋一個 bin
    （對任一寬度為 L 的半開區間，只要 bin 間距 s <= L，區間內必存在 bin 的
    整數倍位置，證明見任務審查紀錄）。因此所需最小樣本數為
    N >= sample_rate / 頻帶寬度，取上界即為門檻。
    這個門檻依 sample_rate、BAND_START_HZ、BAND_RATIO 動態算出，
    不是與取樣率無關的魔術數字。
    """
    edges = _band_edges(sample_rate)
    band_width = float(edges[1] - edges[0])  # 最低頻帶的寬度（Hz）
    return math.ceil(sample_rate / band_width)


def spectral_fingerprint(samples: np.ndarray, sample_rate: int) -> np.ndarray:
    """計算頻譜指紋：功率譜依 1/3 八度聚合後正規化為機率分布。

    正規化是為了讓指紋滿足「機率分布」的介面契約（元素非負、總和為 1，
    見 test_fingerprint_is_normalized_distribution），而不是指紋對音量不
    敏感的原因——後者其實來自後續比較用的餘弦距離本身（對向量長度不敏感）：
    即使不做正規化，直接對功率譜取餘弦距離一樣不受音量影響。
    """
    min_samples = _min_samples_for_lowest_band(sample_rate)  # 保證最低頻帶至少有一個 bin 落入的門檻
    if samples.size < min_samples:
        raise ValueError(
            f"樣本太短，無法計算頻譜指紋：於取樣率 {sample_rate}Hz 下，"
            f"至少需要 {min_samples} 個樣本才能保證最低頻帶"
            f"（{BAND_START_HZ:.1f}Hz 起）至少涵蓋一個 FFT bin，"
            f"實際只有 {samples.size} 個"
        )
    windowed = samples.astype(np.float64) * np.hanning(samples.size)
    power = np.abs(np.fft.rfft(windowed)) ** 2
    freqs = np.fft.rfftfreq(samples.size, d=1.0 / sample_rate)
    edges = _band_edges(sample_rate)
    last_band_index = len(edges) - 2  # 最後一個頻帶的索引
    bands = np.zeros(len(edges) - 1)
    for index in range(len(edges) - 1):
        if index == last_band_index:
            # 最後一個頻帶上界改用「含 Nyquist」的閉區間，
            # 避免偶數長度樣本時恰為 Nyquist 的那個 bin 落在所有頻帶之外而遺失能量。
            mask = (freqs >= edges[index]) & (freqs <= edges[index + 1])
        else:
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


def _group_fingerprint(fingerprints: list[np.ndarray], indices) -> np.ndarray:
    """一組噪音窗的代表指紋：各頻帶取中位數後重新正規化。

    取中位數而非平均：停頓裡常混有呼吸、鍵盤、翻頁、殘響尾音等瞬態事件，
    平均會被它們拉走，中位數取的是「這段時間最常出現的頻譜形狀」。
    """
    stacked = np.array([fingerprints[i] for i in indices])
    median = np.median(stacked, axis=0)
    total = median.sum()
    return median / total if total > 0 else median


def detect_zones(fingerprints: list[np.ndarray], windows: list[Interval],
                 total_duration: float, threshold: float = ZONE_THRESHOLD,
                 trend_windows: int = ZONE_TREND_WINDOWS,
                 min_zone_seconds: float = MIN_ZONE_SECONDS) -> list[Zone]:
    """偵測錄音條件變化點並切分 zone。

    **不比較相鄰的兩個噪音窗**。真實素材實測揭露：底噪的頻譜形狀本來就
    會隨時間漂移（冷氣起停、風扇轉速、室外聲、螢幕錄影裡播放的內容），
    那不代表錄音條件改變。一支單一講者、單一場地的 1273 秒錄音，用相鄰
    比較切出了 46 個 zone，相鄰距離在 0.001 與 0.95 之間劇烈鋸齒震盪，
    找不到任何能分開「同條件」與「換條件」的門檻。

    改為看**趨勢的持續性**：對每個候選位置，比較前 trend_windows 個窗與
    後 trend_windows 個窗的中位指紋。真正的設備／場地變更會讓兩側持續
    不同；隨機漂移不會。再加上最小 zone 長度約束 —— 換麥克風不會在兩
    分鐘內來回發生。同一支素材用這個方法收斂到 5 個 zone，且門檻在
    0.30-0.70 之間給出幾乎相同的切點。

    切點取噪音窗的中點，讓 zone 邊界必定落在確定無人聲的區間內 ——
    區級增益在 zone 交界是硬切（斜坡只作用在 zone 內的語句交界），
    切在語音中段會是一聲清楚可聽的喀聲。
    """
    # 窗數不足以做前後比較時不分區。硬切一刀的風險遠大於少分一區。
    if len(fingerprints) < 2 * trend_windows + 1:
        return [Zone(index=0, start=0.0, end=total_duration,
                     noise_window_indices=list(range(len(windows))))]

    # 每個候選位置的「前後兩組中位指紋」距離
    candidates: list[tuple[float, float]] = []
    for center in range(trend_windows, len(fingerprints) - trend_windows):
        before = _group_fingerprint(fingerprints, range(center - trend_windows, center))
        after = _group_fingerprint(fingerprints, range(center, center + trend_windows))
        distance = cosine_distance(before, after)
        if distance > threshold:
            cut = (windows[center].start + windows[center].end) / 2.0
            candidates.append((cut, distance))

    # 依距離由大到小貪婪選點，強制彼此間隔至少 min_zone_seconds。
    # 距離最大者優先，確保保留的是證據最強的變化點。
    cut_points: list[float] = []
    for cut, _ in sorted(candidates, key=lambda item: -item[1]):
        if all(abs(cut - chosen) >= min_zone_seconds for chosen in cut_points):
            cut_points.append(cut)
    cut_points.sort()

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
