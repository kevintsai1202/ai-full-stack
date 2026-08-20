"""master.py 端到端測試：美化、響度收斂、AB 試聽、錯誤處置。"""
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def test_master_produces_normalized_output(synth_wav: Path, tmp_path: Path):
    """正常執行應產出美化檔且響度收斂、峰值受控、附 AB 試聽片段。

    驗收與修復階段同一把尺：integrated 在 target ±0.5、true peak ≤ −1.5。
    美化鏈的 EQ／壓縮會改變響度，若輸出仍收斂代表 apply_loudnorm 的
    迭代收斂在美化後的訊號上同樣成立。
    """
    from ar.bulk import measure_overall
    out_path = tmp_path / "mastered.wav"
    work_dir = tmp_path / "work"
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "master.py"),
         "--input", str(synth_wav), "--out", str(out_path),
         "--preset", "podcast", "--work-dir", str(work_dir)],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr + result.stdout
    assert out_path.exists()
    assert (work_dir / "master-preview-ab.wav").exists()
    lufs, true_peak = measure_overall(out_path)
    assert abs(lufs - (-16.0)) <= 0.5
    assert true_peak <= -1.5


def test_master_rejects_unknown_preset(synth_wav: Path, tmp_path: Path):
    """未知 preset 須非零退出，且訊息列出可用選項供使用者照抄。"""
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "master.py"),
         "--input", str(synth_wav), "--out", str(tmp_path / "o.wav"),
         "--preset", "loud"],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode != 0
    combined = result.stderr + result.stdout
    for name in ("conservative", "podcast", "rich"):
        assert name in combined


def test_master_rejects_missing_input(tmp_path: Path):
    """輸入檔不存在時須明確報錯，不得噴 traceback。"""
    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "master.py"),
         "--input", str(tmp_path / "missing.mp4"),
         "--out", str(tmp_path / "o.wav")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode != 0
    combined = result.stderr + result.stdout
    assert "找不到" in combined
    assert "Traceback" not in combined
