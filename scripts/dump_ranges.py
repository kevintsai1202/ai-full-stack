# -*- coding: utf-8 -*-
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def dump_ranges():
    lines = (ROOT / "course-package/ch01-env-and-ai-workflow/02_dump.txt").read_text(encoding="utf-8").splitlines()
    def show(start_num, end_num):
        print(f"--- Cues {start_num} to {end_num} ---")
        for ln in lines:
            if not ln.strip():
                continue
            num = int(ln.split(" | ")[0])
            if start_num <= num <= end_num:
                print(ln)

    show(330, 345)
    print()
    show(358, 368)
    print()
    show(558, 570)
    print()
    show(625, 638)

if __name__ == "__main__":
    dump_ranges()
