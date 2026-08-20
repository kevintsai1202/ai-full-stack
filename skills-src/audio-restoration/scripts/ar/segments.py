"""把時間軸切成三類區間：語句、噪音採樣窗、非語音雜訊事件。

三類的用途各不相同：
  語句     → 逐句增益補償的作用範圍
  噪音窗   → 降噪基準與頻譜指紋的採樣來源（必須完全無人聲）
  雜訊事件 → 咳嗽／翻頁／椅子聲，要壓低而非拉平
"""
from dataclasses import dataclass

from .silence import Interval
from .timeline import Timeline

MIN_NOISE_GAP = 0.6  # 噪音採樣窗的最小長度（秒）


@dataclass
class Utterance:
    """一個語句的增益作用範圍（邊界已外擴至靜音中點）。"""
    index: int
    start: float
    end: float


@dataclass
class Classification:
    """三分類結果。"""
    utterances: list[Utterance]
    noise_windows: list[Interval]
    nonspeech_events: list[Interval]


def classify(tl: Timeline, silences: list[Interval], total_duration: float,
             min_noise_gap: float = MIN_NOISE_GAP) -> Classification:
    """執行三分類。"""
    return Classification(
        utterances=_build_utterances(tl, total_duration),
        noise_windows=_build_noise_windows(tl, silences, total_duration, min_noise_gap),
        nonspeech_events=_build_nonspeech_events(tl, silences, total_duration),
    )


def _build_utterances(tl: Timeline, total_duration: float) -> list[Utterance]:
    """把 ASR 語句邊界外擴到相鄰語句間 gap 的中點。

    增益切換若發生在人聲上會產生可聽的爆點，故一律移到完全無聲處。
    首句起點與末句終點延伸到檔案兩端，確保增益覆蓋無空隙。
    """
    spans = [(seg.start, seg.end) for seg in tl.segments]
    if not spans:
        return [Utterance(index=0, start=0.0, end=total_duration)]

    utterances: list[Utterance] = []
    for index, (start, end) in enumerate(spans):
        left = 0.0 if index == 0 else (spans[index - 1][1] + start) / 2.0
        right = total_duration if index == len(spans) - 1 else (end + spans[index + 1][0]) / 2.0
        utterances.append(Utterance(index=index, start=left, end=right))
    return utterances


def _build_noise_windows(tl: Timeline, silences: list[Interval], total_duration: float,
                         min_noise_gap: float) -> list[Interval]:
    """取出「詞間 gap 夠長」且「silencedetect 也認為靜音」的雙重確認區間。

    採樣窗若混入人聲，降噪會把人聲的頻譜特徵當成噪音消掉，這是修音最常見的
    災難，故採雙重確認而非單一判準。
    """
    gaps = _word_gaps(tl, total_duration)
    windows: list[Interval] = []
    for gap in gaps:
        for silence in silences:
            overlap_start = max(gap.start, silence.start)
            overlap_end = min(gap.end, silence.end)
            if overlap_end - overlap_start >= min_noise_gap:
                windows.append(Interval(overlap_start, overlap_end))
    return windows


def _word_gaps(tl: Timeline, total_duration: float) -> list[Interval]:
    """算出所有詞與詞之間的空隙（含檔頭與檔尾）。"""
    words = tl.all_words()
    if not words:
        return [Interval(0.0, total_duration)]
    gaps = [Interval(0.0, words[0].start)]
    for previous, current in zip(words, words[1:]):
        if current.start > previous.end:
            gaps.append(Interval(previous.end, current.start))
    gaps.append(Interval(words[-1].end, total_duration))
    return [gap for gap in gaps if gap.duration > 0]


def _build_nonspeech_events(tl: Timeline, silences: list[Interval],
                            total_duration: float) -> list[Interval]:
    """找出 silencedetect 判定有聲、但 ASR 完全沒詞的區段。"""
    sounding = _invert(silences, total_duration)
    words = tl.all_words()
    events: list[Interval] = []
    for span in sounding:
        has_word = any(word.start < span.end and word.end > span.start for word in words)
        if not has_word:
            events.append(span)
    return events


def _invert(intervals: list[Interval], total_duration: float) -> list[Interval]:
    """把靜音區間清單反轉成有聲區間清單。"""
    ordered = sorted(intervals, key=lambda i: i.start)
    result: list[Interval] = []
    cursor = 0.0
    for interval in ordered:
        if interval.start > cursor:
            result.append(Interval(cursor, interval.start))
        cursor = max(cursor, interval.end)
    if cursor < total_duration:
        result.append(Interval(cursor, total_duration))
    return result
