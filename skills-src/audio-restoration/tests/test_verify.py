"""verify 模組測試：四項硬指標的通過與失敗判定。"""
from ar.verify import verify


def _before(**overrides) -> dict:
    """修復前的指標。預設 SNR 40dB（≥ CLEAN_SNR_DB），走「素材本已乾淨」
    分支，讓其餘三項測試不需要額外構造 noise_window_rms／utterance_rms。
    """
    base = {"snr_db": 40.0, "integrated_lufs": -24.0,
            "true_peak": -3.0, "utterance_rms_stdev": 6.0}
    base.update(overrides)
    return base


def _after(**overrides) -> dict:
    """修復後的指標（可覆寫單項以測試失敗情境）。"""
    base = {"snr_db": 40.0, "integrated_lufs": -16.1,
            "true_peak": -1.6, "utterance_rms_stdev": 1.8}
    base.update(overrides)
    return base


def _paired(before_windows, after_windows, before_utterances, after_utterances,
           owners) -> tuple[dict, dict]:
    """建立一組附帶配對資料（噪音窗 + 所屬句 + 句 RMS）的 before/after 指標。

    snr_db 固定給一個低於 CLEAN_SNR_DB 的值，確保走的是配對式淨壓制分支
    而非「素材本已乾淨」的捷徑。
    """
    before = {"snr_db": 15.0, "integrated_lufs": -24.0, "true_peak": -3.0,
              "utterance_rms_stdev": 6.0,
              "noise_window_rms": before_windows, "utterance_rms": before_utterances,
              "window_owner": owners}
    after = {"snr_db": 20.0, "integrated_lufs": -16.1, "true_peak": -1.6,
             "utterance_rms_stdev": 1.8,
             "noise_window_rms": after_windows, "utterance_rms": after_utterances,
             "window_owner": owners}
    return before, after


def test_all_checks_pass_for_good_result():
    """四項全數達標時應通過。"""
    result = verify(_before(), _after(), target_lufs=-16.0)
    assert result.passed is True
    assert len(result.checks) == 4


def test_denoise_not_applicable_for_clean_material():
    """SNR 已高於 CLEAN_SNR_DB 的素材，降噪淨效果視為不適用並通過。

    真實素材實測：SNR 47dB 的講課錄音，afftdn 12dB 對深達 -84dB 的底噪
    幾乎無濾除效果，配對淨壓制中位量到的只是量測殘差。強求一個不存在
    的改善是指標的錯，不是流程的錯。
    """
    result = verify(_before(snr_db=47.0), _after(), target_lufs=-16.0)
    check = next(c for c in result.checks if c.name == "降噪淨效果")
    assert check.passed is True
    assert "不適用" in check.detail


def test_pairing_removes_common_gain():
    """配對淨壓制須把共同增益完全相消，只留降噪的淨效果。

    構造「窗與所屬句增益相同、窗額外多降 12dB」的資料：每個窗與所屬句
    的共同部分（例如都 +5dB）應被相減抵銷，淨壓制應精確等於 -12。
    """
    # 兩個窗都屬於同一句（index 0），該句與窗共同增益 +5dB，
    # 窗在此之外又被 afftdn 多壓了 12dB
    before, after = _paired(
        before_windows=[-50.0, -52.0], after_windows=[-57.0, -59.0],
        before_utterances=[-20.0], after_utterances=[-15.0],
        owners=[0, 0],
    )
    result = verify(before, after, target_lufs=-16.0)
    check = next(c for c in result.checks if c.name == "降噪淨效果")
    assert check.passed is True
    assert "-12.0" in check.detail


def test_excessive_noise_reduction_fails():
    """淨壓制超過 25dB 視為降噪過頭，把人聲一併削掉了。"""
    before, after = _paired(
        before_windows=[-50.0], after_windows=[-80.0],
        before_utterances=[-20.0], after_utterances=[-20.0],
        owners=[0],
    )
    result = verify(before, after, target_lufs=-16.0)
    assert result.passed is False
    assert any("降噪過頭" in c.detail for c in result.checks if not c.passed)


def test_denoise_not_improved_fails():
    """配對淨壓制未為負（未改善或反而變差）應失敗。"""
    before, after = _paired(
        before_windows=[-50.0], after_windows=[-48.0],
        before_utterances=[-20.0], after_utterances=[-20.0],
        owners=[0],
    )
    result = verify(before, after, target_lufs=-16.0)
    assert result.passed is False
    assert any("未生效" in c.detail for c in result.checks if not c.passed)


def test_loudness_off_target_fails():
    """響度未收斂到目標 ±0.5 應失敗。"""
    result = verify(_before(), _after(integrated_lufs=-14.0), target_lufs=-16.0)
    assert result.passed is False


def test_true_peak_over_limit_fails():
    """真峰值超過 -1.5 dBTP 應失敗。"""
    result = verify(_before(), _after(true_peak=-0.5), target_lufs=-16.0)
    assert result.passed is False


def test_flattening_not_effective_fails():
    """句間標準差沒有變小代表拉平未生效。"""
    result = verify(_before(), _after(utterance_rms_stdev=6.5), target_lufs=-16.0)
    assert result.passed is False
    assert any("拉平" in c.detail for c in result.checks if not c.passed)


def test_denoise_check_reports_unverifiable_when_missing():
    """SNR 為 None 時應回報不可驗證，而不是假裝通過或直接失敗。"""
    before = {**_before(), "snr_db": None}
    after = _after(snr_db=None)
    result = verify(before, after, target_lufs=-16.0)
    check = next(c for c in result.checks if c.name == "降噪淨效果")
    assert check.passed is True
    assert "不可驗證" in check.detail


def test_single_utterance_flattening_is_not_a_failure():
    """單句音檔的標準差恆為 0，不得因此判為拉平失敗。"""
    before = {**_before(), "utterance_rms_stdev": 0.0}
    after = _after(utterance_rms_stdev=0.0)
    result = verify(before, after, target_lufs=-16.0)
    flattening = next(c for c in result.checks if c.name == "拉平生效")
    assert flattening.passed is True
    assert "不適用" in flattening.detail


def test_failed_check_names_the_stage():
    """失敗時必須指出是哪個環節，不得只回傳布林值。"""
    result = verify(_before(), _after(integrated_lufs=-20.0), target_lufs=-16.0)
    failed = [c for c in result.checks if not c.passed]
    assert len(failed) == 1
    assert failed[0].name == "響度收斂"
