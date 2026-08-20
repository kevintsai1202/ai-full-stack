"""ffmpeg / ffprobe 外部程序呼叫封裝。"""
import json
import shutil
import subprocess
from pathlib import Path


class FFmpegError(RuntimeError):
    """ffmpeg 或 ffprobe 執行失敗時拋出。"""


def require_tool(tool: str) -> str:
    """確認外部工具存在，回傳其絕對路徑；不存在則明確報錯。

    命名為公開函式（非 _require），因為後續模組（如批次處理流程）
    需要跨模組呼叫此函式以提前檢查工具是否齊備。
    """
    found = shutil.which(tool)
    if not found:
        raise FFmpegError(f"找不到 {tool}，請先安裝 ffmpeg 並加入 PATH")
    return found


def run_ffmpeg(args: list[str]) -> str:
    """執行 ffmpeg 並回傳 stderr 全文（濾鏡量測結果都印在 stderr）。"""
    cmd = [require_tool("ffmpeg"), "-hide_banner", "-nostdin", *args]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                            errors="replace")
    if result.returncode != 0:
        raise FFmpegError(f"ffmpeg 失敗（exit {result.returncode}）：\n{result.stderr[-2000:]}")
    return result.stderr


def run_ffprobe_json(path: Path) -> dict:
    """以 ffprobe 取得媒體的 format 與 streams 資訊（JSON）。"""
    cmd = [
        require_tool("ffprobe"), "-hide_banner", "-v", "error",
        "-print_format", "json", "-show_format", "-show_streams", str(path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                            errors="replace")
    if result.returncode != 0:
        raise FFmpegError(f"ffprobe 失敗：{result.stderr[-1000:]}")
    return json.loads(result.stdout)
