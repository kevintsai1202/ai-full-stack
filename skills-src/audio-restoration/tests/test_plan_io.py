"""plan_io 模組測試：plan.json 的結構與往返一致性。"""
from pathlib import Path

from ar.plan_io import PLAN_SCHEMA_VERSION, load_plan, write_plan
from ar.diagnose import ZoneDiagnosis
from ar.probe import MediaSpec


def _spec(tmp_path: Path) -> MediaSpec:
    """測試用媒體規格。"""
    return MediaSpec(path=tmp_path / "in.wav", is_video=False, duration=8.0,
                     sample_rate=48000, channels=1, audio_codec="pcm_s16le")


def _diagnosis() -> ZoneDiagnosis:
    """測試用診斷結果。"""
    return ZoneDiagnosis(
        zone_index=0, start=0.0, end=8.0, noise_lufs=-55.0, speech_lufs=-20.0,
        snr_db=35.0, sibilance_ratio=0.05, rumble_ratio=0.02, clipped_ratio=0.0,
        reverb_slope=-60.0, denoise_db=12, needs_deesser=False,
        needs_highpass=False, needs_ai_rescue=False, issues=[],
    )


def test_plan_roundtrip_preserves_values(tmp_path: Path):
    """寫出再讀回的 plan 應保留 zone 決策與逐句增益。"""
    path = write_plan(tmp_path, _spec(tmp_path), [_diagnosis()],
                      utterance_gains=[(0.0, 4.5, 2.0), (4.5, 8.0, -1.5)],
                      zone_gains=[3.0], target_lufs=-16.0, noise_floors=[-48.0])
    plan = load_plan(path)
    assert plan["schema_version"] == PLAN_SCHEMA_VERSION
    assert plan["target_lufs"] == -16.0
    assert plan["zones"][0]["denoise_db"] == 12
    assert plan["zones"][0]["gain_db"] == 3.0
    assert plan["zones"][0]["noise_floor_db"] == -48.0
    assert len(plan["utterances"]) == 2
    assert plan["utterances"][1]["gain_db"] == -1.5


def test_plan_is_human_editable_json(tmp_path: Path):
    """plan.json 須為縮排 JSON 且含中文說明欄位，使用者要能手動改。"""
    path = write_plan(tmp_path, _spec(tmp_path), [_diagnosis()],
                      utterance_gains=[(0.0, 8.0, 0.0)], zone_gains=[0.0],
                      target_lufs=-16.0, noise_floors=[-48.0])
    text = path.read_text(encoding="utf-8")
    assert "\n  " in text
    assert "說明" in text
