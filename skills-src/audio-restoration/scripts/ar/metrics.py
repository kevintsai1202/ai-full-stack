"""收集驗證所需的四項指標。"""
import json
import statistics
from pathlib import Path

from .measure import measure_interval


def collect_metrics(path: Path, plan: dict, report_path: Path | None = None) -> dict:
    """量測整體響度、真峰值、底噪與句間標準差。

    底噪取自 report.json 記錄的噪音採樣窗 —— 那是唯一經雙重確認、確定不含
    人聲的區間。不可改從 plan.json 的 utterances 推算空隙：那些邊界已外擴至
    靜音中點、彼此相接，中間沒有任何空隙可用。
    """
    # 整段音檔的整體響度量測結果
    overall = measure_interval(path, 0.0, _total_duration(plan))
    # 逐句量測到的 LUFS 清單，用來計算句間標準差（拉平效果的量化證據）
    utterance_lufs = [
        measure_interval(path, u["start"], u["end"]).lufs for u in plan["utterances"]
    ]
    # report.json 中經雙重確認的噪音採樣窗（詞間 gap ≥ 0.6 秒且 silencedetect 判靜音）
    windows = _noise_windows(report_path)
    # 沒有噪音採樣窗時回 None 代表「底噪未量測」，不可拿整體響度頂替。
    # loudnorm 幾乎必然把整體響度收斂到目標，用它冒充底噪會讓「底噪下降」
    # 這項檢查幾乎恆真，卻在報告上印成「底噪下降 2.4 dB」誤導使用者以為
    # 降噪確實生效。實測已證實：無 report.json 時 noise 與 integrated
    # 會是完全相同的數字。
    # 底噪用 RMS 而非 LUFS：ebur128 的 integrated loudness 有 **-70 LUFS 絕對
    # 閘門**，安靜的底噪一律被截斷成 -70，修復前後都量到 -70，降幅永遠是 0。
    # 真實素材實測證實了這點（前 -70.00 → 後 -70.00，「底噪下降」判定失敗）。
    # RMS 沒有閘門，能真實反映底噪位準。
    noise_rms_db = (
        min(measure_interval(path, start, end).rms_db for start, end in windows)
        if windows else None
    )
    return {
        "noise_rms_db": noise_rms_db,
        "integrated_lufs": overall.lufs,
        # 用 ebur128 的真峰值，不是 astats 的樣本峰值
        "true_peak": overall.true_peak_db,
        "utterance_lufs_stdev": (
            statistics.pstdev(utterance_lufs) if len(utterance_lufs) > 1 else 0.0
        ),
    }


def _total_duration(plan: dict) -> float:
    """從計畫推得總時長。"""
    return max(zone["end"] for zone in plan["zones"])


def _noise_windows(report_path: Path | None) -> list[tuple[float, float]]:
    """從 report.json 取出噪音採樣窗；沒有報告時回傳空清單。"""
    if report_path is None or not report_path.exists():
        return []
    report = json.loads(report_path.read_text(encoding="utf-8"))
    return [(w["start"], w["end"]) for w in report.get("noise_windows", [])]
