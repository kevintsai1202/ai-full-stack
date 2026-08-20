"""批次體檢：對整個目錄的音影檔各自跑一次 analyze，並匯總成總表。

只做體檢不做修復 —— 修復仍須逐檔確認 plan.json，這是刻意的設計。
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

# Windows 主控台預設 cp950，印出非 Big5 字元（例如中文警示符號、標點）會
# 拋出 UnicodeEncodeError 並讓行程以非零狀態退出，看起來像批次失敗，
# 實際上只是印不出來 —— 因此必須在最前面把標準輸出/錯誤輸出都轉成 UTF-8。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

# 支援批次處理的媒體副檔名（音檔與影片）
MEDIA_SUFFIXES = {".wav", ".mp3", ".m4a", ".flac", ".aac", ".mp4", ".mov", ".mkv"}


def main() -> None:
    """解析參數並批次執行體檢（或在 --restore 時批次執行修復）。"""
    parser = argparse.ArgumentParser(description="批次音質體檢")
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--target", type=float, default=-16.0)
    parser.add_argument("--restore", action="store_true",
                        help="對已有 plan.json 的檔案執行修復並匯總驗證結果")
    args = parser.parse_args()

    analyze = Path(__file__).resolve().parent / "analyze.py"
    # 目錄下所有符合媒體副檔名的檔案，依檔名排序以確保輸出順序穩定
    media_files = sorted(
        p for p in args.input_dir.iterdir()
        if p.is_file() and p.suffix.lower() in MEDIA_SUFFIXES
    )
    if not media_files:
        raise SystemExit(f"{args.input_dir} 下沒有可處理的音影檔")

    if args.restore:
        _batch_restore(media_files, args.out_dir)
        return

    entries = []  # 每一檔的體檢摘要
    for media in media_files:
        work_dir = args.out_dir / media.stem
        print(f"\n=== 體檢 {media.name} ===")
        result = subprocess.run(
            [sys.executable, str(analyze), "--input", str(media),
             "--work-dir", str(work_dir), "--target", str(args.target)],
            text=True, encoding="utf-8",
        )
        entries.append(_summarize(media, work_dir, result.returncode))

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "summary.json").write_text(
        json.dumps({"target_lufs": args.target, "files": entries},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    _print_summary(entries)


def _summarize(media: Path, work_dir: Path, returncode: int) -> dict:
    """摘要單一檔案的體檢結果。"""
    report_path = work_dir / "report.json"
    if returncode != 0 or not report_path.exists():
        return {"file": media.name, "ok": False, "reason": "體檢失敗"}
    report = json.loads(report_path.read_text(encoding="utf-8"))
    zones = report["zones"]
    return {
        "file": media.name,
        "ok": True,
        "duration": report["media"]["duration"],
        "zones": len(zones),
        "utterances": report["counts"]["utterances"],
        "min_snr_db": min(z["snr_db"] for z in zones),
        "needs_ai_rescue": any(z["needs_ai_rescue"] for z in zones),
        "issues": [issue for z in zones for issue in z["issues"]],
    }


def _batch_restore(media_files: list[Path], out_dir: Path) -> None:
    """對已體檢過（有 plan.json）的檔案執行修復，並匯總前後指標。

    沒有 plan.json 的檔案一律跳過 —— 批次不得成為繞過人工確認的後門。
    """
    restore = Path(__file__).resolve().parent / "restore.py"
    entries: list[dict] = []  # 每一檔的修復摘要（含被跳過的）
    for media in media_files:
        work_dir = out_dir / media.stem
        plan_path = work_dir / "plan.json"
        if not plan_path.exists():
            entries.append({"file": media.name, "skipped": True,
                            "reason": "沒有 plan.json，尚未體檢或尚未確認"})
            print(f"跳過 {media.name}：沒有 plan.json")
            continue
        out_path = work_dir / f"{media.stem}-restored{media.suffix}"
        print(f"\n=== 修復 {media.name} ===")
        result = subprocess.run(
            [sys.executable, str(restore), "--plan", str(plan_path),
             "--out", str(out_path), "--work-dir", str(work_dir / "work")],
            text=True, encoding="utf-8",
        )
        entries.append(_summarize_restore(media, work_dir, result.returncode))

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "restore-summary.json").write_text(
        json.dumps({"files": entries}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    _print_restore_summary(entries)


def _summarize_restore(media: Path, work_dir: Path, returncode: int) -> dict:
    """摘要單一檔案的修復前後指標。

    returncode 優先於 verify.json 的存在與否：work_dir 會在多次 --restore
    之間保留，若這次在寫出新 verify.json 之前就失敗（例如 ffmpeg 崩潰），
    讀到的會是**上一輪殘留**的舊資料，讓使用者以為卡在同一個驗證項，
    掩蓋這次真正的失敗原因。
    """
    verify_path = work_dir / "work" / "verify.json"
    if returncode != 0:
        return {"file": media.name, "skipped": False, "ok": False,
                "reason": f"修復失敗（結束碼 {returncode}），請看該檔的終端輸出"}
    if not verify_path.exists():
        return {"file": media.name, "skipped": False, "ok": False,
                "reason": "修復未產出 verify.json"}
    payload = json.loads(verify_path.read_text(encoding="utf-8"))
    return {
        "file": media.name,
        "skipped": False,
        "ok": payload["result"]["passed"],
        "before": payload["before"],
        "after": payload["after"],
        "failed_checks": [
            c["name"] for c in payload["result"]["checks"] if not c["passed"]
        ],
    }


def _print_restore_summary(entries: list[dict]) -> None:
    """印出修復前後指標總表。"""
    print("\n=== 批次修復總表 ===")
    for entry in entries:
        if entry.get("skipped"):
            print(f"  {entry['file']}: 跳過（{entry['reason']}）")
            continue
        if not entry["ok"]:
            failed = "、".join(entry.get("failed_checks", [])) or entry.get("reason", "")
            print(f"  {entry['file']}: 未通過（{failed}）")
            continue
        before, after = entry["before"], entry["after"]

        def _fmt(value, precision: int = 1) -> str:
            """格式化單一指標值；None（噪音採樣窗為空時不可量測）印 N/A。

            與 restore.py _print_result 的 None 判斷同一防護：直接對 None
            做 :.1f 會拋 TypeError，讓整支批次流程在印總表時中斷。
            """
            return f"{value:.{precision}f}" if value is not None else "N/A"

        print(f"  {entry['file']}: SNR {_fmt(before['snr_db'])} → "
              f"{_fmt(after['snr_db'])} dB／底噪 {_fmt(before['noise_rms_db'])} → "
              f"{_fmt(after['noise_rms_db'])} dB RMS／整體 {_fmt(before['integrated_lufs'])} → "
              f"{_fmt(after['integrated_lufs'])}／句間標準差 "
              f"{_fmt(before['utterance_rms_stdev'], 2)} → {_fmt(after['utterance_rms_stdev'], 2)}")


def _print_summary(entries: list[dict]) -> None:
    """印出體檢總表。"""
    print("\n=== 批次體檢總表 ===")
    for entry in entries:
        if not entry["ok"]:
            print(f"  {entry['file']}: {entry['reason']}")
            continue
        flag = "⚠ 需 AI 救援" if entry["needs_ai_rescue"] else ""
        print(f"  {entry['file']}: {entry['zones']} 區／"
              f"最差 SNR {entry['min_snr_db']:.1f} dB {flag}")


if __name__ == "__main__":
    main()
