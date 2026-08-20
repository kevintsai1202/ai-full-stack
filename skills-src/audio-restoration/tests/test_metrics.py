"""metrics 模組測試：底噪必須取自 report.json 的噪音採樣窗，
before 指標必須能直接從 report.json 組出且與實測同構。"""
import json
import subprocess
import sys
from pathlib import Path

import pytest

from ar.metrics import collect_metrics, metrics_from_report

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def _metrics_plan() -> dict:
    """utterances 邊界相接的計畫（analyze.py 的實際輸出形態）。"""
    return {
        "schema_version": 2, "is_video": False, "target_lufs": -16.0,
        "zones": [{"index": 0, "start": 0.0, "end": 8.0, "gain_db": 0.0,
                   "denoise_db": 12, "noise_floor_db": -48.0, "needs_highpass": False,
                   "needs_deesser": False, "needs_ai_rescue": False, "issues": []}],
        "utterances": [{"start": 0.0, "end": 4.5, "gain_db": 0.0},
                       {"start": 4.5, "end": 8.0, "gain_db": 0.0}],
    }


def _metrics_report(path: Path) -> Path:
    """含噪音採樣窗的 report.json（collect_metrics 只讀窗的區間）。"""
    payload = {"schema_version": 2,
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
    assert metrics["noise_rms_db"] < -40.0
    assert metrics["noise_rms_db"] < metrics["integrated_lufs"] - 20.0


def test_utterance_stdev_reflects_level_difference(synth_wav: Path, tmp_path: Path):
    """兩句相差約 12dB，句間 RMS 標準差應明顯大於 0。"""
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=_metrics_report(tmp_path / "r.json"))
    assert metrics["utterance_rms_stdev"] > 3.0


def test_noise_is_none_when_report_missing(synth_wav: Path, tmp_path: Path):
    """沒有 report.json 時底噪回 None（代表未量測），不得用整體響度頂替。

    拿整體響度冒充底噪會讓「底噪下降」檢查幾乎恆真——loudnorm 本來就會
    把整體響度收斂到目標——卻在報告上印成看似真實的底噪降幅。
    """
    metrics = collect_metrics(synth_wav, _metrics_plan(),
                              report_path=tmp_path / "nonexistent.json")
    assert metrics["noise_rms_db"] is None
    assert metrics["integrated_lufs"] is not None


def test_metrics_from_report_rejects_v1_report():
    """舊版 report（無 overall、窗/句無 rms_db）必須明確拒收並引導重跑 analyze。

    不可靜默退回對原始檔重量測：那會讓 before/after 走不同量測路徑，
    配對比對失真。
    """
    v1_report = {
        "schema_version": 1,
        "noise_windows": [{"start": 0.2, "end": 1.8}],  # 無 rms_db
        "utterances": [{"index": 0, "start": 0.0, "end": 4.5}],  # 無 rms_db
        # 無 overall
    }
    with pytest.raises(ValueError, match="analyze"):
        metrics_from_report(v1_report)


def _fake_timeline(work_dir: Path) -> None:
    """預先放好時間軸，讓測試不需要真的跑 ASR（與 analyze CLI 測試同一配置）。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "info": {"language": "zh", "duration": 8.0, "model": "small"},
        "segments": [
            {"id": 1, "start": 2.0, "end": 4.0, "text": "A", "no_speech_prob": 0.0,
             "words": [{"start": 2.0, "end": 4.0, "word": "A", "probability": 0.9}]},
            {"id": 2, "start": 5.0, "end": 7.0, "text": "B", "no_speech_prob": 0.0,
             "words": [{"start": 5.0, "end": 7.0, "word": "B", "probability": 0.9}]},
        ],
    }
    (work_dir / "timeline.json").write_text(json.dumps(payload), encoding="utf-8")


def test_metrics_from_report_matches_collect_metrics(synth_wav: Path, tmp_path: Path):
    """metrics_from_report 與 collect_metrics 對同一原始檔必須同構且數值一致。

    實跑 analyze 產出 report.json/plan.json，然後：
      before_a = metrics_from_report(report)          ← 新的 before 路徑
      before_b = collect_metrics(原始檔, plan, report) ← 舊的 before 路徑
    兩者所有數值欄位須在 0.1 dB 內一致——這是「before 改讀 report 不重量測」
    不改變驗證語意的直接證據。window_owner 則必須完全相同（report 與 plan
    記錄的是同一組外擴後邊界，配對不得錯位）。
    """
    work_dir = tmp_path / "work"
    _fake_timeline(work_dir)
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "analyze.py"),
         "--input", str(synth_wav), "--work-dir", str(work_dir)],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    report = json.loads((work_dir / "report.json").read_text(encoding="utf-8"))
    plan = json.loads((work_dir / "plan.json").read_text(encoding="utf-8"))

    from_report = metrics_from_report(report)
    remeasured = collect_metrics(synth_wav, plan,
                                 report_path=work_dir / "report.json")

    # 配對索引必須逐項相同
    assert from_report["window_owner"] == remeasured["window_owner"]
    # 純量欄位在 0.1 dB 內一致
    for key in ("noise_rms_db", "snr_db", "integrated_lufs",
                "true_peak", "utterance_rms_stdev"):
        assert abs(from_report[key] - remeasured[key]) < 0.1, key
    # 清單欄位逐項一致
    for key in ("noise_window_rms", "utterance_rms"):
        assert len(from_report[key]) == len(remeasured[key])
        for a, b in zip(from_report[key], remeasured[key]):
            assert abs(a - b) < 0.1, key
