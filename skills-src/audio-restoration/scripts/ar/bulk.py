"""批次量測基礎設施：單次全檔解碼 + numpy 聚合。

現行流程對每句／每窗各 spawn 一次 ffmpeg（measure.measure_interval），
全流程約 2,800 次行程，佔 90% 處理時間。本模組提供「整檔一次解碼進
記憶體，之後所有區間統計都在 numpy 上完成」的替代路徑，讓 RMS／峰值
這類可向量化的量測不再需要每區間一次行程；LUFS／真峰值因演算法
（K-weighting、閘門、過採樣）不宜自行重刻，改為全檔單次 ffmpeg 量測。
"""
import subprocess
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from .ffmpeg_io import FFmpegError, require_tool, run_ffmpeg
# regex 與 _extract 直接沿用 measure 模組：那些 regex 帶著 ffmpeg 版本差異
# 的教訓（例如 True peak 與 astats Peak 是兩個不同的量），複製一份會讓
# 未來的修正漏掉一份，因此這裡只 import 不重寫。
from .measure import _I_RE, _TRUE_PEAK_RE, SILENT_FLOOR, _extract
from .probe import probe

# SILENT_FLOOR（-120dB）對應的線性振幅門檻。RMS／峰值低於此值時視為
# 數位靜音，直接回傳地板值，避免 log10(0) 產生 -inf 汙染下游統計。
# 注意這比 astats 的字面行為**刻意放寬**：astats 只在精確數位靜音（全零）
# 才印 -inf，-135dBFS 的真實訊號仍會印出數字。等價性測試證明的是
# 「正常訊號區間」兩條路徑一致，不是地板語意逐位元相同——-120dB 以下
# 的差異對下游任何決策門檻都無意義，統一收斂到地板值反而更穩健。
_SILENT_LINEAR = 10.0 ** (SILENT_FLOOR / 20.0)


@dataclass
class AudioBuffer:
    """整檔解碼後駐留記憶體的音訊緩衝。

    samples 固定為 float32 單聲道：與 fingerprint.read_samples 的解碼
    格式一致，且 float32 已足夠承載 16/24-bit 素材的動態範圍，
    整檔駐留時記憶體只有 float64 的一半。
    """
    samples: np.ndarray   # float32 單聲道全檔樣本
    sample_rate: int      # 實際解碼取樣率（重採樣後即為指定值）
    duration: float       # 總時長（秒），= samples.size / sample_rate


def load_audio(path: Path, sample_rate: int | None = None) -> AudioBuffer:
    """單次 ffmpeg 全檔解碼成 float32 單聲道緩衝。

    sample_rate 為 None 時沿用來源原生取樣率（先以 ffprobe 取得），
    確保後續切片與時間軸換算不因隱性重採樣產生偏差；指定時則重採樣，
    供指紋等只需 16kHz 的下游使用。

    解碼失敗拋 FFmpegError；「解出 0 個樣本」也視為失敗——空陣列不會
    立即報錯，但會讓下游所有統計靜默變成 nan 或地板值，等到整條流程
    跑完才發現，比在源頭中斷危險得多。
    """
    if sample_rate is None:
        sample_rate = probe(path).sample_rate  # 來源原生取樣率
    cmd = [
        require_tool("ffmpeg"), "-hide_banner", "-nostdin", "-v", "error",
        "-i", str(path),
        "-ac", "1", "-ar", str(sample_rate), "-f", "f32le", "-",
    ]
    result = subprocess.run(cmd, capture_output=True)
    if result.returncode != 0:
        raise FFmpegError(
            f"全檔解碼失敗：{result.stderr.decode('utf-8', 'replace')[-500:]}"
        )
    samples = np.frombuffer(result.stdout, dtype=np.float32)
    if samples.size == 0:
        raise FFmpegError(f"{path} 解碼成功但沒有任何樣本，無法進行量測")
    return AudioBuffer(
        samples=samples,
        sample_rate=sample_rate,
        duration=samples.size / sample_rate,
    )


def slice_samples(buf: AudioBuffer, start: float, end: float) -> np.ndarray:
    """依秒數切出樣本子區間（回傳 view，不複製）。

    邊界一律 clamp 到 [0, size]：呼叫端的區間常由靜音偵測推得，可能
    略超出檔案頭尾，直接 clamp 比逐處防呆可靠。end <= start 時回傳
    空陣列而不報錯——這是合法的退化區間，語意（跳過或視為靜音）由
    呼叫端自行決定。

    秒→樣本索引的換算採**截斷**（int()），必須與 gain.build_gain_envelope
    的索引換算維持同一種慣例：後續流程會把同一組 (start, end) 邊界同時
    餵給 bulk（計算增益）與 gain（套用增益），若這裡用四捨五入，小數
    樣本偏移 ≥ 0.5 時兩條路徑會選到差一個樣本的索引，形成「增益計算
    位置」與「增益實際套用位置」的系統性錯位。
    """
    start_index = max(0, min(buf.samples.size, int(start * buf.sample_rate)))
    end_index = max(0, min(buf.samples.size, int(end * buf.sample_rate)))
    if end_index <= start_index:
        return buf.samples[:0]
    return buf.samples[start_index:end_index]


def rms_db(samples: np.ndarray) -> float:
    """計算 RMS 位準（dBFS）。

    空陣列或 RMS 低到數位靜音門檻時回傳 measure.SILENT_FLOOR，
    與 astats 路徑量到 -inf 時的處理一致，讓兩條路徑對下游可互換。
    使用 float64 累加：float32 對長區間平方和累加會損失精度。
    """
    if samples.size == 0:
        return SILENT_FLOOR
    rms = float(np.sqrt(np.mean(np.square(samples, dtype=np.float64))))
    if rms <= _SILENT_LINEAR:
        return SILENT_FLOOR
    return 20.0 * float(np.log10(rms))


def peak_db(samples: np.ndarray) -> float:
    """計算樣本峰值（dBFS，取樣點最大絕對值，不含 inter-sample peak）。

    靜音地板處理同 rms_db。注意這對應 astats 的 Peak level dB，
    不是 ebur128 的 True peak——驗證真峰值上限仍須用 measure_overall。
    """
    if samples.size == 0:
        return SILENT_FLOOR
    peak = float(np.max(np.abs(samples)))
    if peak <= _SILENT_LINEAR:
        return SILENT_FLOOR
    return 20.0 * float(np.log10(peak))


def measure_overall(path: Path) -> tuple[float, float]:
    """全檔單次 ffmpeg 量測 integrated LUFS 與真峰值（dBFS）。

    LUFS（K-weighting + 閘門）與真峰值（過採樣）演算法不宜以 numpy
    自行重刻，維持交給 ffmpeg 的 ebur128；但改為整檔一次，取代逐區間
    spawn。解析沿用 measure 模組的 regex 與 _extract，解析失敗會由
    _extract 拋出 MeasurementParseError（與逐區間路徑同一套錯誤語意）。
    """
    stderr = run_ffmpeg([
        "-i", str(path),
        "-af", "ebur128=peak=true",
        "-f", "null", "-",
    ])
    i_match = _I_RE.search(stderr)
    true_peak_match = _TRUE_PEAK_RE.search(stderr)
    # _extract 的 start/end 只用於錯誤訊息定位，與 AudioBuffer.duration
    # 是不同概念（那是解碼後的實際時長）。成功路徑不需要它，只有解析
    # 失敗要拋例外時才值得多花一次 ffprobe 取得檔案總時長，讓錯誤訊息
    # 能標示「這是 0 到全長的全檔量測」而非誤導的 0-0 區間。
    duration_for_error = 0.0  # 錯誤訊息用的退路時長
    if i_match is None or true_peak_match is None:
        duration_for_error = probe(path).duration
    lufs = _extract(i_match, "LUFS (I:)", 0.0, duration_for_error)
    true_peak = _extract(true_peak_match, "True peak", 0.0, duration_for_error)
    return lufs, true_peak
