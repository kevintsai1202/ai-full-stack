# -*- coding: utf-8 -*-
"""長音檔分段 ASR：切成小段呼叫 Fish Audio，再合併回單一 .asr.json。

背景：
    Fish Audio ASR 對過長／過大的音檔會回 400（format not recognised 為誤導訊息，
    實測 4.4 分鐘 10MB 可過、36 分鐘 45MB 被拒）。本腳本先用 FFmpeg 把音檔
    切成固定長度小段，逐段辨識後依「各段實際長度」累加時間位移合併，
    輸出與 asr_to_srt.py 相同格式的逐字級 .asr.json 快取與初版 .srt。

用法（PowerShell）：
    python scripts/asr_long_audio.py course-package/ch01-env-and-ai-workflow/02.mp3
    python scripts/asr_long_audio.py --chunk 240 a.mp3   # 自訂每段秒數
    python scripts/asr_long_audio.py --force a.mp3       # 忽略既有 .asr.json 重新辨識

說明：
    - 各段辨識結果快取在 <音檔目錄>/.asr-chunks/<檔名>/ 之下，
      中斷重跑時已完成的段落直接讀快取，不重複耗 API 額度。
    - 合併完成後產生 <檔名>.asr.json，之後 align_script_srt.py 可直接取用。
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
# 重用單檔腳本的 API Key 讀取、字幕合併與 SRT 輸出邏輯
from asr_to_srt import cues_to_srt, load_api_key, merge_segments

from fish_audio_sdk import Session
from fish_audio_sdk.schemas import ASRRequest

sys.stdout.reconfigure(encoding="utf-8")  # Windows 主控台預設 cp950，避免中文亂碼

ROOT = Path(__file__).resolve().parent.parent

# 每段預設長度（秒）：Fish ASR 的逐字時間戳在單一請求約 1100~1200 字後會
# 凍結不再前進（實測 300 秒段的尾端 25~60 秒時間全部相同），
# 120 秒（一般語速約 550 字）留足安全邊際。
DEFAULT_CHUNK_SECONDS = 120


def run(cmd: list[str]) -> str:
    """執行外部指令並回傳 stdout，失敗時直接中止。"""
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        sys.exit(f"指令失敗：{' '.join(cmd)}\n{result.stderr}")
    return result.stdout


def probe_duration(path: Path) -> float:
    """用 ffprobe 取得音檔實際長度（秒）。"""
    out = run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)])
    return float(out.strip())


def split_audio(audio_path: Path, chunk_seconds: int) -> list[Path]:
    """把音檔切成固定秒數的 16kHz 單聲道 WAV 小段，回傳依序排列的段落路徑。

    必須重編碼成 WAV：mp3 用 stream copy 切段時，VBR 段落的時長中繼資料會被
    低估，Fish ASR 會把該段尾端的時間戳「夾」在錯誤的估計時長上不再前進
    （實測 chunk 尾端 20+ 秒的字元時間全部凍結）。WAV 時長取樣點精確，
    16kHz 單聲道也讓每段檔案小於 10MB，符合 API 限制。
    """
    chunk_dir = audio_path.parent / ".asr-chunks" / audio_path.stem
    chunk_dir.mkdir(parents=True, exist_ok=True)
    pattern = chunk_dir / f"{audio_path.stem}_%03d.wav"
    existing = sorted(chunk_dir.glob(f"{audio_path.stem}_[0-9][0-9][0-9].wav"))
    if existing:
        print(f"[切割] 使用既有段落 {len(existing)} 段：{chunk_dir}")
        return existing
    run(["ffmpeg", "-y", "-v", "error", "-i", str(audio_path), "-f", "segment",
         "-segment_time", str(chunk_seconds), "-ar", "16000", "-ac", "1",
         "-c:a", "pcm_s16le", str(pattern)])
    chunks = sorted(chunk_dir.glob(f"{audio_path.stem}_[0-9][0-9][0-9].wav"))
    print(f"[切割] 產生 {len(chunks)} 段（每段約 {chunk_seconds} 秒）：{chunk_dir}")
    return chunks


def asr_chunk(session: Session, chunk_path: Path, force: bool) -> list[dict]:
    """辨識單一段落（優先讀快取），回傳該段落內的相對時間 segments。"""
    cache_path = chunk_path.with_suffix(".asr.json")
    if cache_path.exists() and not force:
        print(f"[ASR] 使用快取：{cache_path.name}")
        return json.loads(cache_path.read_text(encoding="utf-8"))
    print(f"[ASR] 辨識中：{chunk_path.name}")
    resp = session.asr(ASRRequest(audio=chunk_path.read_bytes(), language="zh", ignore_timestamps=False))
    segments = [{"start": s.start, "end": s.end, "text": s.text} for s in resp.segments]
    cache_path.write_text(json.dumps(segments, ensure_ascii=False, indent=1), encoding="utf-8")
    return segments


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", nargs="+", help="音檔路徑（mp3/wav/flac）")
    parser.add_argument("--chunk", type=int, default=DEFAULT_CHUNK_SECONDS, help="每段秒數")
    parser.add_argument("--force", action="store_true", help="忽略段落快取重新辨識")
    args = parser.parse_args()

    session = Session(load_api_key())
    for arg in args.audio:
        audio_path = Path(arg)
        if not audio_path.is_absolute():
            audio_path = ROOT / audio_path
        if not audio_path.exists():
            sys.exit(f"找不到檔案：{audio_path}")

        chunks = split_audio(audio_path, args.chunk)
        merged: list[dict] = []
        offset = 0.0  # 累計時間位移：用各段「實際長度」而非名目段長，避免誤差累積
        for chunk in chunks:
            for seg in asr_chunk(session, chunk, args.force):
                merged.append({"start": round(seg["start"] + offset, 3),
                               "end": round(seg["end"] + offset, 3),
                               "text": seg["text"]})
            offset += probe_duration(chunk)

        if not merged:
            sys.exit(f"[ASR] {audio_path} 未回傳任何 segments，無法產生字幕")
        out_json = audio_path.with_suffix(".asr.json")
        out_json.write_text(json.dumps(merged, ensure_ascii=False, indent=1), encoding="utf-8")
        cues = merge_segments(merged)
        out_srt = audio_path.with_suffix(".srt")
        out_srt.write_text(cues_to_srt(cues), encoding="utf-8")
        print(f"[完成] {out_json.name}（{len(merged)} 字）→ {out_srt.name}（{len(cues)} 則字幕，總長約 {merged[-1]['end']:.0f} 秒）")


if __name__ == "__main__":
    main()
