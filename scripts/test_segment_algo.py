# -*- coding: utf-8 -*-
"""智能合併與語意斷句算法。"""
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def char_width(ch: str) -> float:
    return 1.0 if unicodedata.east_asian_width(ch) in ("W", "F") else 0.5

def text_width(text: str) -> float:
    return sum(char_width(c) for c in text)

def clean_spoken_fillers(t: str) -> str:
    """清理口語贅字與重複。"""
    t = t.strip()
    
    # 移除句首純口語贅字（僅在開頭為獨立贅詞時）
    starters_to_strip = ["好那我們", "OK好那我們", "OK好那", "好我們", "好那", "啊那", "好所以", "那好了以後啊"]
    for s in starters_to_strip:
        if t.startswith(s):
            t = t[len(s):].strip()
            break
            
    # 移除結尾贅字
    trailing_fillers = ["啦", "喔", "哦", "哈", "呃"]
    for tr in trailing_fillers:
        if t.endswith(tr):
            t = t[:-len(tr)].strip()
            
    return t

def test_algorithm():
    from optimize_from_bak import load_bak_cues
    cues = load_bak_cues()
    print(f"Loaded {len(cues)} cues.")
    
    # 測試前 50 句的合併效果
    merged = []
    curr_text = ""
    curr_start = cues[0]["start"]
    curr_end = cues[0]["end"]
    
    for c in cues[:50]:
        t = c["text"].strip()
        print(f"[{c['num']:03d}] ({c['start']:.2f}s -> {c['end']:.2f}s) {t}")

if __name__ == "__main__":
    test_algorithm()
