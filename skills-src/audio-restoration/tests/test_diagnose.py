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
