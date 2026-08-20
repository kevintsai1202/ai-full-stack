"""逐區間響度量測（LUFS / RMS / 峰值）。"""
import re
from dataclasses import dataclass
from pathlib import Path

from .ffmpeg_io import run_ffmpeg
from .segments import Utterance
from .silence import Interval

_I_RE = re.compile(r"^\s*I:\s*(-?[\d.]+|-inf)\s*LUFS", re.MULTILINE)
_RMS_RE = re.compile(r"RMS level dB:\s*(-?[\d.]+|-inf)")
_PEAK_RE = re.compile(r"Peak level dB:\s*(-?[\d.]+|-inf)")
# ebur128 的真峰值印在 "True peak:" 區塊底下的 "Peak:" 那一行，
# 與 astats 的 "Peak level dB:" 是兩個不同的量：後者是取樣點的最大值，
# 前者經過過採樣、含 inter-sample peak。驗證真峰值上限必須用前者，
# 用樣本峰值會系統性低估 0.3-3dB，可能實際超標卻回報合格。
_TRUE_PEAK_RE = re.compile(r"True peak:\s*\n\s*Peak:\s*(-?[\d.]+|-inf)\s*dBFS")

SILENT_FLOOR = -120.0  # 量到 -inf 時採用的替代值，避免後續運算出現無限大


class MeasurementParseError(RuntimeError):
    """ffmpeg 執行成功，但從其輸出解析不到預期的量測欄位時拋出。

    這與「量到 -inf（真實靜音）」是完全不同的狀況：後者是合法的量測結果，
    只是數值為負無限大；前者代表 regex 沒有在 stderr 中抓到對應欄位
    （可能是 ffmpeg 版本差異、濾鏡輸出格式變動、或輸出被截斷等環境問題）。
    若把「解析失敗」也靜默套用 SILENT_FLOOR，會讓兩者無法區分：一個其實
    音量正常的片段會被誤判成全靜音，下游逐句增益計算據此套用極端增益，
    而且因為沒有任何訊號，這個錯誤要等整條流程跑完才會被發現。因此解析
    失敗一律視為不可信的量測結果，直接拋例外中斷，交由呼叫端決定如何
    處理（記錄失敗區間、對該句重試、或提示人工檢查）。
    """


@dataclass
class LoudnessStats:
    """一段區間的響度量測結果。"""
    lufs: float            # EBU R128 integrated loudness
    rms_db: float          # RMS 位準
    peak_db: float         # 樣本峰值（astats，取樣點最大值，不含 inter-sample peak）
    true_peak_db: float    # 真峰值（ebur128，過採樣後含 inter-sample peak）


def _extract(match: re.Match | None, label: str, start: float, end: float) -> float:
    """從 regex match 物件取出數值。

    match 為 None 代表 ffmpeg 輸出中完全沒抓到這個欄位，屬於解析失敗，
    非真實靜音，直接拋出 MeasurementParseError；只有 match 存在且擷取到
    的數值字串是 "-inf" 時，才代表 ffmpeg 真的量到負無限大，以 SILENT_FLOOR
    地板值取代，避免後續運算出現無限大。
    """
    if match is None:
        raise MeasurementParseError(
            f"無法從 ffmpeg 輸出解析出「{label}」（區間 {start:.3f}-{end:.3f}s）："
            "欄位不存在，可能是 ffmpeg 版本或濾鏡輸出格式差異，非真實靜音，"
            "此量測結果不可信，需人工檢查"
        )
    value = match.group(1)
    if value == "-inf":
        return SILENT_FLOOR
    return float(value)


def measure_interval(path: Path, start: float, end: float) -> LoudnessStats:
    """量測單一區間的響度。

    ebur128 與 astats 一次跑完，避免對長檔案重複解碼。
    """
    duration = max(0.05, end - start)
    stderr = run_ffmpeg([
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(path),
        "-af", "ebur128=peak=true,astats=measure_perchannel=none",
        "-f", "null", "-",
    ])
    i_match = _I_RE.search(stderr)
    rms_match = _RMS_RE.search(stderr)
    peak_match = _PEAK_RE.search(stderr)
    true_peak_match = _TRUE_PEAK_RE.search(stderr)
    return LoudnessStats(
        lufs=_extract(i_match, "LUFS (I:)", start, end),
        rms_db=_extract(rms_match, "RMS level dB", start, end),
        peak_db=_extract(peak_match, "Peak level dB", start, end),
        true_peak_db=_extract(true_peak_match, "True peak", start, end),
    )


def measure_utterances(path: Path, utterances: list[Utterance]) -> list[LoudnessStats]:
    """逐句量測。

    任一句解析失敗會拋出 MeasurementParseError 中斷整個批次——對一份需要
    精準逐句補償增益的錄音而言，讓「無法信任的量測值」悄悄流入下游，
    比中斷一次批次跑更危險；由呼叫端（例如 analyze.py）決定要整批重跑、
    跳過該句、或提示人工檢查。
    """
    return [measure_interval(path, u.start, u.end) for u in utterances]


def measure_intervals(path: Path, intervals: list[Interval]) -> list[LoudnessStats]:
    """逐區間量測（供噪音窗使用）。

    同 measure_utterances，任一區間解析失敗即拋出例外中斷，理由同上。
    """
    return [measure_interval(path, i.start, i.end) for i in intervals]
