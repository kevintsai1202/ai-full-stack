"""silence 模組測試：解析 ffmpeg silencedetect 輸出。"""
from pathlib import Path

from ar.silence import detect_silence, parse_silencedetect

SAMPLE_STDERR = """
[silencedetect @ 000001] silence_start: 0
[silencedetect @ 000001] silence_end: 2.001 | silence_duration: 2.001
[silencedetect @ 000001] silence_start: 4.002
[silencedetect @ 000001] silence_end: 5.003 | silence_duration: 1.001
[silencedetect @ 000001] silence_start: 7.004
"""


def test_parse_silencedetect_extracts_intervals():
    """三段靜音都要解析出來。"""
    intervals = parse_silencedetect(SAMPLE_STDERR, total_duration=8.0)
    assert len(intervals) == 3
    assert intervals[0].start == 0.0
    assert abs(intervals[0].end - 2.001) < 1e-6


def test_parse_silencedetect_closes_trailing_interval():
    """最後一段 silence_start 沒有對應的 silence_end 時，用總時長收尾。"""
    intervals = parse_silencedetect(SAMPLE_STDERR, total_duration=8.0)
    assert abs(intervals[-1].end - 8.0) < 1e-6


def test_detect_silence_on_synth_wav(synth_wav: Path):
    """合成音檔的三段靜音（0-2、4-5、7-8 秒）應被偵測到。"""
    intervals = detect_silence(synth_wav, total_duration=8.0, noise_db=-40.0, min_dur=0.3)
    assert len(intervals) == 3
    assert abs(intervals[1].start - 4.0) < 0.15
