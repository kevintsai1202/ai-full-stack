# -*- coding: utf-8 -*-
"""用 Fish Audio SDK 將音檔（mp3/wav/flac）轉成 SRT 字幕檔（繁體中文）。

用法（PowerShell）：
    python scripts/asr_to_srt.py course-package/ch01-env-and-ai-workflow/00.mp3
    python scripts/asr_to_srt.py a.mp3 b.mp3        # 可一次給多個檔案
    python scripts/asr_to_srt.py --force a.mp3      # 忽略快取、重新呼叫 ASR API

輸出：
    與輸入音檔同目錄、同檔名的 .srt（例：00.mp3 → 00.srt）。
    另存 .asr.json 原始辨識快取——調整合併參數重跑時直接讀快取，不再耗 API 額度。

說明：
    Fish Audio ASR 回傳的是「逐字級」segments（一個中文字一段），
    本腳本依「時間間隔／字幕長度／單則秒數」三規則合併成適合閱讀的字幕句，
    並用 OpenCC 將簡體輸出轉為繁體（s2twp，含台灣慣用語）。

API Key 讀取順序：環境變數 FISH_AUDIO_API_KEY → 專案根目錄 .env 的 FISH_AUDIO_API_KEY。
"""
import json
import re
import sys
from pathlib import Path

from fish_audio_sdk import Session
from fish_audio_sdk.schemas import ASRRequest
from opencc import OpenCC

# Windows 主控台預設 cp950，強制 UTF-8 避免中文輸出亂碼
sys.stdout.reconfigure(encoding="utf-8")

# 專案根目錄（本腳本位於 scripts/ 之下）
ROOT = Path(__file__).resolve().parent.parent

# 逐字 segments 合併成字幕句的三個門檻
GAP_BREAK = 0.5    # 與前一字間隔超過此秒數 → 視為句子停頓，換下一則字幕
MAX_CHARS = 18     # 單則字幕最多字元數（超過就換行成下一則）
MAX_SECONDS = 6.0  # 單則字幕最長持續秒數

# 簡轉繁（s2twp：含台灣用語轉換，例：软件→軟體）
CC = OpenCC("s2twp")


def load_api_key() -> str:
    """讀取 Fish Audio API Key：先看環境變數，再退回專案根目錄 .env。"""
    import os

    key = os.environ.get("FISH_AUDIO_API_KEY")
    if key:
        return key
    env_file = ROOT / ".env"
    if env_file.exists():
        match = re.search(r"^\s*FISH_AUDIO_API_KEY\s*=\s*(\S+)", env_file.read_text(encoding="utf-8"), re.M)
        if match:
            return match.group(1)
    sys.exit("找不到 FISH_AUDIO_API_KEY：請設定環境變數，或在專案根目錄 .env 填入 FISH_AUDIO_API_KEY=你的金鑰")


def fmt_time(seconds: float) -> str:
    """把秒數轉成 SRT 時間格式 HH:MM:SS,mmm。"""
    ms = round(seconds * 1000)
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1_000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def merge_segments(segments: list[dict]) -> list[dict]:
    """把逐字級 segments 合併成字幕句。

    換句條件（任一成立即結束目前這則字幕）：
    1. 下一字與目前字的間隔 > GAP_BREAK 秒（語句停頓）
    2. 累積字元數達 MAX_CHARS
    3. 目前這則字幕持續時間達 MAX_SECONDS 秒
    """
    cues: list[dict] = []
    cur: dict | None = None
    for seg in segments:
        text = seg["text"].strip()
        if not text:
            continue
        if cur is not None:
            gap = seg["start"] - cur["end"]
            too_long = len(cur["text"]) >= MAX_CHARS or (seg["end"] - cur["start"]) > MAX_SECONDS
            if gap > GAP_BREAK or too_long:
                cues.append(cur)
                cur = None
        if cur is None:
            cur = {"start": seg["start"], "end": seg["end"], "text": text}
        else:
            # 英文單字之間補空格；中文字直接相連
            joiner = " " if (cur["text"][-1].isascii() and text[0].isascii()) else ""
            cur["text"] += joiner + text
            cur["end"] = seg["end"]
    if cur is not None:
        cues.append(cur)
    return cues


def cues_to_srt(cues: list[dict]) -> str:
    """把合併後的字幕句組成 SRT 全文（此處一併簡轉繁）。"""
    blocks = []
    for i, cue in enumerate(cues, start=1):
        blocks.append(f"{i}\n{fmt_time(cue['start'])} --> {fmt_time(cue['end'])}\n{CC.convert(cue['text'])}\n")
    return "\n".join(blocks)


def get_segments(session: Session, audio_path: Path, force: bool) -> list[dict]:
    """取得逐字級辨識結果：優先讀 .asr.json 快取，沒有（或 --force）才呼叫 API。"""
    cache_path = audio_path.with_suffix(".asr.json")
    if cache_path.exists() and not force:
        print(f"[ASR] 使用快取：{cache_path}")
        return json.loads(cache_path.read_text(encoding="utf-8"))
    print(f"[ASR] 呼叫 Fish Audio 辨識中：{audio_path}")
    resp = session.asr(ASRRequest(audio=audio_path.read_bytes(), language="zh", ignore_timestamps=False))
    if not resp.segments:
        sys.exit(f"[ASR] {audio_path} 未回傳任何 segments，無法產生字幕")
    segments = [{"start": s.start, "end": s.end, "text": s.text} for s in resp.segments]
    cache_path.write_text(json.dumps(segments, ensure_ascii=False, indent=1), encoding="utf-8")
    return segments


def transcribe(session: Session, audio_path: Path, force: bool) -> Path:
    """辨識單一音檔並輸出同名 .srt，回傳輸出路徑。"""
    segments = get_segments(session, audio_path, force)
    cues = merge_segments(segments)
    out_path = audio_path.with_suffix(".srt")
    out_path.write_text(cues_to_srt(cues), encoding="utf-8")
    total = cues[-1]["end"] if cues else 0.0  # 以最後一句結束時間估音長
    print(f"[ASR] 完成：{out_path}（約 {total:.0f} 秒、{len(segments)} 字 → {len(cues)} 則字幕）")
    return out_path


def main() -> None:
    args = [a for a in sys.argv[1:] if a != "--force"]
    force = "--force" in sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    session = Session(load_api_key())
    for arg in args:
        path = Path(arg)
        if not path.is_absolute():
            path = ROOT / path
        if not path.exists():
            sys.exit(f"找不到檔案：{path}")
        transcribe(session, path, force)


if __name__ == "__main__":
    main()
