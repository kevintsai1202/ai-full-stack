"""restore.py 端到端測試：閘門、輸出、驗證。"""
import json
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def _write_plan(path: Path, input_path: Path) -> Path:
    """寫出可執行的處理計畫。"""
    plan = {
        "schema_version": 2,  # 須與 PLAN_SCHEMA_VERSION 一致，否則 load_plan 會拒收
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


def _write_report(path: Path) -> Path:
    """寫出 v2 report.json：restore 的 before 指標直接讀此檔，不重量原始檔。

    數值對應 synth_wav 的合成配置（底噪約 -54 dBFS、兩句相差約 12dB）。
    utterance_rms 取 [-9, -21] 讓 before SNR = median(-15) − (-54) = 39 dB
    ≥ CLEAN_SNR_DB，走「素材本已乾淨」分支——合成正弦波上 afftdn 的
    實際壓制量不可控，配對式判定在此不穩定，乾淨分支才是可預期的路徑。
    """
    report = {
        "schema_version": 2,
        "overall": {"integrated_lufs": -20.0, "true_peak_db": -5.0},
        "utterances": [
            {"index": 0, "start": 0.0, "end": 4.5, "rms_db": -9.0, "peak_db": -6.0},
            {"index": 1, "start": 4.5, "end": 8.0, "rms_db": -21.0, "peak_db": -18.0},
        ],
        "noise_windows": [
            {"start": 0.2, "end": 1.8, "rms_db": -54.0},
            {"start": 7.1, "end": 7.9, "rms_db": -54.0},
        ],
        "nonspeech_events": [],
    }
    path.write_text(json.dumps(report, ensure_ascii=False), encoding="utf-8")
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


def test_restore_refuses_without_report(synth_wav: Path, tmp_path: Path):
    """plan 存在但 report.json 缺席時必須明確報錯，不得退回重量原始檔。

    before 指標的唯一來源是 analyze 寫進 report.json 的量測值；缺 report
    也代表噪音採樣窗不明，降噪驗證無從進行。
    """
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(tmp_path / "o.wav"),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode != 0
    assert "report.json" in (result.stderr + result.stdout)
    assert "analyze" in (result.stderr + result.stdout)


def test_restore_refuses_when_plan_utterance_count_mismatches_report(
        synth_wav: Path, tmp_path: Path):
    """plan.json 的 utterances 筆數與 report.json 不一致時須明確報錯。

    plan 鼓勵手動編輯，但增刪筆數會讓 before/after 配對迴圈 IndexError
    或靜默錯位——必須在進 verify 前擋下，訊息要說明「可調增益與邊界、
    不可增刪筆數」，不得噴 traceback。
    """
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    _write_report(tmp_path / "report.json")
    # 手動刪掉一句：模擬使用者編輯 plan.json 增刪筆數的誤用
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    plan["utterances"] = plan["utterances"][:1]
    plan_path.write_text(json.dumps(plan, ensure_ascii=False), encoding="utf-8")

    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(tmp_path / "o.wav"),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode != 0
    combined = result.stderr + result.stdout
    assert "不一致" in combined
    assert "analyze" in combined
    # 必須是乾淨的 SystemExit 訊息，不是裸 IndexError traceback
    assert "IndexError" not in combined
    assert "Traceback" not in combined


def test_restore_produces_output_and_verification(synth_wav: Path, tmp_path: Path):
    """正常執行應產出修復檔、ASR WAV、AB 片段與 verify.json。"""
    plan_path = _write_plan(tmp_path / "plan.json", synth_wav)
    _write_report(tmp_path / "report.json")
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
    _write_report(tmp_path / "report.json")
    out_path = tmp_path / "restored.wav"
    subprocess.run(
        [sys.executable, str(SCRIPTS / "restore.py"),
         "--plan", str(plan_path), "--out", str(out_path),
         "--work-dir", str(tmp_path / "work")],
        capture_output=True, text=True, check=True, encoding="utf-8",
    )
    assert abs(measure_interval(out_path, 0.0, 8.0).lufs - (-16.0)) < 1.0
