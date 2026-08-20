"""Mastering 美化 CLI：對修復後的檔案套用音色雕琢（可選的第三階段）。"""
import argparse
import sys
from pathlib import Path

# Windows 主控台預設 cp950，印出非 Big5 字元（例如中文標點或特殊符號）會
# 拋出 UnicodeEncodeError 並讓行程以非零狀態退出，看起來像美化失敗，
# 實際上只是印不出來 —— 因此必須在最前面把標準輸出/錯誤輸出都轉成 UTF-8。
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ar.bulk import measure_overall
from ar.ffmpeg_io import run_ffmpeg
from ar.master import build_master_chain
from ar.preview import build_ab_preview
from ar.probe import probe
from ar.render import apply_loudnorm, mux_video

PREVIEW_SECONDS = 30.0  # AB 試聽片段長度（秒）
LOUDNESS_TOLERANCE = 0.5   # 響度驗收容差（LUFS），與修復階段同一把尺
TRUE_PEAK_LIMIT = -1.5     # 真峰值驗收上限（dBTP），與修復階段同一把尺
# mastering 專用的限幅天花板（樣本層 dBFS）。修復路徑的 -2.0 在這裡不夠：
# treble shelf 與 aexciter 增加的高頻能量會放大 inter-sample peak，
# 真實素材（02.mp4）實測 -2.0 天花板交付後真峰值 -1.10 dBTP 直接超標。
# 壓深到 -3.0 多留 1dB 給 ISP 過衝＋AAC 重編；響度由迭代收斂補回。
MASTER_TRUE_PEAK_TARGET = -3.0


def main() -> None:
    """解析參數並執行美化流程。"""
    parser = argparse.ArgumentParser(description="修復後音軌的 mastering 美化")
    parser.add_argument("--input", required=True, type=Path,
                        help="修復後的檔案（影片或音檔）")
    parser.add_argument("--out", required=True, type=Path, help="輸出檔路徑")
    parser.add_argument("--preset", default="podcast",
                        help="美化 preset：conservative／podcast（預設）／rich")
    parser.add_argument("--target", type=float, default=-16.0,
                        help="目標整體響度（LUFS），預設 -16.0")
    parser.add_argument("--work-dir", type=Path,
                        help="中繼檔目錄，預設為輸出檔同層 work/")
    args = parser.parse_args()

    if not args.input.exists():
        raise SystemExit(f"找不到輸入檔：{args.input}")
    try:
        # 未知 preset 在做任何工作前就擋下，訊息含可用選項供照抄
        master_chain = build_master_chain(args.preset)
    except ValueError as error:
        raise SystemExit(str(error))

    # 中繼檔工作目錄，未指定時預設放在輸出檔同層的 work/ 子目錄
    work_dir = args.work_dir or args.out.parent / "work"
    work_dir.mkdir(parents=True, exist_ok=True)

    # 媒體規格：判斷影片/音檔，並鎖定來源取樣率避免不必要的降規格
    spec = probe(args.input)

    # 美化前的整體指標，供結果報告的前後對照
    before_lufs, before_peak = measure_overall(args.input)

    # 第一步：抽出音軌套美化鏈 → 32-bit float WAV。
    # 保持 f32 的理由同 render：壓縮器 makeup 與 EQ 提升後峰值可能過 0，
    # 此時就量化成 16-bit 會截頂，後面的正規化救不回來。
    mastered = work_dir / "mastered.wav"
    run_ffmpeg([
        "-y", "-i", str(args.input),
        "-af", master_chain,
        "-ac", "1", "-ar", str(spec.sample_rate),
        "-c:a", "pcm_f32le", str(mastered),
    ])

    # 第二步：重用修復階段的迭代收斂正規化 —— 美化的 EQ/壓縮改變了響度，
    # 必須重新收斂到目標；alimiter 的峰值保護也在這條路徑裡。
    normalized = apply_loudnorm(mastered, work_dir / "normalized.wav", args.target,
                                limit_db=MASTER_TRUE_PEAK_TARGET)

    # 第三步：封裝輸出。影片把美化後音軌換揉回原畫面；純音訊比照 restore.py
    # 的三分支（.wav 位元組複製／其他容器交給 ffmpeg 轉檔）。
    if spec.is_video:
        mux_video(args.input, normalized, args.out)
    elif args.out.suffix.lower() == ".wav":
        # 中繼本來就是 WAV，直接搬位元組，不多做一次無謂的編解碼
        args.out.write_bytes(normalized.read_bytes())
    else:
        # 使用者指定了別的容器（如 .mp3／.m4a）。位元組複製會產出
        # 「副檔名說是 mp3、內容其實是 WAV」的檔案，下游可能誤判或播不出來。
        run_ffmpeg(["-y", "-i", str(normalized), str(args.out)])

    # AB 試聽：美化前 30 秒 → 美化後 30 秒。起點取 0.0 即可 —— 美化是
    # 全域效果（EQ/壓縮/激勵作用於整條訊號），任一段都能聽出差異，
    # 不必像修復那樣特意挑降噪最重（最吵）的段落。
    build_ab_preview(args.input, normalized, 0.0,
                     work_dir / "master-preview-ab.wav", PREVIEW_SECONDS)

    # 驗證量**實際交付的檔案**：影片的 mux 會把音軌重編成 AAC，有損編碼
    # 可能讓真峰值上升或響度偏移，量 pre-mux 的 WAV 等於驗證了一個
    # 使用者拿不到的東西。
    after_lufs, after_peak = measure_overall(args.out)
    checks = [
        ("響度收斂",
         abs(after_lufs - args.target) <= LOUDNESS_TOLERANCE,
         f"實測 {after_lufs:.2f} LUFS，目標 {args.target}（容差 ±{LOUDNESS_TOLERANCE}）"),
        ("真峰值",
         after_peak <= TRUE_PEAK_LIMIT,
         f"實測 {after_peak:.2f} dBTP，上限 {TRUE_PEAK_LIMIT}"),
    ]
    _print_result(args.preset, before_lufs, before_peak, after_lufs, after_peak,
                  checks, args.out, work_dir)
    if not all(passed for _, passed, _ in checks):
        raise SystemExit(1)


def _print_result(preset: str, before_lufs: float, before_peak: float,
                  after_lufs: float, after_peak: float,
                  checks: list[tuple[str, bool, str]],
                  out_path: Path, work_dir: Path) -> None:
    """印出美化前後指標對照與驗證結論（風格比照 restore.py）。"""
    print(f"\n輸出：{out_path}")
    print(f"AB 試聽：{work_dir / 'master-preview-ab.wav'}")
    print(f"套用 preset：{preset}\n")
    print("美化前後指標：")
    print(f"  整體 LUFS: {before_lufs:.2f} → {after_lufs:.2f}")
    print(f"  真峰值 dBTP: {before_peak:.2f} → {after_peak:.2f}")
    print("\n驗證結果：")
    for name, passed, detail in checks:
        print(f"  [{'通過' if passed else '失敗'}] {name}：{detail}")
    if not all(passed for _, passed, _ in checks):
        print("\n驗證未通過。請檢查上列失效環節（通常是有損重編吃掉峰值餘裕）。")


if __name__ == "__main__":
    main()
