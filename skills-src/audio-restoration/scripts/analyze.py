"""音質體檢 CLI：產出 report.json 與 plan.json，不修改任何音檔。"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

# Windows 主控台預設編碼（如 cp950）無法輸出中文警示符號等字元，
# 強制改用 UTF-8 以避免摘要輸出時因編碼不符而中斷整個流程。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

from ar.diagnose import diagnose_zone
from ar.fingerprint import detect_zones, read_samples, spectral_fingerprint
from ar.gain import compute_utterance_gains, compute_zone_gains
from ar.measure import measure_interval, measure_intervals, measure_utterances
from ar.plan_io import write_plan, write_report
from ar.probe import probe
from ar.segments import classify
from ar.silence import detect_silence
from ar.timeline import ensure_timeline

DEFAULT_TARGET_LUFS = -16.0
WORKING_LUFS = -20.0  # 拉平階段的共同工作位準


def main() -> None:
    """解析參數並執行體檢流程。"""
    parser = argparse.ArgumentParser(description="講課音軌音質體檢")
    parser.add_argument("--input", required=True, type=Path, help="音檔或影片")
    parser.add_argument("--work-dir", required=True, type=Path, help="產出目錄")
    parser.add_argument("--target", type=float, default=DEFAULT_TARGET_LUFS,
                        help="最終響度目標（LUFS），預設 -16")
    args = parser.parse_args()

    spec = probe(args.input)
    timeline = ensure_timeline(spec, args.work_dir)
    silences = detect_silence(args.input, spec.duration)
    classification = classify(timeline, silences, spec.duration)

    if not classification.noise_windows:
        raise SystemExit("找不到可用的噪音採樣窗（可能整段都有人聲），無法建立降噪基準")

    fingerprints = [
        spectral_fingerprint(read_samples(args.input, w.start, w.end), 16000)
        for w in classification.noise_windows
    ]
    zones = detect_zones(fingerprints, classification.noise_windows, spec.duration)

    noise_stats = measure_intervals(args.input, classification.noise_windows)
    utterance_stats = measure_utterances(args.input, classification.utterances)

    diagnoses = []
    for zone in zones:
        indices = zone.noise_window_indices or [0]
        zone_noise = min((noise_stats[i] for i in indices), key=lambda s: s.lufs)
        zone_speech = measure_interval(args.input, zone.start, min(zone.end, zone.start + 60.0))
        # 落在此 zone 內的語句結束時刻，供殘響量測使用
        zone_ends = [
            seg.end for seg in timeline.segments
            if zone.start <= seg.end < zone.end
        ]
        diagnoses.append(
            diagnose_zone(args.input, zone, zone_noise, zone_speech, zone_ends)
        )

    zone_gains = compute_zone_gains(diagnoses, WORKING_LUFS)
    utterance_gains = compute_utterance_gains(
        classification.utterances, utterance_stats, zones, zone_gains, WORKING_LUFS
    )

    write_report(args.work_dir, spec, classification, diagnoses, utterance_stats)
    plan_path = write_plan(
        args.work_dir, spec, diagnoses, utterance_gains, zone_gains, args.target,
        nonspeech_events=[(e.start, e.end) for e in classification.nonspeech_events],
    )
    _print_summary(diagnoses, classification, plan_path)


def _print_summary(diagnoses, classification, plan_path: Path) -> None:
    """印出人類可讀的體檢摘要。"""
    print(f"語句 {len(classification.utterances)} 句／"
          f"噪音採樣窗 {len(classification.noise_windows)} 個／"
          f"非語音雜訊 {len(classification.nonspeech_events)} 段／"
          f"錄音條件分區 {len(diagnoses)} 區")
    for diagnosis in diagnoses:
        print(f"\n[Zone {diagnosis.zone_index}] "
              f"{diagnosis.start:.1f}s – {diagnosis.end:.1f}s")
        print(f"  SNR {diagnosis.snr_db:.1f} dB／降噪 {diagnosis.denoise_db} dB"
              f"／highpass {'是' if diagnosis.needs_highpass else '否'}"
              f"／deesser {'是' if diagnosis.needs_deesser else '否'}")
        for issue in diagnosis.issues:
            print(f"  ⚠ {issue}")
    print(f"\n處理計畫已寫入：{plan_path}")
    print("請檢視並視需要手動調整後，執行 restore.py --plan 進行修復。")


if __name__ == "__main__":
    main()
