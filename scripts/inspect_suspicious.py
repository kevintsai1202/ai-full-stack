# -*- coding: utf-8 -*-
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def inspect_areas():
    asr = json.loads((ROOT / "course-package/ch01-env-and-ai-workflow/02.asr.json").read_text(encoding="utf-8"))
    
    def print_asr_range(st, en):
        chars = [c for c in asr if st <= c["start"] <= en]
        print(f"ASR ({st}s -> {en}s): " + "".join(c["text"] for c in chars))
        
    print("=== Area 1: 330~340 cues (around 1050s - 1080s) ===")
    print_asr_range(1050, 1080)
    
    print("\n=== Area 2: 360~365 cues (around 1150s - 1175s) ===")
    print_asr_range(1150, 1175)
    
    print("\n=== Area 3: 560~568 cues (around 1785s - 1805s) ===")
    print_asr_range(1785, 1805)
    
    print("\n=== Area 4: 625~635 cues (around 2000s - 2030s) ===")
    print_asr_range(2000, 2030)

if __name__ == "__main__":
    inspect_areas()
