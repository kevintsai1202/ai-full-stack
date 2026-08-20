"""gain 模組測試：增益方向、限幅、平滑、表達式生成。"""
from ar.diagnose import ZoneDiagnosis
from ar.fingerprint import Zone
from ar.gain import (build_volume_expression, compute_utterance_gains,
                     compute_zone_gains)
from ar.measure import LoudnessStats
from ar.segments import Utterance


def _diagnosis(index: int, speech_lufs: float) -> ZoneDiagnosis:
    """建立指定人聲響度的診斷結果。"""
    return ZoneDiagnosis(
        zone_index=index, start=index * 10.0, end=(index + 1) * 10.0,
        noise_lufs=-55.0, speech_lufs=speech_lufs, snr_db=35.0,
        sibilance_ratio=0.05, rumble_ratio=0.02, clipped_ratio=0.0,
        reverb_slope=-60.0, denoise_db=12, needs_deesser=False,
        needs_highpass=False, needs_ai_rescue=False, issues=[],
    )


def test_zone_gain_lifts_quiet_zone_to_working_level():
    """人聲 -28 LUFS 的區段，補到 -20 工作位準應得 +8dB。"""
    gains = compute_zone_gains([_diagnosis(0, -28.0)], working_lufs=-20.0)
    assert abs(gains[0] - 8.0) < 1e-6


def test_zone_gain_attenuates_loud_zone():
    """人聲 -14 LUFS 的區段應被壓低 6dB。"""
    gains = compute_zone_gains([_diagnosis(0, -14.0)], working_lufs=-20.0)
    assert abs(gains[0] + 6.0) < 1e-6


def _setup(lufs_values: list[float]):
    """建立單一 zone、多句的測試情境。"""
    utterances = [Utterance(index=i, start=i * 2.0, end=(i + 1) * 2.0)
                  for i in range(len(lufs_values))]
    stats = [LoudnessStats(lufs=v, rms_db=v, peak_db=v + 10) for v in lufs_values]
    zones = [Zone(index=0, start=0.0, end=len(lufs_values) * 2.0)]
    return utterances, stats, zones


def test_utterance_gain_is_clamped_to_max():
    """一句只有 -40 LUFS（例如咳嗽），增益須被限在 +6dB，不得拉到人聲音量。"""
    utterances, stats, zones = _setup([-20.0, -40.0])
    result = compute_utterance_gains(utterances, stats, zones, [0.0], -20.0,
                                     max_gain_db=6.0, max_step_db=99.0)
    assert result[1][2] <= 6.0


def test_adjacent_gain_step_is_limited():
    """相鄰兩句的增益差不得超過 max_step_db，避免可聽的音量跳動。"""
    utterances, stats, zones = _setup([-20.0, -30.0, -20.0])
    result = compute_utterance_gains(utterances, stats, zones, [0.0], -20.0,
                                     max_gain_db=6.0, max_step_db=3.0)
    gains = [g for _, _, g in result]
    assert abs(gains[1] - gains[0]) <= 3.0 + 1e-6
    assert abs(gains[2] - gains[1]) <= 3.0 + 1e-6


def test_quiet_utterance_gets_positive_gain():
    """小聲句的增益方向必須為正。"""
    utterances, stats, zones = _setup([-20.0, -26.0])
    result = compute_utterance_gains(utterances, stats, zones, [0.0], -20.0)
    assert result[1][2] > 0.0


def test_volume_expression_covers_all_utterances():
    """volume 表達式須包含每一句的時間條件與增益值。"""
    expression = build_volume_expression([(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)])
    assert "between(t,0.000,4.500)" in expression
    assert "between(t,4.500,8.000)" in expression
    assert "2.000" in expression
    assert "-1.500" in expression


def test_volume_expression_is_single_line():
    """表達式必須是單行，換行會讓 ffmpeg 參數解析失敗。"""
    expression = build_volume_expression([(0.0, 1.0, 1.0), (1.0, 2.0, 2.0)])
    assert "\n" not in expression


def test_nonspeech_event_gets_extra_attenuation():
    """咳嗽／翻頁區間須額外疊加負增益，否則會跟著人聲一起被拉高。"""
    expression = build_volume_expression(
        [(0.0, 8.0, 3.0)], nonspeech_events=[(5.0, 5.4)], attenuation_db=-6.0
    )
    assert "between(t,5.000,5.400)*-6.000" in expression


def test_no_attenuation_terms_when_no_events():
    """沒有雜訊事件時不得出現多餘的負增益項。"""
    expression = build_volume_expression([(0.0, 8.0, 3.0)], nonspeech_events=[])
    assert "-6.000" not in expression
