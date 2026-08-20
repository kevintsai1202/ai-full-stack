"""preview 模組測試：AB 試聽片段挑選與生成。"""
from pathlib import Path

from ar.preview import build_ab_preview, pick_preview_start
from ar.probe import probe


def _plan(denoise_levels: list[int]) -> dict:
    """建立指定各區降噪強度的計畫。"""
    return {
        "zones": [
            {"index": i, "start": i * 3.0, "end": (i + 1) * 3.0, "denoise_db": level}
            for i, level in enumerate(denoise_levels)
        ]
    }


def test_pick_preview_start_returns_noisiest_zone_start():
    """應挑出降噪最重（即 SNR 最差）的區段起點，那裡最能看出修復效果。"""
    assert pick_preview_start(_plan([12, 24, 18])) == 3.0


def test_pick_preview_start_handles_uniform_plan():
    """各區強度相同時取第一區，不得因無最大值而失敗。"""
    assert pick_preview_start(_plan([12, 12, 12])) == 0.0


def test_ab_preview_is_concatenation_of_both(synth_wav: Path, tmp_path: Path):
    """AB 片段長度應為兩段之和（前 2 秒 + 後 2 秒 = 4 秒）。"""
    out = build_ab_preview(synth_wav, synth_wav, start=2.0,
                           out_path=tmp_path / "ab.wav", duration=2.0)
    assert abs(probe(out).duration - 4.0) < 0.2
