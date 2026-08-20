"""收集驗證所需的四項指標。"""
import json
import statistics
from statistics import median
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
    # 取中位數而非 min：min 選的是最安靜的那個窗，而片頭/片尾常有近乎
    # 數位靜音的區段（實測某素材第一個窗 -81.5 dB，比其他窗低 15dB 以上）。
    # 那個離群值會主導底噪代表值，讓修復前後都測不出改善。
    # analyze.py 給 afftdn 的底噪也是取中位數，兩處必須用同一種聚合。
    noise_rms_db = (
        median([measure_interval(path, start, end).rms_db for start, end in windows])
        if windows else None
    )
    # SNR = 人聲中位 RMS − 底噪中位 RMS。
    # 驗證用 SNR 而非底噪絕對值：拉平與最終增益會把底噪連同人聲一起搬移
    # （那是設計行為），底噪絕對值因此可升可降；SNR 把共同的增益消掉，
    # 剩下的正是降噪的淨效果。
    utterance_rms = [
        measure_interval(path, u["start"], u["end"]).rms_db
        for u in plan["utterances"]
    ]
    # 逐窗與逐句的原始清單，供 verify 做**配對式**淨壓制計算。
    # 為什麼要配對：拉平的句級增益逐句不同，噪音窗多落在安靜句的增益
    # 範圍內，被抬得比人聲中位更多 —— 「全域中位對全域中位」的 SNR
    # 因此失真（實測假性劣化 4dB）。配對讓每個窗與**包含它的那一句**
    # 相減，共同增益完全相消，剩下的才是降噪淨效果。
    noise_window_rms = [
        measure_interval(path, start, end).rms_db for start, end in windows
    ]
    # 每個噪音窗所屬的句索引（utterances 邊界外擴後覆蓋整條時間軸）
    window_owner = [
        _owner_index(plan["utterances"], (start + end) / 2.0)
        for start, end in windows
    ]
    snr_db = (
        median(utterance_rms) - noise_rms_db
        if noise_rms_db is not None and utterance_rms else None
    )
    return {
        "noise_rms_db": noise_rms_db,
        "noise_window_rms": noise_window_rms,
        "utterance_rms": utterance_rms,
        "window_owner": window_owner,
        "snr_db": snr_db,
        "integrated_lufs": overall.lufs,
        # 用 ebur128 的真峰值，不是 astats 的樣本峰值
        "true_peak": overall.true_peak_db,
        "utterance_lufs_stdev": (
            statistics.pstdev(utterance_lufs) if len(utterance_lufs) > 1 else 0.0
        ),
    }


def _owner_index(utterances: list[dict], time_point: float) -> int:
    """回傳覆蓋指定時間點的句索引；utterances 邊界相接、覆蓋全軸。"""
    for index, utterance in enumerate(utterances):
        if utterance["start"] <= time_point < utterance["end"]:
            return index
    return len(utterances) - 1


def _total_duration(plan: dict) -> float:
    """從計畫推得總時長。"""
    return max(zone["end"] for zone in plan["zones"])


def _noise_windows(report_path: Path | None) -> list[tuple[float, float]]:
    """從 report.json 取出噪音採樣窗；沒有報告時回傳空清單。"""
    if report_path is None or not report_path.exists():
        return []
    report = json.loads(report_path.read_text(encoding="utf-8"))
    return [(w["start"], w["end"]) for w in report.get("noise_windows", [])]
