# -*- coding: utf-8 -*-
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def parse_time(t_str):
    m = re.match(r"(\d+):(\d+):(\d+),(\d+)", t_str)
    return int(m[1]) * 3600 + int(m[2]) * 60 + int(m[3]) + int(m[4]) / 1000

def check_srt_anomalies(path: Path):
    blocks = path.read_text(encoding="utf-8").strip().split("\n\n")
    cues = []
    for b in blocks:
        lines = [l.strip() for l in b.split("\n") if l.strip()]
        if len(lines) >= 3:
            t_parts = lines[1].split(" --> ")
            st = parse_time(t_parts[0])
            en = parse_time(t_parts[1])
            cues.append({
                "num": int(lines[0]),
                "time_str": lines[1],
                "start": st,
                "end": en,
                "duration": en - st,
                "text": " ".join(lines[2:])
            })
    
    print(f"=== Checking {path.name} ({len(cues)} cues) ===")
    
    # 檢查時長過短 (< 0.5s)
    short_cues = [c for c in cues if c["duration"] < 0.6]
    print(f"Cues with duration < 0.6s: {len(short_cues)}")
    for c in short_cues:
        print(f"  [{c['num']:03d}] {c['time_str']} ({c['duration']:.3f}s): {c['text']}")
        
    # 檢查倒退或重疊 (start < prev_start)
    overlaps = []
    for i in range(1, len(cues)):
        if cues[i]["start"] < cues[i-1]["start"]:
            overlaps.append((cues[i-1], cues[i]))
    print(f"Time inversions: {len(overlaps)}")
    for prev, cur in overlaps:
        print(f"  Inversion: [{prev['num']:03d}] {prev['time_str']} -> [{cur['num']:03d}] {cur['time_str']}")

if __name__ == "__main__":
    check_srt_anomalies(ROOT / "course-package/ch01-env-and-ai-workflow/02.srt")
