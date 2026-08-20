"""bulk 模組測試：單次全檔解碼路徑與逐區間 ffmpeg 路徑的等價性。

核心驗收是「numpy 聚合的 RMS／峰值」與「astats 逐區間量測」在同一
訊號上的差異必須小於 0.1dB——後續任務會把 analyze/metrics 切換到
bulk 路徑，等價性不成立就等於改變了所有下游門檻的實際語意。

訊號設計注意：astats 的 RMS 是對「整個量測窗」計算，因此所有比對
區間的邊界都對齊到整秒（48kHz 下必為整數樣本點），避免半個樣本的
邊界差造成假性失敗。synth_wav fixture 的時間軸本身就是整秒配置。
"""
import wave
from pathlib import Path

import numpy as np
import pytest

from ar.bulk import (
    AudioBuffer,
    load_audio,
    measure_overall,
    peak_db,
    rms_db,
    slice_samples,
)
from ar.ffmpeg_io import FFmpegError
from ar.measure import SILENT_FLOOR, measure_interval

# 等價性驗收門檻（dB）。0.1dB 遠小於任何下游決策門檻的粒度，
# 且實測兩條路徑的差異在 0.01dB 以內，此門檻留有充分餘裕。
EQUIV_TOLERANCE_DB = 0.1


# ---------- 等價性：RMS ----------

def test_rms_equivalent_to_astats_full_file(synth_wav: Path):
    """整檔 RMS：numpy 路徑與 astats 路徑差異須小於 0.1dB。"""
    buf = load_audio(synth_wav)
    astats = measure_interval(synth_wav, 0.0, buf.duration)
    numpy_rms = rms_db(buf.samples)
    assert abs(numpy_rms - astats.rms_db) < EQUIV_TOLERANCE_DB


@pytest.mark.parametrize("start,end", [(2.0, 4.0), (5.0, 7.0), (0.0, 2.0)])
def test_rms_equivalent_to_astats_sub_intervals(synth_wav: Path, start: float, end: float):
    """子區間 RMS：涵蓋大聲句、小聲句與純底噪段，皆須與 astats 等價。"""
    buf = load_audio(synth_wav)
    astats = measure_interval(synth_wav, start, end)
    numpy_rms = rms_db(slice_samples(buf, start, end))
    assert abs(numpy_rms - astats.rms_db) < EQUIV_TOLERANCE_DB


# ---------- 等價性：樣本峰值 ----------

def test_peak_equivalent_to_astats_full_file(synth_wav: Path):
    """整檔樣本峰值：numpy 路徑與 astats 的 Peak level dB 差異須小於 0.1dB。"""
    buf = load_audio(synth_wav)
    astats = measure_interval(synth_wav, 0.0, buf.duration)
    numpy_peak = peak_db(buf.samples)
    assert abs(numpy_peak - astats.peak_db) < EQUIV_TOLERANCE_DB


@pytest.mark.parametrize("start,end", [(2.0, 4.0), (5.0, 7.0)])
def test_peak_equivalent_to_astats_sub_intervals(synth_wav: Path, start: float, end: float):
    """子區間樣本峰值：兩個不同振幅的語句段皆須與 astats 等價。"""
    buf = load_audio(synth_wav)
    astats = measure_interval(synth_wav, start, end)
    numpy_peak = peak_db(slice_samples(buf, start, end))
    assert abs(numpy_peak - astats.peak_db) < EQUIV_TOLERANCE_DB


# ---------- 等價性：measure_overall（LUFS / 真峰值）----------

def test_measure_overall_equivalent_to_measure_interval(synth_wav: Path):
    """全檔 LUFS 與真峰值：與 measure_interval(0, 全長) 的結果差異須小於 0.1。"""
    buf = load_audio(synth_wav)
    lufs, true_peak = measure_overall(synth_wav)
    interval = measure_interval(synth_wav, 0.0, buf.duration)
    assert abs(lufs - interval.lufs) < EQUIV_TOLERANCE_DB
    assert abs(true_peak - interval.true_peak_db) < EQUIV_TOLERANCE_DB


# ---------- 靜音地板 ----------

def test_all_zero_signal_maps_to_silent_floor():
    """全零訊號的 RMS 與峰值皆須回傳 SILENT_FLOOR，而非 -inf 或 nan。"""
    zeros = np.zeros(48000, dtype=np.float32)
    assert rms_db(zeros) == SILENT_FLOOR
    assert peak_db(zeros) == SILENT_FLOOR


def test_empty_array_maps_to_silent_floor():
    """空陣列（例如退化區間切片的結果）也須回傳 SILENT_FLOOR。"""
    empty = np.array([], dtype=np.float32)
    assert rms_db(empty) == SILENT_FLOOR
    assert peak_db(empty) == SILENT_FLOOR


# ---------- load_audio ----------

def test_load_audio_native_rate_matches_source(synth_wav: Path):
    """未指定取樣率時應沿用來源原生 48kHz，樣本數等於 8 秒全長。"""
    buf = load_audio(synth_wav)
    assert buf.sample_rate == 48000
    assert buf.samples.dtype == np.float32
    assert abs(buf.samples.size - 8 * 48000) <= 1
    assert abs(buf.duration - 8.0) < 1e-3


def test_load_audio_resamples_to_requested_rate(synth_wav: Path):
    """指定 16kHz 時應重採樣：樣本數 = 時長 × 16000（容差 ±1）。"""
    buf = load_audio(synth_wav, sample_rate=16000)
    assert buf.sample_rate == 16000
    assert abs(buf.samples.size - 8 * 16000) <= 1


def test_load_audio_empty_file_raises(tmp_path: Path):
    """零位元組的假檔案解碼必然失敗，須拋出 FFmpegError 而非回傳空緩衝。"""
    bogus = tmp_path / "empty.wav"
    bogus.touch()
    with pytest.raises(FFmpegError):
        load_audio(bogus, sample_rate=16000)


def test_load_audio_zero_frame_wav_raises(tmp_path: Path):
    """合法 WAV 頭但零個音框：ffmpeg 會「成功」解出 0 樣本，仍須視為失敗。

    這是 0 樣本檢查存在的理由——空陣列不會立即報錯，但會讓下游所有
    統計靜默變成地板值，必須在源頭中斷。
    """
    path = tmp_path / "zero_frames.wav"
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(48000)
        handle.writeframes(b"")
    with pytest.raises(FFmpegError):
        load_audio(path, sample_rate=48000)


# ---------- slice_samples ----------

def _make_buffer(size: int = 48000, sample_rate: int = 48000) -> AudioBuffer:
    """建立內容為索引序列的緩衝，方便直接驗證切片落點。"""
    samples = np.arange(size, dtype=np.float32)
    return AudioBuffer(samples=samples, sample_rate=sample_rate,
                       duration=size / sample_rate)


def test_slice_samples_basic_range():
    """一般切片：秒數換算成樣本索引，長度與內容皆正確。"""
    buf = _make_buffer()
    sliced = slice_samples(buf, 0.25, 0.5)
    assert sliced.size == 12000
    assert sliced[0] == 12000.0  # 0.25s × 48000 = 第 12000 個樣本


def test_slice_samples_clamps_out_of_range():
    """超出檔案頭尾的邊界須 clamp 到 [0, size]，不報錯也不越界。"""
    buf = _make_buffer()
    before = slice_samples(buf, -5.0, 0.5)   # 起點越過檔頭
    after = slice_samples(buf, 0.5, 99.0)    # 終點越過檔尾
    assert before.size == 24000
    assert before[0] == 0.0
    assert after.size == 24000
    assert after[-1] == 47999.0


def test_slice_samples_degenerate_interval_returns_empty():
    """end <= start（含完全在檔外的區間）回傳空陣列而非例外。"""
    buf = _make_buffer()
    assert slice_samples(buf, 0.5, 0.5).size == 0
    assert slice_samples(buf, 0.8, 0.2).size == 0
    assert slice_samples(buf, 10.0, 20.0).size == 0  # clamp 後兩端重合
