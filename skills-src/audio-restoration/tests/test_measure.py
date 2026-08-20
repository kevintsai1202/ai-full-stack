"""measure 模組測試：逐區間 LUFS / RMS / peak 量測。"""
import wave
from pathlib import Path

import numpy as np
import pytest

from ar.measure import (
    SILENT_FLOOR,
    MeasurementParseError,
    measure_interval,
)


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


def _write_true_silent_wav(path: Path, duration: float = 1.0, sample_rate: int = 48000) -> None:
    """寫入樣本「全為數位 0」的 wav（無底噪），用來觸發 ffmpeg 印出真正的 -inf。

    與 synth_wav fixture 的靜音段不同：synth_wav 的靜音段混有底噪
    （振幅 0.002），量到的是有效浮點值（約 -50.9 LUFS），不會走到
    -inf 分支；這裡刻意產生完全無訊號的樣本，才能驗證
    SILENT_FLOOR 真正生效的路徑。
    """
    samples = np.zeros(int(duration * sample_rate), dtype=np.int16)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(samples.tobytes())


def test_true_digital_silence_maps_rms_and_peak_to_silent_floor(tmp_path: Path):
    """樣本全為 0 時，ffmpeg 對 RMS／Peak 會印出 -inf，應轉換為 SILENT_FLOOR。

    ebur128 的 Integrated loudness 有 -70 LUFS 的絕對閘門，因此即使是
    完全靜音也不會印出字面上的 "-inf"（而是印出 "-70.0 LUFS"），故此測試
    只驗證 astats 的 RMS／Peak 兩個真正會印出 -inf 的欄位。
    """
    path = tmp_path / "true_silence.wav"
    _write_true_silent_wav(path)
    stats = measure_interval(path, 0.0, 1.0)
    assert stats.rms_db == SILENT_FLOOR
    assert stats.peak_db == SILENT_FLOOR


def test_parse_failure_raises_distinct_error_instead_of_silent_floor(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    """ffmpeg 輸出解析不到量測欄位時，應拋出可辨識的例外，而非靜默當成 -inf 靜音。

    這裡以 monkeypatch 模擬「ffmpeg 執行成功、但輸出格式不含預期欄位」的情境
    （例如版本差異或濾鏡輸出格式變動），驗證這條路徑不會與「真實量到 -inf」
    混淆成同一個 SILENT_FLOOR 結果。
    """
    import ar.measure as measure_module

    def fake_run_ffmpeg(args: list[str]) -> str:
        """假造一段不含任何預期量測欄位的 ffmpeg stderr 輸出。"""
        return "unexpected ffmpeg output without any recognizable measurement fields"

    monkeypatch.setattr(measure_module, "run_ffmpeg", fake_run_ffmpeg)
    dummy_path = tmp_path / "dummy.wav"
    dummy_path.touch()

    with pytest.raises(MeasurementParseError):
        measure_interval(dummy_path, 0.0, 1.0)
