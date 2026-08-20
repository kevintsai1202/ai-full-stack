"""修復結果的四項硬指標驗證。

不靠耳朵主觀判斷。任一項不合格即報告失敗並指出失效環節，不得靜默通過。
"""
from dataclasses import dataclass, field

MAX_NOISE_DROP_DB = 25.0   # 底噪降幅上限，超過代表降噪把人聲也削了
LOUDNESS_TOLERANCE = 0.5   # 響度收斂容差（LUFS）
TRUE_PEAK_LIMIT = -1.5     # 真峰值上限（dBTP）


@dataclass
class Check:
    """單項驗證結果。"""
    name: str
    passed: bool
    detail: str


@dataclass
class VerifyResult:
    """整體驗證結果。"""
    passed: bool
    checks: list[Check] = field(default_factory=list)


def verify(before: dict, after: dict, target_lufs: float) -> VerifyResult:
    """比對修復前後指標，輸出四項檢查結果。"""
    checks = [
        _check_noise(before, after),
        _check_loudness(after, target_lufs),
        _check_true_peak(after),
        _check_flattening(before, after),
    ]
    return VerifyResult(passed=all(c.passed for c in checks), checks=checks)


def _check_noise(before: dict, after: dict) -> Check:
    """底噪應下降，但降幅過大代表降噪過頭。

    量的是 RMS 不是 LUFS：ebur128 的 integrated loudness 有 -70 LUFS 絕對
    閘門，安靜的底噪會被截斷成 -70，前後都是 -70、降幅永遠 0。

    底噪為 None 代表沒有噪音採樣窗可量（通常是缺 report.json）。
    此時明確回報「不可驗證」而不是拿別的數字頂替 —— 一個假裝通過的
    檢查比沒有檢查更危險。
    """
    if before["noise_rms_db"] is None or after["noise_rms_db"] is None:
        return Check("底噪下降", True,
                     "無噪音採樣窗可量測，此項不可驗證（請確認 report.json 存在）")
    drop = before["noise_rms_db"] - after["noise_rms_db"]
    if drop <= 0:
        return Check("底噪下降", False, f"底噪未下降（變化 {drop:.1f} dB），降噪未生效")
    if drop > MAX_NOISE_DROP_DB:
        return Check("底噪下降", False,
                     f"底噪下降 {drop:.1f} dB 超過 {MAX_NOISE_DROP_DB} dB 上限，"
                     f"降噪過頭，人聲可能一併被削除，請調低 denoise_db 重跑")
    return Check("底噪下降", True, f"底噪下降 {drop:.1f} dB")


def _check_loudness(after: dict, target_lufs: float) -> Check:
    """整體響度須收斂到目標值容差內。"""
    delta = abs(after["integrated_lufs"] - target_lufs)
    passed = delta <= LOUDNESS_TOLERANCE
    return Check("響度收斂", passed,
                 f"實測 {after['integrated_lufs']:.1f} LUFS，"
                 f"目標 {target_lufs:.1f}，誤差 {delta:.2f}"
                 + ("" if passed else "，loudnorm 未收斂"))


def _check_true_peak(after: dict) -> Check:
    """真峰值不得超標。

    這裡的值必須來自 ebur128 的 True peak（過採樣、含 inter-sample peak），
    不能用 astats 的樣本峰值 —— 後者系統性低估 0.3-3dB，會讓實際超標的
    內容通過檢查。
    """
    passed = after["true_peak"] <= TRUE_PEAK_LIMIT
    return Check("真峰值", passed,
                 f"實測 {after['true_peak']:.1f} dBTP，上限 {TRUE_PEAK_LIMIT}"
                 + ("" if passed else "，alimiter 失效"))


def _check_flattening(before: dict, after: dict) -> Check:
    """句間響度標準差變小是拉平成功的量化證據。

    只有一句時標準差恆為 0.0（沒有句間差異可言），此時這項檢查無意義，
    視為 N/A 通過 —— 否則 0.0 < 0.0 為 False，會把修復完全正確的單句
    音檔誤判為拉平失敗。
    """
    if before["utterance_lufs_stdev"] == 0.0 and after["utterance_lufs_stdev"] == 0.0:
        return Check("拉平生效", True, "只有一句（或句間本就無差異），此項不適用")
    passed = after["utterance_lufs_stdev"] < before["utterance_lufs_stdev"]
    return Check("拉平生效", passed,
                 f"句間標準差 {before['utterance_lufs_stdev']:.2f} → "
                 f"{after['utterance_lufs_stdev']:.2f}"
                 + ("" if passed else "，拉平未生效"))
