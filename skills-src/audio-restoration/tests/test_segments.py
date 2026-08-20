"""segments 模組測試：三分類與語句邊界外擴。"""
from ar.segments import classify
from ar.silence import Interval
from ar.timeline import Segment, Timeline, Word


def _timeline() -> Timeline:
    """建立與 conftest 合成音檔對應的時間軸：2-4 秒與 5-7 秒各一句。"""
    return Timeline(
        duration=8.0,
        segments=[
            Segment(2.0, 4.0, "語句A", [Word(2.0, 3.0, "語"), Word(3.0, 4.0, "句A")]),
            Segment(5.0, 7.0, "語句B", [Word(5.0, 6.0, "語"), Word(6.0, 7.0, "句B")]),
        ],
    )


def _silences() -> list[Interval]:
    """對應的靜音區間。"""
    return [Interval(0.0, 2.0), Interval(4.0, 5.0), Interval(7.0, 8.0)]


def test_utterance_boundaries_land_at_gap_midpoint():
    """兩句之間的切換點應落在 4.0-5.0 的中點 4.5，而非緊貼語句邊界。"""
    result = classify(_timeline(), _silences(), total_duration=8.0)
    assert len(result.utterances) == 2
    assert abs(result.utterances[0].end - 4.5) < 1e-6
    assert abs(result.utterances[1].start - 4.5) < 1e-6


def test_first_and_last_utterance_span_whole_file():
    """首尾語句應延伸到檔案兩端，確保增益覆蓋整個時間軸不留空隙。"""
    result = classify(_timeline(), _silences(), total_duration=8.0)
    assert result.utterances[0].start == 0.0
    assert abs(result.utterances[-1].end - 8.0) < 1e-6


def test_noise_windows_require_min_gap_and_silence_agreement():
    """噪音採樣窗須同時滿足：詞間 gap >= 0.6 秒、且 silencedetect 也認為靜音。"""
    result = classify(_timeline(), _silences(), total_duration=8.0, min_noise_gap=0.6)
    assert len(result.noise_windows) == 3
    assert all(w.duration >= 0.6 for w in result.noise_windows)


def test_short_gap_is_not_used_as_noise_window():
    """詞間 gap 只有 0.3 秒時不得採為噪音窗，避免採到語音殘響尾巴。"""
    tl = Timeline(
        duration=8.0,
        segments=[
            Segment(2.0, 3.0, "A", [Word(2.0, 3.0, "A")]),
            Segment(3.3, 4.0, "B", [Word(3.3, 4.0, "B")]),
        ],
    )
    result = classify(tl, [Interval(3.0, 3.3)], total_duration=8.0, min_noise_gap=0.6)
    assert result.noise_windows == []


def test_nonspeech_event_detected_when_sound_without_words():
    """silencedetect 認為有聲、但 ASR 完全沒詞的區段 = 咳嗽／翻頁等雜訊。

    這類事件必須標出來，因為它們要被壓低而非拉平；誤當語句處理會把咳嗽聲
    放大到與人聲同等音量。
    """
    tl = Timeline(duration=8.0, segments=[
        Segment(2.0, 4.0, "A", [Word(2.0, 3.0, "語"), Word(3.0, 4.0, "A")]),
    ])
    # 5.0-5.4 秒有聲音（不在任何靜音區間內），但沒有任何 ASR 詞
    silences = [Interval(0.0, 2.0), Interval(4.0, 5.0), Interval(5.4, 8.0)]
    result = classify(tl, silences, total_duration=8.0)
    assert len(result.nonspeech_events) == 1
    assert abs(result.nonspeech_events[0].start - 5.0) < 1e-6
    assert abs(result.nonspeech_events[0].end - 5.4) < 1e-6
