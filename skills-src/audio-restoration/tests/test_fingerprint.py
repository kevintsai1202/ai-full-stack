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


def _windows(count: int, spacing: float = 10.0) -> list[Interval]:
    """產生等距的噪音採樣窗，每個長 1 秒。"""
    return [Interval(i * spacing, i * spacing + 1.0) for i in range(count)]


def _fps(seeds_white: int, seeds_low: int) -> list[np.ndarray]:
    """前段白雜訊、後段低頻雜訊，模擬中途換了錄音條件。"""
    white = [spectral_fingerprint(_white_noise(s), SR) for s in range(1, seeds_white + 1)]
    low = [spectral_fingerprint(_low_passed_noise(s), SR)
           for s in range(100, 100 + seeds_low)]
    return white + low


def test_detect_zones_splits_at_sustained_change():
    """前段白雜訊、後段低頻雜訊持續不同，應切成兩個 zone。"""
    fps = _fps(4, 4)
    windows = _windows(8)
    zones = detect_zones(fps, windows, total_duration=90.0,
                         trend_windows=2, min_zone_seconds=5.0)
    assert len(zones) == 2
    assert zones[0].start == 0.0
    assert abs(zones[0].end - zones[1].start) < 1e-6
    assert abs(zones[1].end - 90.0) < 1e-6


def test_zone_cut_lands_inside_a_noise_window():
    """切點必須落在某個噪音窗內部，不得落在兩窗之間的語音區。

    兩窗之間全是人聲；zone 交界的區級增益是硬切，切在語音中段會產生
    可聽的喀聲。切在噪音窗內則跳變發生在無人聲處。
    """
    zones = detect_zones(_fps(4, 4), _windows(8), total_duration=90.0,
                         trend_windows=2, min_zone_seconds=5.0)
    cut = zones[0].end
    assert any(w.start <= cut <= w.end for w in _windows(8)), f"切點 {cut} 落在語音區"


def test_detect_zones_returns_single_zone_when_uniform():
    """底噪一致時只應有一個 zone，不得無故切割。"""
    fps = [spectral_fingerprint(_white_noise(s), SR) for s in range(1, 9)]
    zones = detect_zones(fps, _windows(8), total_duration=90.0,
                         trend_windows=2, min_zone_seconds=5.0)
    assert len(zones) == 1
    assert zones[0].start == 0.0
    assert abs(zones[0].end - 90.0) < 1e-6


def test_isolated_outlier_window_does_not_split():
    """單一異常窗不得造成切點 —— 這是相鄰比較最致命的假陽性來源。

    真實素材上，底噪頻譜隨時間漂移（冷氣起停、風扇轉速、螢幕錄影裡
    播放的內容）會讓相鄰距離劇烈震盪。1273 秒的單一場地錄音因此被切成
    46 個 zone。趨勢比較要求變化在前後各數個窗都持續存在，單點異常
    不足以構成證據。
    """
    fps = [spectral_fingerprint(_white_noise(s), SR) for s in range(1, 9)]
    fps[4] = spectral_fingerprint(_low_passed_noise(7), SR)  # 只有一個窗不同
    zones = detect_zones(fps, _windows(8), total_duration=90.0,
                         trend_windows=2, min_zone_seconds=5.0)
    assert len(zones) == 1, f"單一異常窗不該切出 {len(zones)} 個 zone"


def test_min_zone_seconds_suppresses_dense_cuts():
    """最小 zone 長度應濾掉密集切點 —— 換麥克風不會在幾分鐘內來回發生。"""
    fps = ([spectral_fingerprint(_white_noise(s), SR) for s in range(1, 5)]
           + [spectral_fingerprint(_low_passed_noise(s), SR) for s in range(100, 104)]
           + [spectral_fingerprint(_white_noise(s), SR) for s in range(20, 24)])
    windows = _windows(12)
    loose = detect_zones(fps, windows, total_duration=130.0,
                         trend_windows=2, min_zone_seconds=5.0)
    tight = detect_zones(fps, windows, total_duration=130.0,
                         trend_windows=2, min_zone_seconds=100.0)
    assert len(tight) < len(loose)


def test_too_few_windows_returns_single_zone():
    """窗數不足以做前後比較時不分區 —— 硬切一刀的風險大於少分一區。"""
    fps = [spectral_fingerprint(_white_noise(s), SR) for s in (1, 2, 3)]
    zones = detect_zones(fps, _windows(3), total_duration=30.0, trend_windows=5)
    assert len(zones) == 1
    assert zones[0].noise_window_indices == [0, 1, 2]


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
