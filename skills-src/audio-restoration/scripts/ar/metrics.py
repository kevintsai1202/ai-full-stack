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
    if windows:
        # 底噪取所有採樣窗中最安靜（LUFS 最低）者，避免偶發殘留人聲拉高估計值
        noise_lufs = min(measure_interval(path, start, end).lufs
                         for start, end in windows)
    else:
        # 沒有 report.json 時退回整體響度，不得拋例外中斷修復流程
        noise_lufs = overall.lufs
    return {
        "noise_lufs": noise_lufs,
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
