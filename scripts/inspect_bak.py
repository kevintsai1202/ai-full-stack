# -*- coding: utf-8 -*-
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def inspect(path: Path):
    print(f"=== Inspecting {path.name} ===")
    blocks = path.read_text(encoding="utf-8").strip().split("\n\n")
    for b in blocks:
        lines = [l.strip() for l in b.split("\n") if l.strip()]
        if len(lines) >= 3:
            time_str = lines[1]
            if "00:06:3" in time_str or "00:06:4" in time_str or "00:06:5" in time_str or "00:07:0" in time_str:
                print(f"{int(lines[0]):03d} | {lines[1]} | {' '.join(lines[2:])}")

if __name__ == "__main__":
    inspect(ROOT / "course-package/ch01-env-and-ai-workflow/02.srt.user.bak")
