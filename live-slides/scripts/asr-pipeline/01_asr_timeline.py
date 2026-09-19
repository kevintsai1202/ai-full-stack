"""步驟 1：對音檔跑 faster-whisper（CPU/int8），產出詞級時間軸 timeline.json。

用途有二：
  a) 供 audio-restoration 體檢（analyze.py）作為切句依據，避免它另外下載 small 模型再跑一次。
  b) 降噪完成後再跑一次本腳本，其輸出即為最終字幕的來源。

設計說明：
  - device 固定 cpu、compute_type 固定 int8 —— 本機 CUDA 裝置數為 0，int8 量化在純 CPU 下
    約比 float32 快 2-3 倍且中文辨識率幾乎無損。
  - 輸出格式刻意與 audio-restoration/scripts/asr_timeline_runner.py 完全一致，兩者可互相沿用。
  - 本腳本可重跑：同一組參數重跑會覆寫 timeline.json，不會產生累積狀態。
  - 一律先用 ffmpeg 轉成 16kHz mono WAV 再餵給 whisper。實測（2026-09-14）直接餵 MP3
    會讓 RTF 從 0.48 惡化到 9.3（90 秒音檔跑 14 分鐘）—— MP3 需逐幀掃描定位，
    時間全耗在解碼而非推論。whisper 內部本來就重採樣到 16k，先轉不會損失資訊。
"""
import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

from faster_whisper import WhisperModel

# Windows 主控台預設 cp950 無法輸出中文，強制 UTF-8 以免 log 中斷
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def to_wav16k(src: Path, out_dir: Path) -> Path:
    """把任意音／視訊來源轉成 16kHz 單聲道 WAV，回傳該 WAV 路徑。

    這是效能關鍵步驟，不是可省略的整理動作 —— 見模組說明的 RTF 實測數據。
    已存在且非空的同名 WAV 會直接沿用，讓本腳本可重跑而不必重複解碼。
    """
    out_dir.mkdir(parents=True, exist_ok=True)
    wav = out_dir / f"{src.stem}-16k.wav"
    if wav.exists() and wav.stat().st_size > 1024:
        print(f"[01] 沿用既有 WAV：{wav}", flush=True)
        return wav
    print(f"[01] 轉檔 16k mono WAV：{src} → {wav}", flush=True)
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-i", str(src),
         "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", str(wav)],
        check=True,
    )
    return wav


def transcribe(audio_path: Path, out_dir: Path, model_size: str, threads: int,
               beam: int) -> None:
    """轉錄音訊並輸出 timeline.json（詞級時間戳，不在此產字幕檔）。"""
    out_dir.mkdir(parents=True, exist_ok=True)
    started = time.time()
    audio_path = to_wav16k(audio_path, out_dir)
    print(f"[01] 載入模型 {model_size} (cpu/int8, threads={threads}, beam={beam})", flush=True)
    model = WhisperModel(model_size, device="cpu", compute_type="int8",
                         cpu_threads=threads)

    print(f"[01] 開始轉錄：{audio_path}", flush=True)
    raw_segments, info = model.transcribe(
        str(audio_path),
        language="zh",
        beam_size=beam,
        vad_filter=True,          # 濾掉長段靜音，避免模型在無聲處產生幻聽字句
        word_timestamps=True,     # 詞級時間戳是降噪切句與字幕重新斷句的共同依據
    )

    # faster-whisper 的 segments 是 generator，逐段取出時才真正推論，
    # 因此在迴圈內印進度可即時反映存活狀態（供背景執行時以 log 成長判斷）。
    segments = []
    for segment in raw_segments:
        segments.append({
            "id": segment.id,
            "start": segment.start,
            "end": segment.end,
            "text": segment.text.strip(),
            "no_speech_prob": segment.no_speech_prob,
            "words": [
                {"start": w.start, "end": w.end, "word": w.word,
                 "probability": w.probability}
                for w in (segment.words or [])
            ],
        })
        if len(segments) % 20 == 0:
            elapsed = time.time() - started
            print(f"[01] 已完成 {len(segments)} 段 / 音訊位置 {segment.end:.1f}s "
                  f"/ 已耗時 {elapsed/60:.1f} 分", flush=True)

    payload = {
        "info": {
            "language": info.language,
            "language_probability": info.language_probability,
            "duration": info.duration,
            "duration_after_vad": info.duration_after_vad,
            "model": model_size,
        },
        "segments": segments,
    }
    (out_dir / "timeline.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"[01] 完成：{len(segments)} 段，總耗時 {(time.time()-started)/60:.1f} 分", flush=True)
    print(f"[01] 輸出：{out_dir / 'timeline.json'}", flush=True)


def main() -> None:
    """解析參數並啟動轉錄。"""
    parser = argparse.ArgumentParser(description="產出詞級時間軸 timeline.json")
    parser.add_argument("--audio", required=True, type=Path, help="輸入音檔或影片")
    parser.add_argument("--out-dir", required=True, type=Path, help="輸出目錄")
    parser.add_argument("--model", default="medium", help="模型大小，預設 medium")
    parser.add_argument("--threads", type=int, default=8, help="CPU 執行緒數")
    parser.add_argument("--beam", type=int, default=5,
                        help="beam size；前置時間軸用 1 即可，最終字幕建議 5")
    args = parser.parse_args()
    transcribe(args.audio, args.out_dir, args.model, args.threads, args.beam)


if __name__ == "__main__":
    main()
