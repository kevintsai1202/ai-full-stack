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


def test_spectral_fingerprint_accepts_minimum_required_samples():
    """樣本數剛好等於守門門檻時應成功計算（不誤殺剛好足夠的樣本）。"""
    from ar.fingerprint import _min_samples_for_lowest_band

    min_samples = _min_samples_for_lowest_band(SR)
    samples = _white_noise(1)[:min_samples]
    fp = spectral_fingerprint(samples, SR)
    assert fp.ndim == 1
    assert abs(float(fp.sum()) - 1.0) < 1e-6


def test_spectral_fingerprint_rejects_samples_below_minimum():
    """樣本數低於守門門檻時應明確報錯，且錯誤訊息需包含所需的樣本數。"""
    from ar.fingerprint import _min_samples_for_lowest_band

    min_samples = _min_samples_for_lowest_band(SR)
    samples = _white_noise(1)[: min_samples - 1]
    try:
        spectral_fingerprint(samples, SR)
        assert False, "應拋出 ValueError"
    except ValueError as error:
        assert str(min_samples) in str(error)
