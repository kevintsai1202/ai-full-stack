"""analyze.py 端到端測試：對合成音檔跑完整體檢流程。"""
import json
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def _fake_timeline(work_dir: Path) -> None:
    """預先放好時間軸，讓測試不需要真的跑 ASR。"""
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


def test_analyze_produces_report_and_plan(synth_wav: Path, tmp_path: Path):
    """跑完 analyze 應產出 report.json 與 plan.json，且逐句增益方向正確。"""
    work_dir = tmp_path / "work"
    _fake_timeline(work_dir)
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "analyze.py"),
         "--input", str(synth_wav), "--work-dir", str(work_dir)],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    plan = json.loads((work_dir / "plan.json").read_text(encoding="utf-8"))
    assert (work_dir / "report.json").exists()
    assert plan["target_lufs"] == -16.0
    # 語句B（小聲）的增益必須大於語句A（大聲）的增益 —— 這是拉平的方向性驗證
    gains = [u["gain_db"] for u in plan["utterances"]]
    assert gains[1] > gains[0]


def test_analyze_report_v2_structure(synth_wav: Path, tmp_path: Path):
    """report.json 須符合 schema v2：句級只剩 RMS/峰值、噪音窗帶實測底噪、
    含全檔 overall——這些欄位是修復後驗證的 before 基準。"""
    work_dir = tmp_path / "work"
    _fake_timeline(work_dir)
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "analyze.py"),
         "--input", str(synth_wav), "--work-dir", str(work_dir)],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    report = json.loads((work_dir / "report.json").read_text(encoding="utf-8"))
    assert report["schema_version"] == 2

    # 句級：有 rms_db/peak_db、無 lufs（已不量測，留著會是假資料）
    for item in report["utterances"]:
        assert "rms_db" in item and "peak_db" in item
        assert "lufs" not in item
    # 語句A（振幅 0.5）比語句B（0.125）大聲約 12dB。實際略小於 12：
    # 語句邊界外擴到靜音中點，兩句都摻入等長的底噪段稀釋 RMS，
    # 而小聲句受稀釋較少（訊號與底噪差距較小），差值收斂到 11dB 附近。
    rms = [u["rms_db"] for u in report["utterances"]]
    assert 9.0 < (rms[0] - rms[1]) < 13.0

    # 噪音窗：逐窗記錄實測底噪（合成素材約 -54 dBFS）
    for window in report["noise_windows"]:
        assert window["rms_db"] < -40.0

    # 全檔 overall：合成素材整體遠大於靜音、真峰值不超過 0 dBFS 太多
    overall = report["overall"]
    assert -40.0 < overall["integrated_lufs"] < 0.0
    assert overall["true_peak_db"] < 3.0
