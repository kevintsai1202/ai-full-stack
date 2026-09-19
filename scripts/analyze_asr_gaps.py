# -*- coding: utf-8 -*-
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def analyze_asr():
    asr_path = ROOT / "course-package/ch01-env-and-ai-workflow/02.asr.json"
    asr = json.loads(asr_path.read_text(encoding="utf-8"))
    
    print(f"Total ASR characters: {len(asr)}")
    print(f"Time range: {asr[0]['start']}s -> {asr[-1]['end']}s ({asr[-1]['end']/60:.1f} mins)")
    
    # 統計停頓 (gap > 0.3s)
    gaps = []
    for i in range(len(asr) - 1):
        gap = asr[i+1]["start"] - asr[i]["end"]
        if gap >= 0.3:
            gaps.append((i, asr[i]["end"], asr[i+1]["start"], gap, asr[i]["text"], asr[i+1]["text"]))
            
    print(f"Total gaps >= 0.3s: {len(gaps)}")

if __name__ == "__main__":
    analyze_asr()
