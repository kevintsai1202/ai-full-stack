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
