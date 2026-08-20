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
    """一個 zone 的完整診斷結果與處理決策。

    本物件會被序列化進 report.json 與 plan.json 供人工檢視與調整，
    因此每個欄位的語意都必須能獨立看懂，不能倚賴閱讀程式碼。
    """
    zone_index: int          # 該區在整支檔案中的序號（從 0 起）
    start: float             # 該區起始時間（秒）
    end: float                # 該區結束時間（秒）
    noise_lufs: float        # 該區底噪響度
    speech_lufs: float       # 該區人聲響度
    snr_db: float            # 訊噪比
    sibilance_ratio: float   # 5-8kHz 能量佔比
    rumble_ratio: float      # <80Hz 能量佔比
    clipped_ratio: float     # 削峰樣本比例
    # 語句結束後的能量衰減斜率（dB/秒，T20 量測，等於 -60/RT60）。
    # None 代表「量不到」（該區無可用語句結束點、或尾段全在靜音地板），
    # 不是「衰減極慢」——序列化後為 JSON null，下游不得當成數值比較。
    reverb_slope: float | None
    denoise_db: int          # afftdn 降噪強度（12／18／24）
    needs_deesser: bool      # 是否掛 deesser（齒音超標）
    needs_highpass: bool     # 是否掛 highpass（低頻隆隆超標）
    needs_ai_rescue: bool    # ffmpeg 濾鏡鏈是否救不回（SNR 過低）
    issues: list[str] = field(default_factory=list)  # 給人看的問題描述


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
    # 起始位準取前三幀最大值，避免單一幀落在過零點而低估起點。
    # 時間基準必須跟著取在峰值那一幀：若峰值落在 index 1 而從 t=0 起算，
    # 分母會多算峰值前的幀數，把陡峭度低估（且低估方向是更接近 0，
    # 也就是更容易誤判為殘響重的假陽性方向）。
    peak_index = int(np.argmax(db[:3]))
    start_db = float(db[peak_index])
    step = frame / sample_rate

    for index in range(peak_index + 1, frame_count):
        if db[index] <= start_db - DECAY_TARGET_DB:
            return -DECAY_TARGET_DB / ((index - peak_index) * step)

    # 整個窗內都沒降滿 DECAY_TARGET_DB：用實際總降幅外推
    total_drop = start_db - float(db[-1])
    elapsed = (frame_count - 1 - peak_index) * step
    if total_drop <= 0.0 or elapsed <= 0.0:
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
    raw_slope = zone_reverb_slope(path, zone, utterance_ends, sample_rate)
    # 在此把內部的 0.0 哨兵轉成 None，讓「量不到」的語意能安全地跨出本模組
    slope = None if raw_slope == 0.0 else raw_slope

    issues: list[str] = []
    if clipped > CLIP_THRESHOLD:
        issues.append(f"削峰樣本比例 {clipped:.4f} 超標（修復無法還原已削掉的波形）")
    if slope is not None and slope > REVERB_SLOPE_THRESHOLD:
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
