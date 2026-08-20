"""restore.py 端到端測試：閘門、輸出、驗證。"""
import json
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def _write_plan(path: Path, input_path: Path) -> Path:
    """寫出可執行的處理計畫。"""
    plan = {
        "schema_version": 1,
        "input": str(input_path),
        "is_video": False,
        "target_lufs": -16.0,
        "zones": [
            {"index": 0, "start": 0.0, "end": 4.5, "gain_db": 0.0, "denoise_db": 12,
             "noise_floor_db": -48.0, "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
            {"index": 1, "start": 4.5, "end": 8.0, "gain_db": 6.0, "denoise_db": 12,
             "noise_floor_db": -48.0, "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
        ],
        "utterances": [
            {"start": 0.0, "end": 4.5, "gain_db": 0.0},
            {"start": 4.5, "end": 8.0, "gain_db": 3.0},
        ],
    }
    path.write_text(json.dumps(plan, ensure_ascii=False), encoding="utf-8")
    return path


def test_restore_refuses_without_plan(tmp_path: Path):
    """沒有 plan.json 時必須拒絕執行 —— 這是體檢閘門的強制實現。"""
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(tmp_path / "missing.json"), "--out", str(tmp_path / "o.wav")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode != 0
    assert "plan" in (result.stderr + result.stdout).lower()


def test_restore_produces_output_and_verification(synth_wav: Path, tmp_path: Path):
    """正常執行應產出修復檔、ASR WAV、AB 片段與 verify.json。"""
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    out_path = tmp_path / "restored.wav"
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(out_path),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    assert out_path.exists()
    assert (tmp_path / "work" / "verify.json").exists()
    assert (tmp_path / "work" / "restored-16k.wav").exists()
    assert (tmp_path / "work" / "preview-ab.wav").exists()


def test_restore_output_hits_target_loudness(synth_wav: Path, tmp_path: Path):
    """修復後整體響度應落在目標 ±1.0 LUFS。"""
    from ar.measure import measure_interval
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    out_path = tmp_path / "restored.wav"
    subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(out_path),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, check=True, encoding="utf-8",
    )
    assert abs(measure_interval(out_path, 0.0, 8.0).lufs - (-16.0)) < 1.0
