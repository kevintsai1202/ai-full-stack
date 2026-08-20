"""AB 試聽片段：把修復前後的同一段接在一起，供人耳直接比對。"""
from pathlib import Path

from .ffmpeg_io import run_ffmpeg


def pick_preview_start(plan: dict) -> float:
    """挑出試聽起點：降噪強度最高的區段起點。

    denoise_db 由 SNR 反推而來，取最高者即取 SNR 最差的區段 —— 那裡最能
    看出修復是否有效。以 plan 而非診斷物件為輸入，因為 restore 階段只有
    plan.json 可用（使用者可能已手動調整過）。
    """
    zone = max(plan["zones"], key=lambda z: z["denoise_db"])
    return float(zone["start"])


def build_ab_preview(before_path: Path, after_path: Path, start: float,
                     out_path: Path, duration: float = 30.0) -> Path:
    """輸出「修復前 → 修復後」串接的試聽片段。"""
    run_ffmpeg([
        "-y",
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(before_path),
        "-ss", f"{start:.3f}", "-t", f"{duration:.3f}", "-i", str(after_path),
        "-filter_complex", "[0:a][1:a]concat=n=2:v=0:a=1[out]",
        "-map", "[out]", "-ac", "1", "-c:a", "pcm_s16le", str(out_path),
    ])
    return out_path
