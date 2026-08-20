"""metrics 模組測試：底噪必須取自 report.json 的噪音採樣窗。"""
import json
from pathlib import Path

from ar.metrics import collect_metrics


def _metrics_plan() -> dict:
    """utterances 邊界相接的計畫（analyze.py 的實際輸出形態）。"""
    return {
        "schema_version": 1, "is_video": False, "target_lufs": -16.0,
        "zones": [{"index": 0, "start": 0.0, "end": 8.0, "gain_db": 0.0,
                   "denoise_db": 12, "noise_floor_db": -48.0, "needs_highpass": False,
                   "needs_deesser": False, "needs_ai_rescue": False, "issues": []}],
        "utterances": [{"start": 0.0, "end": 4.5, "gain_db": 0.0},
                       {"start": 4.5, "end": 8.0, "gain_db": 0.0}],
    }


def _metrics_report(path: Path) -> Path:
    """含噪音採樣窗的 report.json。"""
    payload = {"schema_version": 1,
               "noise_windows": [{"start": 0.2, "end": 1.8},
                                 {"start": 4.1, "end": 4.9}]}
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def test_noise_measured_from_report_windows(synth_wav: Path, tmp_path: Path):
    """底噪應取自噪音採樣窗，而非退化成整體響度。

    合成音檔底噪約 -54 dBFS、整體約 -20 LUFS，兩者差距極大；若量到接近
    整體的值，代表採樣窗沒有被使用。
    """
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=_metrics_report(tmp_path / "report.json"))
    assert metrics["noise_lufs"] < -40.0
    assert metrics["noise_lufs"] < metrics["integrated_lufs"] - 20.0


def test_utterance_stdev_reflects_level_difference(synth_wav: Path, tmp_path: Path):
    """兩句相差約 12dB，句間標準差應明顯大於 0。"""
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=_metrics_report(tmp_path / "r.json"))
    assert metrics["utterance_lufs_stdev"] > 3.0


def test_noise_is_none_when_report_missing(synth_wav: Path, tmp_path: Path):
    """沒有 report.json 時底噪回 None（代表未量測），不得用整體響度頂替。

    拿整體響度冒充底噪會讓「底噪下降」檢查幾乎恆真——loudnorm 本來就會
    把整體響度收斂到目標——卻在報告上印成看似真實的底噪降幅。
    """
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=tmp_path / "nonexistent.json")
    assert metrics["noise_lufs"] is None
    assert metrics["integrated_lufs"] is not None
