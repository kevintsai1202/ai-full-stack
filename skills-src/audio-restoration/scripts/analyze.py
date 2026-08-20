"""音質體檢 CLI：產出 report.json 與 plan.json，不修改任何音檔。"""
import argparse
import sys
from pathlib import Path
from statistics import median

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
        raise SystemExit(
            "找不到可用的噪音採樣窗，無法建立降噪基準。\n"
            "可能原因與處置：\n"
            "  1. 整段幾乎都有人聲（講者沒有停頓）→ 手動指定一段確定無人聲的區間\n"
            "  2. ASR 斷句過密，詞間 gap 都不足 0.6 秒 → 檢查 timeline.json 的詞級時間戳\n"
            "  3. 底噪偏高使 silencedetect 判不出靜音 → 調高 detect_silence 的 noise_db 門檻"
        )

    fingerprints = [
        spectral_fingerprint(read_samples(args.input, w.start, w.end), 16000)
        for w in classification.noise_windows
    ]
    zones = detect_zones(fingerprints, classification.noise_windows, spec.duration)

    noise_stats = measure_intervals(args.input, classification.noise_windows)
    utterance_stats = measure_utterances(args.input, classification.utterances)

    diagnoses = []
    zone_noise_floors: list[float] = []  # 各 zone 的底噪 RMS（dB），供 afftdn 使用
    for zone in zones:
        if not zone.noise_window_indices:
            # 不可退回別區的噪音窗：那會讓這一區用錯誤的降噪基準，且錯得無聲無息。
            # detect_zones 的切點取自相鄰窗的中點，每個 zone 理論上必含至少一個窗，
            # 走到這裡代表分區結果異常，應該停下來而不是猜一個。
            raise SystemExit(
                f"Zone {zone.index}（{zone.start:.1f}s–{zone.end:.1f}s）沒有任何噪音採樣窗，"
                "無法為此區建立降噪基準。這通常代表分區偵測異常，"
                "請檢查 report.json 的 zones 與 noise_windows 是否對得上。"
            )
        # 底噪取該區各採樣窗的中位數：單一異常安靜的窗（例如空調剛好停機）
        # 會讓 min 低估底噪、使 SNR 被高估而降噪不足；中位數對離群值穩健。
        zone_noise_lufs = median(
            [noise_stats[i].lufs for i in zone.noise_window_indices]
        )
        # 人聲響度取該區各語句的中位數，而非整段區間的響度。
        # 整段含靜音，會把人聲位準拉低，讓 SNR 被低估、降噪被拉得過強。
        zone_utterance_lufs = [
            stat.lufs
            for utterance, stat in zip(classification.utterances, utterance_stats)
            if zone.start <= (utterance.start + utterance.end) / 2.0 < zone.end
        ]
        if not zone_utterance_lufs:
            raise SystemExit(
                f"Zone {zone.index}（{zone.start:.1f}s–{zone.end:.1f}s）沒有任何語句，"
                "無法判斷人聲響度。請檢查 ASR 時間軸是否涵蓋整支檔案。"
            )
        zone_speech_lufs = median(zone_utterance_lufs)
        # afftdn 的 nf 語意是訊號位準，用 RMS 而非 LUFS
        zone_noise_floors.append(
            median([noise_stats[i].rms_db for i in zone.noise_window_indices])
        )
        # 落在此 zone 內的語句結束時刻，供殘響量測使用
        zone_ends = [
            seg.end for seg in timeline.segments
            if zone.start <= seg.end < zone.end
        ]
        diagnoses.append(
            diagnose_zone(args.input, zone, zone_noise_lufs, zone_speech_lufs, zone_ends)
        )

    zone_gains = compute_zone_gains(diagnoses, WORKING_LUFS)
    utterance_gains = compute_utterance_gains(
        classification.utterances, utterance_stats, zones, zone_gains, WORKING_LUFS
    )

    write_report(args.work_dir, spec, classification, diagnoses, utterance_stats)
    plan_path = write_plan(
        args.work_dir, spec, diagnoses, utterance_gains, zone_gains, args.target,
        noise_floors=zone_noise_floors,
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
