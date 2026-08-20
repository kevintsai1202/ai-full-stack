"""probe 模組測試：驗證能正確讀出合成音檔的規格。"""
from pathlib import Path

from ar.probe import probe


def test_probe_reads_synth_wav_spec(synth_wav: Path):
    """合成音檔應被判定為非影片，且時長／取樣率／聲道數正確。"""
    spec = probe(synth_wav)
    assert spec.is_video is False
    assert spec.sample_rate == 48000
    assert spec.channels == 1
    assert abs(spec.duration - 8.0) < 0.05
    assert spec.audio_codec == "pcm_s16le"
