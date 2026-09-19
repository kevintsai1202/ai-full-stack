"""步驟 0（診斷用）：量測 faster-whisper 在本機 CPU 上的實際吞吐。

背景：本機無 CUDA 裝置，只能純 CPU 推論。不同 compute_type / beam_size /
word_timestamps 組合在同一顆 CPU 上的差距可達數倍，且 int8 未必最快
（需 CPU 支援 AVX512-VNNI 才有明顯優勢）。與其憑經驗猜，不如實測。

量測方式：把「模型載入」與「實際推論」分開計時 —— 載入是一次性成本，
推論才會隨音檔長度線性放大，兩者混在一起會嚴重誤判全檔所需時間。
"""
import argparse
import sys
import time
from pathlib import Path

from faster_whisper import WhisperModel

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def bench(audio: Path, model_size: str, compute_type: str, threads: int,
          beam: int, word_ts: bool, audio_sec: float) -> None:
    """跑一組參數並印出載入／推論耗時與即時率（RTF）。"""
    t0 = time.time()
    model = WhisperModel(model_size, device="cpu", compute_type=compute_type,
                         cpu_threads=threads)
    load_s = time.time() - t0

    t1 = time.time()
    segs, _ = model.transcribe(str(audio), language="zh", beam_size=beam,
                               vad_filter=True, word_timestamps=word_ts)
    n = sum(1 for _ in segs)   # generator 必須耗盡才算真正跑完推論
    infer_s = time.time() - t1

    # RTF < 1 表示比即時快；全檔預估 = 音檔秒數 × RTF
    rtf = infer_s / audio_sec
    print(f"{model_size:7s} {compute_type:8s} th={threads:<3d} beam={beam} "
          f"word_ts={str(word_ts):5s} | 載入 {load_s:5.1f}s | 推論 {infer_s:6.1f}s "
          f"| {n:3d} 段 | RTF {rtf:.2f} | 全檔 4010s 預估 {4010*rtf/60:.0f} 分", flush=True)


def main() -> None:
    """依命令列參數執行單組量測。"""
    p = argparse.ArgumentParser()
    p.add_argument("--audio", required=True, type=Path)
    p.add_argument("--audio-sec", type=float, required=True, help="樣本音檔實際秒數")
    p.add_argument("--model", default="medium")
    p.add_argument("--compute-type", default="int8")
    p.add_argument("--threads", type=int, default=8)
    p.add_argument("--beam", type=int, default=5)
    p.add_argument("--no-word-ts", action="store_true")
    a = p.parse_args()
    bench(a.audio, a.model, a.compute_type, a.threads, a.beam,
          not a.no_word_ts, a.audio_sec)


if __name__ == "__main__":
    main()
