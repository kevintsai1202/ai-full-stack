"""measure 模組測試：逐區間 LUFS / RMS / peak 量測。"""
from pathlib import Path

from ar.measure import measure_interval


def test_loud_utterance_measures_higher_than_quiet_one(synth_wav: Path):
    """合成音檔語句A（振幅 0.5）應比語句B（振幅 0.125）大約 12dB。"""
    loud = measure_interval(synth_wav, 2.2, 3.8)
    quiet = measure_interval(synth_wav, 5.2, 6.8)
    delta = loud.lufs - quiet.lufs
    assert 10.0 < delta < 14.0


def test_silence_measures_much_quieter_than_speech(synth_wav: Path):
    """靜音段（僅底噪）的 RMS 應遠低於語句段。"""
    silence = measure_interval(synth_wav, 0.2, 1.8)
    speech = measure_interval(synth_wav, 2.2, 3.8)
    assert speech.rms_db - silence.rms_db > 30.0


def test_peak_reflects_amplitude(synth_wav: Path):
    """語句A 振幅 0.5，峰值應接近 -6 dBFS。"""
    stats = measure_interval(synth_wav, 2.2, 3.8)
    assert -7.5 < stats.peak_db < -4.5
