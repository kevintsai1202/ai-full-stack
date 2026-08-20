"""timeline 模組測試：載入、時長驗證、詞彙攤平。"""
import json
from pathlib import Path

from ar.timeline import load_timeline, timeline_matches


def _write_timeline(path: Path, duration: float) -> Path:
    """寫出一份與 faster-whisper 相同結構的 timeline.json 供測試使用。"""
    payload = {
        "info": {"language": "zh", "duration": duration, "model": "small"},
        "segments": [
            {
                "id": 1, "start": 2.0, "end": 4.0, "text": "語句A",
                "no_speech_prob": 0.05,
                "words": [
                    {"start": 2.0, "end": 3.0, "word": "語", "probability": 0.9},
                    {"start": 3.0, "end": 4.0, "word": "句A", "probability": 0.9},
                ],
            },
            {
                "id": 2, "start": 5.0, "end": 7.0, "text": "語句B",
                "no_speech_prob": 0.05,
                "words": [
                    {"start": 5.0, "end": 6.0, "word": "語", "probability": 0.8},
                    {"start": 6.0, "end": 7.0, "word": "句B", "probability": 0.8},
                ],
            },
        ],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return path


def test_load_timeline_parses_segments_and_words(tmp_path: Path):
    """載入後應保留兩個語句、共四個詞，且時間戳不變。"""
    tl = load_timeline(_write_timeline(tmp_path / "timeline.json", 8.0))
    assert len(tl.segments) == 2
    assert len(tl.all_words()) == 4
    assert tl.segments[0].words[0].start == 2.0
    assert tl.segments[1].end == 7.0


def test_timeline_matches_within_tolerance(tmp_path: Path):
    """時長誤差在 0.5 秒容差內視為匹配。"""
    tl = load_timeline(_write_timeline(tmp_path / "a.json", 8.0))
    assert timeline_matches(tl, 8.3) is True


def test_timeline_rejected_when_duration_drifts(tmp_path: Path):
    """時長誤差超過容差視為不匹配（例如音檔被剪過），須重跑 ASR。"""
    tl = load_timeline(_write_timeline(tmp_path / "b.json", 8.0))
    assert timeline_matches(tl, 9.2) is False
