"""收集驗證所需的指標：after 端實際量測、before 端直接讀 report.json。

兩條入口的分工：
  collect_metrics      → 對「修復後的輸出檔」實際量測（批次路徑：整檔一次
                         解碼 + numpy 切片，LUFS/真峰值全檔單次 ffmpeg）。
  metrics_from_report  → 從 analyze 產出的 report.json（schema v2）直接組出
                         before 指標，不再對原始檔重量測——analyze 當下已用
                         同一條批次路徑量過，重量一次只是浪費時間，且兩次
                         量測若路徑不同反而會讓配對比對失真。
"""
import json
from pathlib import Path
from statistics import median, pstdev

from .bulk import load_audio, measure_overall, rms_db, slice_samples


def collect_metrics(path: Path, plan: dict, report_path: Path | None = None) -> dict:
    """量測整體響度、真峰值、底噪與句間標準差（批次路徑）。

    量測方式：整檔一次解碼進記憶體（原生取樣率），逐句／逐窗 RMS 全用
    numpy 切片計算；integrated LUFS 與真峰值以 measure_overall 全檔單次
    ffmpeg 量測。不再逐區間 spawn ffmpeg（舊路徑每句每窗一次行程）。

    底噪取自 report.json 記錄的噪音採樣窗 —— 那是唯一經雙重確認、確定不含
    人聲的區間。不可改從 plan.json 的 utterances 推算空隙：那些邊界已外擴至
    靜音中點、彼此相接，中間沒有任何空隙可用。窗的「區間」來自 report，
    但 RMS 是對本函式的 path（即修復後輸出檔）實際計算的。
    """
    # 整檔一次解碼（原生率）：afftdn 等處理是絕對位準語意，重採樣會
    # 改變高頻噪音能量，量測必須用原生率
    buf = load_audio(path)
    # 全檔感知響度與真峰值：ebur128 演算法（K-weighting、閘門、過採樣）
    # 不宜自行重刻，維持交給 ffmpeg，但只跑全檔一次
    integrated_lufs, true_peak = measure_overall(path)
    # report.json 中經雙重確認的噪音採樣窗（詞間 gap ≥ 0.6 秒且 silencedetect 判靜音）
    windows = _noise_windows(report_path)
    # 沒有噪音採樣窗時回 None 代表「底噪未量測」，不可拿整體響度頂替。
    # loudnorm 幾乎必然把整體響度收斂到目標，用它冒充底噪會讓「降噪淨效果」
    # 這項檢查幾乎恆真，卻在報告上印成看似真實的改善數字誤導使用者以為
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
    noise_window_rms = [
        rms_db(slice_samples(buf, start, end)) for start, end in windows
    ]
    noise_rms_db = median(noise_window_rms) if noise_window_rms else None
    # SNR = 人聲中位 RMS − 底噪中位 RMS。
    # 驗證用 SNR 而非底噪絕對值：拉平與最終增益會把底噪連同人聲一起搬移
    # （那是設計行為），底噪絕對值因此可升可降；SNR 把共同的增益消掉，
    # 剩下的正是降噪的淨效果。
    utterance_rms = [
        rms_db(slice_samples(buf, u["start"], u["end"]))
        for u in plan["utterances"]
    ]
    # 逐窗與逐句的原始清單，供 verify 做**配對式**淨壓制計算。
    # 為什麼要配對：拉平的句級增益逐句不同，噪音窗多落在安靜句的增益
    # 範圍內，被抬得比人聲中位更多 —— 「全域中位對全域中位」的 SNR
    # 因此失真（實測假性劣化 4dB）。配對讓每個窗與**包含它的那一句**
    # 相減，共同增益完全相消，剩下的才是降噪淨效果。
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
        "integrated_lufs": integrated_lufs,
        # 用 ebur128 的真峰值，不是 astats 的樣本峰值
        "true_peak": true_peak,
        # 句間標準差改用逐句 RMS 而非逐句 LUFS：這項只是「拉平生效」的
        # 相對證據，RMS 與 LUFS 在句與句的相對比較上等價，且批次路徑
        # 已不再逐句量 LUFS（analyze 側同樣只留逐句 RMS），兩端一致
        # 才能前後可比。
        "utterance_rms_stdev": (
            pstdev(utterance_rms) if len(utterance_rms) > 1 else 0.0
        ),
    }


def metrics_from_report(report: dict) -> dict:
    """從 analyze 的 report.json（schema v2）組出 before 指標。

    analyze 當下已用同一條批次路徑量過原始檔（逐句/逐窗 RMS、全檔
    overall），這裡只做「讀值 + 聚合」，不碰音檔——before 與 after 的
    數值因此出自完全相同的量測定義，配對比對才有意義。

    window_owner 用 report 自身的 utterances 計算即可：已實際查證
    report 寫入的是 classification.utterances（segments._build_utterances
    產出，邊界**已外擴**到靜音中點、覆蓋全軸），而 plan 的 utterances
    來自 compute_utterance_gains，它原樣回傳 (u.start, u.end, gain)——
    兩邊是**同一組邊界**。after 端 collect_metrics 用 plan 邊界算 owner，
    此處用 report 邊界結果必然一致，配對不會錯位。

    舊版 report（v1：無 overall、噪音窗無 rms_db、句無 rms_db）直接拋
    ValueError 引導重跑 analyze——不可退回對原始檔重量測，那會讓
    before/after 走不同路徑。
    """
    # 硬性 schema 檢查：缺任一 v2 欄位都視為舊版報告
    overall = report.get("overall")
    windows = report.get("noise_windows", [])
    utterances = report.get("utterances", [])
    if (
        not isinstance(overall, dict)
        or any("rms_db" not in w for w in windows)
        or any("rms_db" not in u for u in utterances)
    ):
        raise ValueError(
            "report.json 缺少批次量測欄位（overall／逐窗 rms_db／逐句 rms_db），"
            "屬於舊版格式。請重新執行 analyze.py 產生新版 report.json。"
        )
    # 逐句／逐窗 RMS 直接讀 analyze 的實測值
    utterance_rms = [u["rms_db"] for u in utterances]
    noise_window_rms = [w["rms_db"] for w in windows]
    # 聚合定義必須與 collect_metrics 逐字相同（中位數、pstdev、SNR 公式），
    # 否則 before/after 比較的是兩種不同的統計量
    noise_rms_db = median(noise_window_rms) if noise_window_rms else None
    window_owner = [
        _owner_index(utterances, (w["start"] + w["end"]) / 2.0) for w in windows
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
        "integrated_lufs": overall["integrated_lufs"],
        "true_peak": overall["true_peak_db"],
        "utterance_rms_stdev": (
            pstdev(utterance_rms) if len(utterance_rms) > 1 else 0.0
        ),
    }


def _owner_index(utterances: list[dict], time_point: float) -> int:
    """回傳覆蓋指定時間點的句索引；utterances 邊界相接、覆蓋全軸。"""
    for index, utterance in enumerate(utterances):
        if utterance["start"] <= time_point < utterance["end"]:
            return index
    return len(utterances) - 1


def _noise_windows(report_path: Path | None) -> list[tuple[float, float]]:
    """從 report.json 取出噪音採樣窗；沒有報告時回傳空清單。"""
    if report_path is None or not report_path.exists():
        return []
    report = json.loads(report_path.read_text(encoding="utf-8"))
    return [(w["start"], w["end"]) for w in report.get("noise_windows", [])]
