"""diagnose 模組測試：各項診斷指標的數值行為。"""
import numpy as np

from ar.bulk import AudioBuffer
from ar.diagnose import (band_energy_ratio, clipped_ratio, diagnose_zone,
                         pick_denoise_level, reverb_slope, zone_reverb_slope)
from ar.fingerprint import Zone

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


def _make_buffer(samples: np.ndarray) -> AudioBuffer:
    """把合成樣本包成 16kHz 的 AudioBuffer（診斷路徑的契約輸入）。"""
    samples = samples.astype(np.float32)
    return AudioBuffer(samples=samples, sample_rate=SR,
                       duration=samples.size / SR)


def _speech_then_silence_buffer() -> AudioBuffer:
    """2 秒素材：前 1 秒 220Hz 人聲、於 1.0s 乾淨結束，其後僅底噪。"""
    rng = np.random.default_rng(11)
    voice = _tone(220.0, 1.0, amplitude=0.5)
    floor = 0.002 * rng.standard_normal(SR)
    return _make_buffer(np.concatenate([voice, floor]))


# 殘響量測的語句結束時刻：取 0.98 而非 1.0——尾窗必須從「還有人聲」的
# 時刻起算才量得到衰減；恰從 1.0 起算的窗只剩底噪平台，量不到下降段。
# 真實流程中 ASR 的詞級結束時間戳本來就落在聲音實際消失之前。
UTTERANCE_END = 0.98


def test_diagnose_zone_reads_from_buffer():
    """diagnose_zone 收 AudioBuffer：不碰檔案系統即可完成整個 zone 的診斷。

    220Hz 純音不在齒音／隆隆頻帶，SNR 34dB 對應降噪 12dB。
    """
    buf = _speech_then_silence_buffer()
    zone = Zone(index=0, start=0.0, end=2.0)
    diagnosis = diagnose_zone(buf, zone, noise_rms_db=-54.0,
                              speech_rms_db=-20.0, utterance_ends=[UTTERANCE_END])
    assert abs(diagnosis.snr_db - 34.0) < 1e-6
    assert diagnosis.denoise_db == 12
    assert not diagnosis.needs_deesser
    assert not diagnosis.needs_highpass
    assert not diagnosis.needs_ai_rescue
    # 乾淨結束的衰減極陡，不得被判為殘響重
    assert diagnosis.reverb_slope is not None
    assert diagnosis.reverb_slope < -500.0


def test_zone_reverb_slope_skips_windows_beyond_zone_end():
    """尾窗超出 zone 邊界的結束點必須跳過，避免量到下一區的聲學條件。

    唯一的結束點在 zone 邊界外 → 無可用量測，回傳 0.0（無法判斷）。
    """
    buf = _speech_then_silence_buffer()
    zone = Zone(index=0, start=0.0, end=1.1)  # 0.98 + 0.3 秒窗超出 1.1
    assert zone_reverb_slope(buf, zone, utterance_ends=[UTTERANCE_END]) == 0.0
