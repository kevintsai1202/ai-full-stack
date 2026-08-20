"""report.json 與 plan.json 的讀寫。

plan.json 是使用者可手動編輯的處理計畫。restore.py 只認 plan.json，
不會自行推測參數 —— 這是「先體檢、再提案、確認後執行」的強制實現。
"""
import json
from dataclasses import asdict
from pathlib import Path

from .diagnose import ZoneDiagnosis
from .probe import MediaSpec

PLAN_SCHEMA_VERSION = 1  # plan.json 結構版本，改變欄位語意時必須遞增


def write_report(work_dir: Path, spec: MediaSpec, classification, diagnoses,
                 utterance_stats) -> Path:
    """輸出完整診斷結果（供人工檢閱與修復後比對）。"""
    work_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "schema_version": PLAN_SCHEMA_VERSION,
        "input": str(spec.path),
        "media": {
            "is_video": spec.is_video, "duration": spec.duration,
            "sample_rate": spec.sample_rate, "channels": spec.channels,
            "audio_codec": spec.audio_codec,
        },
        "counts": {
            "utterances": len(classification.utterances),
            "noise_windows": len(classification.noise_windows),
            "nonspeech_events": len(classification.nonspeech_events),
            "zones": len(diagnoses),
        },
        "zones": [asdict(d) for d in diagnoses],
        "utterances": [
            {"index": u.index, "start": u.start, "end": u.end,
             "lufs": s.lufs, "rms_db": s.rms_db, "peak_db": s.peak_db}
            for u, s in zip(classification.utterances, utterance_stats)
        ],
        "nonspeech_events": [
            {"start": e.start, "end": e.end} for e in classification.nonspeech_events
        ],
        # 噪音採樣窗須寫入報告：修復後驗證底噪降幅時，只有這些區間是
        # 經雙重確認、確定不含人聲的量測位置
        "noise_windows": [
            {"start": w.start, "end": w.end} for w in classification.noise_windows
        ],
    }
    path = work_dir / "report.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def write_plan(work_dir: Path, spec: MediaSpec, diagnoses: list[ZoneDiagnosis],
               utterance_gains: list[tuple[float, float, float]],
               zone_gains: list[float], target_lufs: float,
               noise_floors: list[float],
               nonspeech_events: list[tuple[float, float]] | None = None) -> Path:
    """輸出處理計畫。

    utterance_gains 每項為 (start, end, gain_db)。
    noise_floors 每項為該 zone 的底噪 RMS（dB），供 afftdn 的 nf 使用 ——
    用 RMS 而非 LUFS，因為 afftdn 的 nf 語意是訊號位準而非感知響度。
    nonspeech_events 每項為 (start, end)，這些區間會被額外壓低。
    """
    work_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "schema_version": PLAN_SCHEMA_VERSION,
        "說明": "此檔為處理計畫，可手動編輯後再交給 restore.py。"
                "zones[].gain_db 為區級增益、utterances[].gain_db 為句級增益，"
                "兩者相加即該句實際增益。修改 zone 邊界請同步調整 start/end。",
        "input": str(spec.path),
        "is_video": spec.is_video,
        "target_lufs": target_lufs,
        "zones": [
            {
                "index": d.zone_index, "start": d.start, "end": d.end,
                "gain_db": zone_gains[d.zone_index],
                "denoise_db": d.denoise_db,
                # afftdn 的底噪起始估計值，取自該區噪音採樣窗的實測 RMS
                "noise_floor_db": noise_floors[d.zone_index],
                "needs_highpass": d.needs_highpass,
                "needs_deesser": d.needs_deesser,
                "needs_ai_rescue": d.needs_ai_rescue,
                "issues": d.issues,
            }
            for d in diagnoses
        ],
        "utterances": [
            {"start": start, "end": end, "gain_db": gain}
            for start, end, gain in utterance_gains
        ],
        "nonspeech_events": [
            {"start": start, "end": end} for start, end in (nonspeech_events or [])
        ],
    }
    path = work_dir / "plan.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def load_plan(path: Path) -> dict:
    """讀取 plan.json 並驗證 schema 版本。"""
    plan = json.loads(path.read_text(encoding="utf-8"))
    if plan.get("schema_version") != PLAN_SCHEMA_VERSION:
        raise ValueError(
            f"plan.json schema 版本不符（檔案 {plan.get('schema_version')}，"
            f"程式 {PLAN_SCHEMA_VERSION}），請重新執行 analyze.py"
        )
    return plan
