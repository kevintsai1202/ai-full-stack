# -*- coding: utf-8 -*-
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def inspect_bak():
    bak_path = ROOT / "course-package/ch01-env-and-ai-workflow/02.srt.bak"
    blocks = bak_path.read_text(encoding="utf-8").strip().split("\n\n")
    cues = []
    for b in blocks:
        lines = [l.strip() for l in b.split("\n") if l.strip()]
        if len(lines) >= 3:
            cues.append({
                "num": int(lines[0]),
                "time": lines[1],
                "text": " ".join(lines[2:])
            })
    print(f"Loaded {len(cues)} cues from 02.srt.bak")
    
    # 輸出 02.srt.bak 到 txt
    out_txt = ROOT / "course-package/ch01-env-and-ai-workflow/02_bak_dump.txt"
    with open(out_txt, "w", encoding="utf-8") as f:
        for c in cues:
            f.write(f"{c['num']:03d} | {c['time']} | {c['text']}\n")
    print(f"Dumped to {out_txt}")

if __name__ == "__main__":
    inspect_bak()
