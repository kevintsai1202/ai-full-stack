"""batch 模組測試：批次體檢與總表產出。"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent / "scripts"


def test_batch_analyzes_every_media_file(synth_wav: Path, tmp_path: Path):
    """目錄下兩支音檔都應各自產出 report.json，並匯總成一份總表。"""
    media_dir = tmp_path / "media"
    media_dir.mkdir()
    shutil.copy(synth_wav, media_dir / "a.wav")
    shutil.copy(synth_wav, media_dir / "b.wav")
    for name in ("a", "b"):
        work = tmp_path / "out" / name
        work.mkdir(parents=True)
        payload = {
            "info": {"language": "zh", "duration": 8.0, "model": "small"},
            "segments": [
                {"id": 1, "start": 2.0, "end": 4.0, "text": "A", "no_speech_prob": 0.0,
                 "words": [{"start": 2.0, "end": 4.0, "word": "A", "probability": 0.9}]},
                {"id": 2, "start": 5.0, "end": 7.0, "text": "B", "no_speech_prob": 0.0,
                 "words": [{"start": 5.0, "end": 7.0, "word": "B", "probability": 0.9}]},
            ],
        }
        (work / "timeline.json").write_text(json.dumps(payload), encoding="utf-8")

    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "batch.py"),
         "--input-dir", str(media_dir), "--out-dir", str(tmp_path / "out")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    assert (tmp_path / "out" / "a" / "report.json").exists()
    assert (tmp_path / "out" / "b" / "report.json").exists()
    summary = json.loads((tmp_path / "out" / "summary.json").read_text(encoding="utf-8"))
    assert len(summary["files"]) == 2


def test_batch_skips_non_media_files(synth_wav: Path, tmp_path: Path):
    """非音影格式（.txt）不得被當成素材處理。"""
    media_dir = tmp_path / "media"
    media_dir.mkdir()
    shutil.copy(synth_wav, media_dir / "a.wav")
    (media_dir / "note.txt").write_text("不是音檔", encoding="utf-8")
    work = tmp_path / "out" / "a"
    work.mkdir(parents=True)
    payload = {
        "info": {"language": "zh", "duration": 8.0, "model": "small"},
        "segments": [
            {"id": 1, "start": 2.0, "end": 4.0, "text": "A", "no_speech_prob": 0.0,
             "words": [{"start": 2.0, "end": 4.0, "word": "A", "probability": 0.9}]},
            {"id": 2, "start": 5.0, "end": 7.0, "text": "B", "no_speech_prob": 0.0,
             "words": [{"start": 5.0, "end": 7.0, "word": "B", "probability": 0.9}]},
        ],
    }
    (work / "timeline.json").write_text(json.dumps(payload), encoding="utf-8")

    subprocess.run(
        [sys.executable, str(SCRIPTS / "batch.py"),
         "--input-dir", str(media_dir), "--out-dir", str(tmp_path / "out")],
        capture_output=True, text=True, encoding="utf-8", check=True,
    )
    summary = json.loads((tmp_path / "out" / "summary.json").read_text(encoding="utf-8"))
    assert len(summary["files"]) == 1


def test_batch_restore_skips_files_without_plan(synth_wav: Path, tmp_path: Path):
    """--restore 只處理已有 plan.json 的子目錄，沒體檢過的必須跳過並記錄。

    這是體檢閘門在批次模式下的延伸：批次不得成為繞過人工確認的後門。
    """
    import shutil
    media_dir = tmp_path / "media"
    media_dir.mkdir()
    shutil.copy(synth_wav, media_dir / "a.wav")
    (tmp_path / "out" / "a").mkdir(parents=True)  # 有目錄但沒有 plan.json

    result = subprocess.run(
        [sys.executable, str(SCRIPTS / "batch.py"), "--restore",
         "--input-dir", str(media_dir), "--out-dir", str(tmp_path / "out")],
        capture_output=True, text=True, encoding="utf-8",
    )
    assert result.returncode == 0, result.stderr
    summary = json.loads(
        (tmp_path / "out" / "restore-summary.json").read_text(encoding="utf-8")
    )
    assert summary["files"][0]["skipped"] is True
    assert "plan" in summary["files"][0]["reason"]
