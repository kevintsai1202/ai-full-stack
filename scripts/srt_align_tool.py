# -*- coding: utf-8 -*-
import json
import re
import difflib
import unicodedata
from pathlib import Path

def fmt_time(seconds: float) -> str:
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    ms = int(round((seconds - int(seconds)) * 1000))
    if ms >= 1000:
        ms = 999
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

def norm_char(ch: str) -> str:
    # 轉小寫，全形英數轉半形
    ch = unicodedata.normalize("NFKC", ch).lower()
    return ch

def is_content_char(ch: str) -> bool:
    return ch.isalnum()

def load_asr_chars(asr_path: Path):
    raw = json.loads(asr_path.read_text(encoding="utf-8"))
    chars = []
    for item in raw:
        t = item["text"]
        for c in t:
            chars.append({
                "char": c,
                "norm": norm_char(c),
                "start": item["start"],
                "end": item["end"]
            })
    return chars

def align_cues_to_asr(cue_texts: list[str], asr_chars: list[dict]):
    """將字幕文本列表對齊到 ASR 字元流，回傳有精確 start/end 的 cues。"""
    cues = []
    
    # 建立所有 cue 的字元位置與索引
    full_text = "".join(cue_texts)
    offsets = []
    pos = 0
    for t in cue_texts:
        offsets.append(pos)
        pos += len(t)
        
    c_pos = [i for i, ch in enumerate(full_text) if is_content_char(ch)]
    c_norm = [norm_char(full_text[i]) for i in c_pos]
    a_norm = [c["norm"] for c in asr_chars]
    
    asr_of = [None] * len(c_norm)
    sm = difflib.SequenceMatcher(None, c_norm, a_norm, autojunk=False)
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            for off in range(i2 - i1):
                asr_of[i1 + off] = j1 + off
        elif tag == "replace":
            n = i2 - i1
            for off in range(n):
                asr_of[i1 + off] = j1 + min(int(off * (j2 - j1) / max(n, 1)), j2 - j1 - 1)
                
    # 局部二次對齊
    k = 0
    while k < len(c_norm):
        if asr_of[k] is not None:
            k += 1
            continue
        k_end = k
        while k_end < len(c_norm) and asr_of[k_end] is None:
            k_end += 1
        a_lo = (asr_of[k - 1] + 1) if k > 0 else 0
        a_hi = asr_of[k_end] if k_end < len(c_norm) else len(a_norm)
        if a_hi > a_lo:
            local = difflib.SequenceMatcher(None, c_norm[k:k_end], a_norm[a_lo:a_hi], autojunk=False)
            for a, b, size in local.get_matching_blocks():
                for m in range(size):
                    asr_of[k + a + m] = a_lo + b + m
        k = k_end
        
    times = [None] * len(full_text)
    for k, j in enumerate(asr_of):
        if j is not None:
            times[c_pos[k]] = (asr_chars[j]["start"], asr_chars[j]["end"])
            
    # 內插
    known = [(i, t) for i, t in enumerate(times) if t is not None]
    if known:
        ki = 0
        for i in range(len(times)):
            if times[i] is not None or not is_content_char(full_text[i]):
                continue
            while ki + 1 < len(known) and known[ki + 1][0] < i:
                ki += 1
            prev = known[ki] if known[ki][0] < i else None
            nxt = next((kv for kv in known[ki:] if kv[0] > i), None)
            if prev and nxt:
                ratio = (i - prev[0]) / (nxt[0] - prev[0])
                st = prev[1][1] + (nxt[1][0] - prev[1][1]) * ratio
                times[i] = (st, st)
            else:
                times[i] = (prev or nxt)[1]
                
    # 計算每則 cue 的 start / end
    for idx, (t, off) in enumerate(zip(cue_texts, offsets)):
        spans = [times[off + i] for i in range(len(t)) if times[off + i] is not None]
        if spans:
            st, en = spans[0][0], spans[-1][1]
        else:
            st, en = 0.0, 0.0
        cues.append({
            "idx": idx + 1,
            "text": t,
            "start": st,
            "end": max(en, st + 0.5),
            "time_str": f"{fmt_time(st)} --> {fmt_time(max(en, st + 0.5))}"
        })
        
    # 時間單調性與非重疊保證
    for i in range(1, len(cues)):
        if cues[i]["start"] < cues[i-1]["start"]:
            cues[i]["start"] = cues[i-1]["end"]
            cues[i]["end"] = max(cues[i]["end"], cues[i]["start"] + 0.5)
            cues[i]["time_str"] = f"{fmt_time(cues[i]['start'])} --> {fmt_time(cues[i]['end'])}"
            
    return cues
