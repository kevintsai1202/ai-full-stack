"""音質修復 CLI：只吃 plan.json，不自行推測參數。"""
import argparse
import json
import sys
from dataclasses import asdict
from pathlib import Path

# Windows 主控台預設 cp950，印出非 Big5 字元（例如中文標點或特殊符號）會
# 拋出 UnicodeEncodeError 並讓行程以非零狀態退出，看起來像修復失敗，
# 實際上只是印不出來 —— 因此必須在最前面把標準輸出/錯誤輸出都轉成 UTF-8。
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ar.metrics import collect_metrics
from ar.plan_io import load_plan
from ar.preview import build_ab_preview, pick_preview_start
from ar.probe import probe
from ar.ffmpeg_io import run_ffmpeg
from ar.render import (apply_loudnorm, concat_zones, export_asr_wav, mux_video,
                       render_zones)
from ar.verify import verify

PREVIEW_SECONDS = 30.0  # AB 試聽片段長度（秒）


def main() -> None:
    """解析參數並執行修復流程。"""
    parser = argparse.ArgumentParser(description="講課音軌音質修復")
    parser.add_argument("--plan", required=True, type=Path, help="analyze.py 產出的 plan.json")
    parser.add_argument("--out", required=True, type=Path, help="輸出檔路徑")
    parser.add_argument("--work-dir", type=Path, help="中繼檔目錄，預設為輸出檔同層 work/")
    args = parser.parse_args()

    # 硬性閘門：沒有 plan.json 就直接報錯退出，不得自行推測參數
    if not args.plan.exists():
        raise SystemExit(
            f"找不到 plan：{args.plan}\n"
            "restore.py 不會自行推測參數，請先執行 analyze.py 產出處理計畫。"
        )
    try:
        plan = load_plan(args.plan)
    except ValueError as error:
        # schema 版本不符時不要吐出完整 traceback，給一句使用者能照做的話，
        # 與上面「找不到 plan」的乾淨中文訊息維持一致水準。
        raise SystemExit(
            f"{error}\n請重新執行 analyze.py 產生新版 plan.json。"
        )
    # 中繼檔工作目錄，未指定時預設放在輸出檔同層的 work/ 子目錄
    work_dir = args.work_dir or args.out.parent / "work"
    work_dir.mkdir(parents=True, exist_ok=True)

    # 原始輸入檔路徑（來自 plan.json 記錄的來源），與媒體規格
    input_path = Path(plan["input"])
    spec = probe(input_path)

    # report.json 與 plan.json 同目錄，內含經雙重確認的噪音採樣窗
    report_path = args.plan.parent / "report.json"
    # 修復前的四項指標基準值
    before = collect_metrics(input_path, plan, report_path=report_path)

    # 依計畫逐區間渲染（降噪、增益、去齒音等），再合併、響度正規化
    parts = render_zones(input_path, plan, work_dir)
    merged = concat_zones(parts, work_dir / "merged.wav")
    normalized = apply_loudnorm(merged, work_dir / "normalized.wav", plan["target_lufs"])

    # 影片來源需把修復後音軌與原始畫面重新封裝；純音訊依輸出副檔名決定
    if spec.is_video:
        mux_video(input_path, normalized, args.out)
    elif args.out.suffix.lower() == ".wav":
        # 中繼本來就是 WAV，直接搬位元組，不多做一次無謂的編解碼
        args.out.write_bytes(normalized.read_bytes())
    else:
        # 使用者指定了別的容器（如 .mp3／.m4a）。位元組複製會產出
        # 「副檔名說是 mp3、內容其實是 WAV」的檔案，下游可能誤判或播不出來。
        run_ffmpeg(["-y", "-i", str(normalized), str(args.out)])

    # 供 ASR 使用的 16k 單聲道 WAV
    export_asr_wav(normalized, work_dir / "restored-16k.wav")

    # 修復前後 AB 試聽片段
    build_ab_preview(input_path, normalized, pick_preview_start(plan),
                     work_dir / "preview-ab.wav", PREVIEW_SECONDS)

    # 影片情境要量**實際交付的檔案**：mux 會把音軌重編成 AAC，
    # 有損編碼可能讓真峰值上升或響度偏移，量 pre-mux 的 WAV 等於
    # 驗證了一個使用者拿不到的東西。
    after_source = args.out if spec.is_video else normalized
    after = collect_metrics(after_source, plan, report_path=report_path)
    result = verify(before, after, plan["target_lufs"])
    (work_dir / "verify.json").write_text(
        json.dumps({"before": before, "after": after, "result": asdict(result)},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    _print_result(before, after, result, args.out, work_dir)
    if not result.passed:
        raise SystemExit(1)


def _print_result(before: dict, after: dict, result, out_path: Path, work_dir: Path) -> None:
    """印出前後指標對照與驗證結論。"""
    print(f"\n輸出：{out_path}")
    print(f"AB 試聽：{work_dir / 'preview-ab.wav'}")
    print(f"ASR 用 WAV：{work_dir / 'restored-16k.wav'}\n")
    print("修復前後指標：")
    for key, label in (("noise_rms_db", "底噪 RMS dB"), ("integrated_lufs", "整體 LUFS"),
                       ("true_peak", "真峰值 dBTP"), ("utterance_lufs_stdev", "句間標準差")):
        # noise_rms_db 缺 report.json 時為 None（不可驗證），格式化前需個別判斷，
        # 否則 f"{None:.2f}" 會拋 TypeError 讓整支 CLI 崩潰
        before_str = f"{before[key]:.2f}" if before[key] is not None else "N/A"
        after_str = f"{after[key]:.2f}" if after[key] is not None else "N/A"
        print(f"  {label}: {before_str} → {after_str}")
    print("\n驗證結果：")
    for check in result.checks:
        print(f"  [{'通過' if check.passed else '失敗'}] {check.name}：{check.detail}")
    if not result.passed:
        print("\n驗證未通過。請依上列失效環節調整 plan.json 後重跑。")


if __name__ == "__main__":
    main()
