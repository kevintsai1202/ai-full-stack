# -*- coding: utf-8 -*-
"""分析與優化 SRT 字幕腳本。"""
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def load_srt(srt_path: Path):
    content = srt_path.read_text(encoding="utf-8")
    blocks = content.strip().split("\n\n")
    cues = []
    for b in blocks:
        lines = [l.strip() for l in b.split("\n") if l.strip()]
        if len(lines) >= 3:
            cues.append({
                "num": int(lines[0]),
                "time": lines[1],
                "text": " ".join(lines[2:])
            })
    return cues

def main():
    srt_path = ROOT / "course-package/ch01-env-and-ai-workflow/02.srt"
    cues = load_srt(srt_path)
    print(f"Loaded {len(cues)} cues from {srt_path}")
    
    # 輸出純文本版供檢查
    out_txt = ROOT / "course-package/ch01-env-and-ai-workflow/02_dump.txt"
    with open(out_txt, "w", encoding="utf-8") as f:
        for c in cues:
            f.write(f"{c['num']:03d} | {c['time']} | {c['text']}\n")
    print(f"Dumped to {out_txt}")

if __name__ == "__main__":
    main()
