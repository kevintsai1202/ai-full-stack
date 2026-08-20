"""媒體規格盤點：判斷是影片或純音檔，並取出音訊軌參數。"""
from dataclasses import dataclass
from pathlib import Path

from .ffmpeg_io import FFmpegError, run_ffprobe_json


@dataclass
class MediaSpec:
    """一個輸入媒體的規格摘要。"""
    path: Path
    is_video: bool          # 是否含影像軌（決定輸出要不要換揉）
    duration: float         # 總時長（秒）
    sample_rate: int        # 音訊取樣率
    channels: int           # 聲道數
    audio_codec: str        # 音訊編碼名稱


def probe(path: Path) -> MediaSpec:
    """讀取媒體規格；無音訊軌時明確報錯，因為本技能只處理人聲音軌。"""
    data = run_ffprobe_json(path)
    streams = data.get("streams", [])
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    if audio is None:
        raise FFmpegError(f"{path} 沒有音訊軌，無法進行音質修復")
    has_video = any(
        s.get("codec_type") == "video" and s.get("disposition", {}).get("attached_pic", 0) == 0
        for s in streams
    )
    return MediaSpec(
        path=path,
        is_video=has_video,
        duration=float(data["format"]["duration"]),
        sample_rate=int(audio["sample_rate"]),
        channels=int(audio["channels"]),
        audio_codec=audio["codec_name"],
    )
