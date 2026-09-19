# -*- coding: utf-8 -*-
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def check_asr_segment():
    asr = json.loads((ROOT / "course-package/ch01-env-and-ai-workflow/02.asr.json").read_text(encoding="utf-8"))
    chars = [c for c in asr if 400 <= c["start"] <= 425]
    cur_word = ""
    st = chars[0]["start"]
    for c in chars:
        print(f"{c['text']}({c['start']:.2f}-{c['end']:.2f})", end=" ")
    print()

if __name__ == "__main__":
    check_asr_segment()
