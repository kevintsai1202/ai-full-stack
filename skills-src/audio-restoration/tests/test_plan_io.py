"""plan_io 模組測試：plan.json 的結構與往返一致性、report.json v2 結構。"""
import json
from pathlib import Path

from ar.plan_io import PLAN_SCHEMA_VERSION, load_plan, write_plan, write_report
from ar.diagnose import ZoneDiagnosis
from ar.probe import MediaSpec
from ar.segments import Classification, Utterance
from ar.silence import Interval


def _spec(tmp_path: Path) -> MediaSpec:
    """測試用媒體規格。"""
    return MediaSpec(path=tmp_path / "in.wav", is_video=False, duration=8.0,
                     sample_rate=48000, channels=1, audio_codec="pcm_s16le")


def _diagnosis() -> ZoneDiagnosis:
    """測試用診斷結果。"""
    return ZoneDiagnosis(
        zone_index=0, start=0.0, end=8.0, noise_rms_db=-55.0, speech_rms_db=-20.0,
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


def _classification() -> Classification:
    """兩句、兩噪音窗、一個非語音事件的分類結果。"""
    return Classification(
        utterances=[Utterance(index=0, start=2.0, end=4.0),
                    Utterance(index=1, start=5.0, end=7.0)],
        noise_windows=[Interval(start=0.2, end=1.8), Interval(start=4.1, end=4.9)],
        nonspeech_events=[Interval(start=7.2, end=7.5)],
    )


def _write_report_v2(tmp_path: Path) -> dict:
    """以 v2 介面寫出 report.json 並讀回。"""
    path = write_report(
        tmp_path, _spec(tmp_path), _classification(), [_diagnosis()],
        utterance_rms=[-20.0, -32.0], utterance_peaks=[-10.0, -22.0],
        noise_window_rms=[-54.0, -53.0], overall=(-21.5, -9.8),
    )
    return json.loads(path.read_text(encoding="utf-8"))


def test_report_v2_utterances_have_rms_without_lufs(tmp_path: Path):
    """utterances 每項須有 rms_db/peak_db 且不得有 lufs——逐句 LUFS 已不量測，
    留著會是假資料。"""
    report = _write_report_v2(tmp_path)
    assert report["schema_version"] == PLAN_SCHEMA_VERSION
    for item, rms in zip(report["utterances"], [-20.0, -32.0]):
        assert item["rms_db"] == rms
        assert "peak_db" in item
        assert "lufs" not in item


def test_report_v2_noise_windows_carry_rms(tmp_path: Path):
    """noise_windows 逐窗須記錄 rms_db，供修復後驗證直接讀取 before 端底噪。"""
    report = _write_report_v2(tmp_path)
    assert [w["rms_db"] for w in report["noise_windows"]] == [-54.0, -53.0]


def test_report_v2_has_overall_block(tmp_path: Path):
    """頂層 overall 須含全檔 integrated_lufs 與 true_peak_db。"""
    report = _write_report_v2(tmp_path)
    assert report["overall"] == {"integrated_lufs": -21.5, "true_peak_db": -9.8}
