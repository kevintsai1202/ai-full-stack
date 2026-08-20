"""verify 模組測試：四項硬指標的通過與失敗判定。"""
from ar.verify import verify


def _before() -> dict:
    """修復前的指標。"""
    return {"noise_rms_db": -45.0, "integrated_lufs": -24.0,
            "true_peak": -3.0, "utterance_lufs_stdev": 6.0}


def _after(**overrides) -> dict:
    """修復後的指標（可覆寫單項以測試失敗情境）。"""
    base = {"noise_rms_db": -58.0, "integrated_lufs": -16.1,
            "true_peak": -1.6, "utterance_lufs_stdev": 1.8}
    base.update(overrides)
    return base


def test_all_checks_pass_for_good_result():
    """四項全數達標時應通過。"""
    result = verify(_before(), _after(), target_lufs=-16.0)
    assert result.passed is True
    assert len(result.checks) == 4


def test_excessive_noise_reduction_fails():
    """底噪降幅超過 25dB 視為降噪過頭，把人聲一併削掉了。"""
    result = verify(_before(), _after(noise_rms_db=-75.0), target_lufs=-16.0)
    assert result.passed is False
    assert any("降噪過頭" in c.detail for c in result.checks if not c.passed)


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
    result = verify(_before(), _after(utterance_lufs_stdev=6.5), target_lufs=-16.0)
    assert result.passed is False
    assert any("拉平" in c.detail for c in result.checks if not c.passed)


def test_noise_check_reports_unverifiable_when_missing():
    """底噪為 None 時應回報不可驗證，而不是假裝通過或直接失敗。"""
    before = {**_before(), "noise_rms_db": None}
    after = _after(noise_rms_db=None)
    result = verify(before, after, target_lufs=-16.0)
    noise_check = next(c for c in result.checks if c.name == "底噪下降")
    assert noise_check.passed is True
    assert "不可驗證" in noise_check.detail


def test_single_utterance_flattening_is_not_a_failure():
    """單句音檔的標準差恆為 0，不得因此判為拉平失敗。"""
    before = {**_before(), "utterance_lufs_stdev": 0.0}
    after = _after(utterance_lufs_stdev=0.0)
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
