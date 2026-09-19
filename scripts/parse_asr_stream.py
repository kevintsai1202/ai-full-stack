# -*- coding: utf-8 -*-
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def extract_asr_stream():
    asr_path = ROOT / "course-package/ch01-env-and-ai-workflow/02.asr.json"
    asr = json.loads(asr_path.read_text(encoding="utf-8"))
    
    # 將 ASR 組合成字元流與停頓點
    chars = []
    for i, item in enumerate(asr):
        chars.append({
            "idx": i,
            "char": item["text"],
            "start": item["start"],
            "end": item["end"],
            "gap_after": asr[i+1]["start"] - item["end"] if i+1 < len(asr) else 0.0
        })
    
    print(f"Total chars: {len(chars)}")
    return chars

if __name__ == "__main__":
    extract_asr_stream()
