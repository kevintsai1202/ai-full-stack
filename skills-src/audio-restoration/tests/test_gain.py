"""gain 模組測試：增益方向、限幅、平滑、包絡生成與套用。"""
import numpy as np
import pytest

from ar.diagnose import ZoneDiagnosis
from ar.fingerprint import Zone
from ar.gain import (apply_gain_envelope, build_gain_envelope,
                     compute_utterance_gains, compute_zone_gains)
from ar.measure import LoudnessStats
from ar.segments import Utterance


def _diagnosis(index: int, speech_rms_db: float) -> ZoneDiagnosis:
    """建立指定人聲響度的診斷結果。"""
    return ZoneDiagnosis(
        zone_index=index, start=index * 10.0, end=(index + 1) * 10.0,
        noise_rms_db=-55.0, speech_rms_db=speech_rms_db, snr_db=35.0,
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
    stats = [LoudnessStats(lufs=v, rms_db=v, peak_db=v + 10, true_peak_db=v + 10)
              for v in lufs_values]
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


SR = 16000  # 包絡測試用取樣率


def _envelope_at(envelope, seconds: float) -> float:
    """取包絡在指定秒數的值，方便斷言。"""
    return float(envelope[int(seconds * SR)])


def test_envelope_holds_each_utterance_gain():
    """每句的增益應覆蓋該句的主要時段。"""
    env = build_gain_envelope(int(8 * SR), SR, [(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)])
    assert abs(_envelope_at(env, 1.0) - 2.0) < 1e-6
    assert abs(_envelope_at(env, 7.0) - (-1.5)) < 1e-6


def test_envelope_has_no_spike_at_utterance_boundary():
    """交界不得出現尖峰。

    早期用 ffmpeg between 表達式時，閉區間讓交界那一點的兩個條件同時成立，
    增益變成兩句相加（實測 t=4.5 得 0.5 而非介於 2.0 與 -1.5 之間）。
    這個測試就是在防止那個 bug 以任何形式回來。
    """
    env = build_gain_envelope(int(8 * SR), SR, [(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)])
    assert env.max() <= 2.0 + 1e-6
    assert env.min() >= -1.5 - 1e-6


def test_envelope_ramps_linearly_across_boundary():
    """交界處應線性過渡，中點恰為兩句增益的平均。"""
    env = build_gain_envelope(int(8 * SR), SR, [(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)],
                              ramp_ms=200)
    assert abs(_envelope_at(env, 4.5) - 0.25) < 0.05
    assert _envelope_at(env, 4.45) > _envelope_at(env, 4.5) > _envelope_at(env, 4.55)


def test_envelope_ramp_length_matches_parameter():
    """斜坡長度應等於 ramp_ms，超出範圍的兩側維持各自的平坦增益。"""
    env = build_gain_envelope(int(8 * SR), SR, [(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)],
                              ramp_ms=200)
    assert abs(_envelope_at(env, 4.39) - 2.0) < 1e-6
    assert abs(_envelope_at(env, 4.61) - (-1.5)) < 1e-6


def test_nonspeech_event_gets_extra_attenuation():
    """咳嗽／翻頁區間須額外疊加負增益，否則會跟著人聲一起被拉高。"""
    env = build_gain_envelope(int(8 * SR), SR, [(0.0, 8.0, 3.0)],
                              nonspeech_events=[(5.0, 5.4)], attenuation_db=-6.0)
    assert abs(_envelope_at(env, 5.2) - (-3.0)) < 1e-6
    assert abs(_envelope_at(env, 2.0) - 3.0) < 1e-6


def test_no_attenuation_when_no_events():
    """沒有雜訊事件時整條包絡應維持該句增益。"""
    env = build_gain_envelope(int(8 * SR), SR, [(0.0, 8.0, 3.0)], nonspeech_events=[])
    assert abs(env.min() - 3.0) < 1e-6
    assert abs(env.max() - 3.0) < 1e-6


def test_apply_envelope_scales_samples_by_db():
    """+6dB 應讓振幅約變兩倍，-6dB 約變一半。"""
    samples = np.full(SR, 0.25, dtype=np.float32)
    louder = apply_gain_envelope(samples, np.full(SR, 6.0))
    quieter = apply_gain_envelope(samples, np.full(SR, -6.0))
    assert abs(float(louder[0]) - 0.5) < 0.01
    assert abs(float(quieter[0]) - 0.125) < 0.01


def test_apply_envelope_rejects_length_mismatch():
    """樣本與包絡長度不符時必須報錯，不得靜默截斷而讓增益錯位。"""
    with pytest.raises(ValueError):
        apply_gain_envelope(np.zeros(100, dtype=np.float32), np.zeros(50))
