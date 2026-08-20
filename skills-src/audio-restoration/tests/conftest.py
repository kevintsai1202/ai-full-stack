"""測試共用 fixture：以純 Python 合成可預測的測試音檔。"""
import math
import wave
from pathlib import Path

import numpy as np
import pytest

SAMPLE_RATE = 48000  # 取樣率，與實際講課素材一致


def _write_wav(path: Path, samples: np.ndarray, sample_rate: int = SAMPLE_RATE) -> None:
    """將 float32 (-1..1) 樣本寫成 16-bit 單聲道 PCM WAV。"""
    clipped = np.clip(samples, -1.0, 1.0)
    pcm = (clipped * 32767.0).astype(np.int16)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(pcm.tobytes())


def make_tone(duration: float, freq: float, amplitude: float,
              sample_rate: int = SAMPLE_RATE) -> np.ndarray:
    """產生指定時長、頻率、振幅的正弦波，用來模擬「一句話」。"""
    t = np.arange(int(duration * sample_rate)) / sample_rate
    return amplitude * np.sin(2 * math.pi * freq * t)


def make_noise(duration: float, amplitude: float,
               sample_rate: int = SAMPLE_RATE, seed: int = 0) -> np.ndarray:
    """產生固定亂數種子的白雜訊，用來模擬底噪（可重現）。"""
    rng = np.random.default_rng(seed)
    return amplitude * rng.standard_normal(int(duration * sample_rate))


@pytest.fixture
def synth_wav(tmp_path: Path) -> Path:
    """合成 8 秒測試音檔：兩句音量相差約 12dB，中間夾靜音（僅底噪）。

    時間軸配置（供各模組測試共用，數值不可隨意更動）：
      0.0-2.0s 靜音（僅底噪）
      2.0-4.0s 語句A（220Hz，振幅 0.5，約 -6 dBFS）
      4.0-5.0s 靜音（僅底噪）
      5.0-7.0s 語句B（220Hz，振幅 0.125，約 -18 dBFS）
      7.0-8.0s 靜音（僅底噪）
    """
    noise_amp = 0.002  # 底噪振幅，約 -54 dBFS
    parts = [
        make_noise(2.0, noise_amp, seed=1),
        make_tone(2.0, 220.0, 0.5) + make_noise(2.0, noise_amp, seed=2),
        make_noise(1.0, noise_amp, seed=3),
        make_tone(2.0, 220.0, 0.125) + make_noise(2.0, noise_amp, seed=4),
        make_noise(1.0, noise_amp, seed=5),
    ]
    path = tmp_path / "synth.wav"
    _write_wav(path, np.concatenate(parts))
    return path
