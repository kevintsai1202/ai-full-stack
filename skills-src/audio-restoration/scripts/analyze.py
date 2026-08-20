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

from ar.bulk import load_audio, measure_overall, peak_db, rms_db, slice_samples
from ar.diagnose import diagnose_zone
from ar.fingerprint import detect_zones, spectral_fingerprint
from ar.gain import compute_utterance_gains, compute_zone_gains
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

    # 雙 buffer 各載入一次，之後所有區間量測都在 numpy 上切片完成，
    # 不再逐句／逐窗 spawn ffmpeg（舊路徑約 2,800 次行程，佔 90% 時間）。
    # - buf_native（原生率）：RMS／峰值量測。afftdn 的 nf 是絕對位準，
    #   16k 重採樣會截掉高頻噪音能量、讓嘶聲型底噪 RMS 偏低，必須原生率。
    # - buf_16k：指紋／診斷頻帶／殘響。分區門檻 ZONE_THRESHOLD 是在 16k
    #   頻帶分布下校準的，不可換率。
    # 註：舊的 measure_interval 對區間長度有 max(0.05, end-start) 下限，
    # bulk 切片採「照實切片」語意。真實句／窗都遠大於 0.05 秒，無實際影響。
    # 取樣率直接沿用開頭 probe 的結果，避免 load_audio 內部再 ffprobe 一次
    buf_native = load_audio(args.input, sample_rate=spec.sample_rate)
    buf_16k = load_audio(args.input, sample_rate=16000)

    fingerprints = [
        spectral_fingerprint(slice_samples(buf_16k, w.start, w.end), 16000)
        for w in classification.noise_windows
    ]
    zones = detect_zones(fingerprints, classification.noise_windows, spec.duration)

    # 逐窗／逐句 RMS 與峰值：全部由記憶體切片聚合。逐句 LUFS 不再量測
    # （句級增益改用 RMS，見 compute_utterance_gains），感知響度只在
    # 整檔層級量一次（measure_overall）。
    noise_window_rms = [
        rms_db(slice_samples(buf_native, w.start, w.end))
        for w in classification.noise_windows
    ]
    utterance_rms = [
        rms_db(slice_samples(buf_native, u.start, u.end))
        for u in classification.utterances
    ]
    utterance_peaks = [
        peak_db(slice_samples(buf_native, u.start, u.end))
        for u in classification.utterances
    ]

    diagnoses = []
    zone_noise_floors: list[float] = []  # 各 zone 的底噪 RMS（dB），供 afftdn 使用
    for zone in zones:
        if not zone.noise_window_indices:
            # 不可退回別區的噪音窗：那會讓這一區用錯誤的降噪基準，且錯得無聲無息。
            # detect_zones 的切點取自「變化點所在噪音窗」的中點，且經最小 zone
            # 長度過濾，正常情況下每個 zone 都會含至少一個窗；但分區偵測異常時
            # 仍可能產生無窗的 zone（這正是本 SystemExit 存在的理由），
            # 走到這裡應該停下來而不是猜一個。
            raise SystemExit(
                f"Zone {zone.index}（{zone.start:.1f}s–{zone.end:.1f}s）沒有任何噪音採樣窗，"
                "無法為此區建立降噪基準。這通常代表分區偵測異常，"
                "請檢查 report.json 的 zones 與 noise_windows 是否對得上。"
            )
        # 底噪與 SNR 兩端一律用 RMS，不用 LUFS：ebur128 的 integrated loudness
        # 有 -70 LUFS 絕對閘門，比它更安靜的底噪會被截斷成 -70，用 LUFS 算 SNR
        # 會系統性低估訊噪比、讓降噪強度被選得過強。RMS 沒有閘門，且 SNR 在
        # 聲學上本來就是功率比（RMS 比），這才是正確定義。
        # 底噪取該區各採樣窗的中位數：單一異常安靜的窗（例如空調剛好停機）
        # 會讓 min 低估底噪、使 SNR 被高估而降噪不足；中位數對離群值穩健。
        zone_noise_rms = median(
            [noise_window_rms[i] for i in zone.noise_window_indices]
        )
        # 人聲響度取該區各語句的中位數，而非整段區間的響度。
        # 整段含靜音，會把人聲位準拉低，讓 SNR 被低估、降噪被拉得過強。
        zone_utterance_rms = [
            rms
            for utterance, rms in zip(classification.utterances, utterance_rms)
            if zone.start <= (utterance.start + utterance.end) / 2.0 < zone.end
        ]
        if not zone_utterance_rms:
            raise SystemExit(
                f"Zone {zone.index}（{zone.start:.1f}s–{zone.end:.1f}s）沒有任何語句，"
                "無法判斷人聲響度。請檢查 ASR 時間軸是否涵蓋整支檔案。"
            )
        zone_speech_rms = median(zone_utterance_rms)
        # afftdn 的 nf 語意就是訊號位準，與 SNR 用的是同一個量，
        # 直接沿用 zone_noise_rms，不必再重量一次
        zone_noise_floors.append(zone_noise_rms)
        # 落在此 zone 內的語句結束時刻，供殘響量測使用
        zone_ends = [
            seg.end for seg in timeline.segments
            if zone.start <= seg.end < zone.end
        ]
        diagnoses.append(
            diagnose_zone(buf_16k, zone, zone_noise_rms, zone_speech_rms, zone_ends)
        )

    zone_gains = compute_zone_gains(diagnoses, WORKING_LUFS)
    utterance_gains = compute_utterance_gains(
        classification.utterances, utterance_rms, zones, zone_gains, WORKING_LUFS
    )

    # 全檔感知響度與真峰值：唯一保留的 ffmpeg 量測（演算法不宜自行重刻）
    overall = measure_overall(args.input)

    write_report(args.work_dir, spec, classification, diagnoses,
                 utterance_rms, utterance_peaks, noise_window_rms, overall)
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
