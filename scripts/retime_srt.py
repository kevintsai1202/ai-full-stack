# -*- coding: utf-8 -*-
"""SRT 重新對時＋斷句邊界優化：以（人工修訂後的）字幕文字為權威，用 ASR 逐字時間戳重算時間。

用法（PowerShell）：
    python scripts/retime_srt.py `
        --srt "course-package/ch01-env-and-ai-workflow/02.srt" `
        --asr "course-package/ch01-env-and-ai-workflow/02.asr.json" `
        --redistribute-from 311

設計：
    1. 重新對時（全部字幕）：逐則字幕在 ASR 逐字流的「移動視窗」內做局部對齊
       （forced-alignment 式；全文一次性 SequenceMatcher 遇到重複片語會貪婪錯配，
       造成整段字幕對不到時間而被內插壓縮——實測 96~101 則塌縮成 0.4 秒）。
       正規化與 align_script_srt.py 相同：繁→簡、僅內容字元。
       人工新增／修正而 ASR 沒有的字元，以鄰近有時間字元線性內插。
    2. 邊界重新分配（--redistribute-from N 起）：對相鄰兩則的合併文字，在寬度
       允許（每則 ≤20、≥3，全形=1 半形=0.5）的切點中挑分數最高者：實際停頓
       越長越好、切點後是語句起始詞（好／那／然後…）加分、切點前是標點加分、
       離原邊界越遠小幅扣分。N 之前（人工修訂區域）的邊界不動。
    3. 超寬拆分（全檔）：寬度 >20 的字幕以相同評分找最佳切點拆成多則。
    原檔先備份 .retime.bak；執行後列出異動統計與最寬字幕供複查。
"""
import argparse
import difflib
import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
# 重用對齊腳本的正規化與 ASR 載入邏輯，確保兩邊行為一致
from align_script_srt import fmt_time, is_content_char, load_asr_chars, norm_char

sys.stdout.reconfigure(encoding="utf-8")  # Windows 主控台預設 cp950，避免中文亂碼

ROOT = Path(__file__).resolve().parent.parent

MAX_WIDTH = 20.0  # 單則字幕寬度上限（全形=1、半形=0.5）
MIN_WIDTH = 3.0   # 單則字幕寬度下限

# 語句起始詞：切點正好落在這些詞前面時加分（模仿人工斷句——新語句從這些詞開始）
STARTERS = ("好那", "接下來", "然後", "但是", "所以", "其實", "因為", "再來", "就是", "OK", "好", "那")
PUNCT_BEFORE = "，。！？；：、"  # 切點前一字是標點 → 加分


def char_width(ch: str) -> float:
    """單一字元顯示寬度：全形=1、半形=0.5。"""
    return 1.0 if unicodedata.east_asian_width(ch) in ("W", "F") else 0.5


def text_width(text: str) -> float:
    """整段文字顯示寬度。"""
    return sum(char_width(c) for c in text)


def parse_srt(path: Path) -> list[dict]:
    """讀入 SRT，回傳 [{start, end, text}]。容錯：略過人工編輯殘留的空行與行號行。"""
    cues = []
    for block in path.read_text(encoding="utf-8").strip().split("\n\n"):
        lines = [ln for ln in block.split("\n") if ln.strip()]
        while lines and "-->" not in lines[0]:
            lines.pop(0)
        if len(lines) < 2:
            continue
        m = re.match(r"(\d+):(\d+):(\d+),(\d+) --> (\d+):(\d+):(\d+),(\d+)", lines[0])
        if not m:
            continue
        st = int(m[1]) * 3600 + int(m[2]) * 60 + int(m[3]) + int(m[4]) / 1000
        en = int(m[5]) * 3600 + int(m[6]) * 60 + int(m[7]) + int(m[8]) / 1000
        cues.append({"start": st, "end": en, "text": " ".join(lines[1:])})
    return cues


def assign_char_times(cues: list[dict], asr_chars: list[dict]) -> tuple[list, list[int]]:
    """兩段式對齊：回傳 (全文字元時間表, 每則位移)。

    第一段：全文 SequenceMatcher 對齊（equal 直接對應、replace 線性配對），
    大部分字元在此取得時間。第二段：沒對上的字元缺口，以「缺口兩側已對上
    的 ASR 索引」界定範圍做局部二次對齊——修復全文對齊在重複片語處的貪婪
    錯配（實測會讓整段字幕對不到時間而塌縮），且錯誤不會向外擴散。
    """
    offsets: list[int] = []
    pos = 0
    for c in cues:
        offsets.append(pos)
        pos += len(c["text"])
    full = "".join(c["text"] for c in cues)
    times: list = [None] * len(full)
    # 內容字元序列（與 ASR 同樣正規化）
    c_pos = [i for i, ch in enumerate(full) if is_content_char(ch)]
    c_norm = [norm_char(full[i]) for i in c_pos]
    a_norm = [c["norm"] for c in asr_chars]

    # 第一段：全文對齊；asr_of[k] = 第 k 個內容字元對到的 ASR 索引
    asr_of: list[int | None] = [None] * len(c_norm)
    sm = difflib.SequenceMatcher(None, c_norm, a_norm, autojunk=False)
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            for off in range(i2 - i1):
                asr_of[i1 + off] = j1 + off
        elif tag == "replace":
            n = i2 - i1
            for off in range(n):
                asr_of[i1 + off] = j1 + min(int(off * (j2 - j1) / max(n, 1)), j2 - j1 - 1)

    # 第二段：對缺口（連續 None）在兩側已知 ASR 索引間做局部對齊
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

    for k, j in enumerate(asr_of):
        if j is not None:
            times[c_pos[k]] = (asr_chars[j]["start"], asr_chars[j]["end"])
    # 內插：沒有時間的內容字元，用前後最近的已知時間線性內插
    known = [(i, t) for i, t in enumerate(times) if t is not None]
    if not known:
        sys.exit("[對時] 全文與 ASR 完全對不上，無法重新對時")
    ki = 0
    for i in range(len(times)):
        if times[i] is not None or not is_content_char(full[i]):
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
    return times, offsets


def boundary_score(merged: str, t_slice: list, cut: int, orig_cut: int) -> float:
    """評分：切點停頓越長、後接語句起始詞、前為標點越好；離原邊界越遠小扣分。"""
    prev_i = next((i for i in range(cut - 1, -1, -1) if t_slice[i] is not None), None)
    next_i = next((i for i in range(cut, len(merged)) if t_slice[i] is not None), None)
    if prev_i is None or next_i is None:
        return -1e9
    gap = max(0.0, t_slice[next_i][0] - t_slice[prev_i][1])
    score = gap * 10.0  # 停頓每 0.1 秒 = 1 分
    rest = merged[cut:].lstrip()
    if any(rest.startswith(s) for s in STARTERS):
        score += 3.0
    if cut > 0 and merged[cut - 1] in PUNCT_BEFORE:
        score += 2.0
    score -= abs(cut - orig_cut) * 0.15
    return score


def redistribute(cues: list[dict], times: list, offsets: list[int], from_idx: int) -> int:
    """對 from_idx（0-based）起的相鄰字幕邊界重新選擇最佳切點，回傳移動數。

    只動 from_idx 之後的字幕文字（人工修訂區域不碰）。移動時不增刪字元、
    只把字元在相鄰兩則間重新分配，並同步更新位移表讓後續邊界計算正確。
    """
    moved = 0
    for i in range(from_idx, len(cues) - 1):
        a, b = cues[i], cues[i + 1]
        merged = a["text"] + b["text"]
        t_slice = times[offsets[i]:offsets[i] + len(merged)]
        orig_cut = len(a["text"])
        best_cut, best_score = orig_cut, boundary_score(merged, t_slice, orig_cut, orig_cut)
        for cut in range(1, len(merged)):
            left_w, right_w = text_width(merged[:cut]), text_width(merged[cut:])
            if not (MIN_WIDTH <= left_w <= MAX_WIDTH and MIN_WIDTH <= right_w <= MAX_WIDTH):
                continue
            s = boundary_score(merged, t_slice, cut, orig_cut)
            if s > best_score + 1e-9:
                best_cut, best_score = cut, s
        if best_cut != orig_cut:
            a["text"], b["text"] = merged[:best_cut], merged[best_cut:]
            offsets[i + 1] = offsets[i] + best_cut  # 邊界移動 → 更新下一則的位移
            moved += 1
    return moved


def split_overwide(cues: list[dict], times: list, offsets: list[int]) -> tuple[list[dict], int]:
    """把寬度 >MAX_WIDTH 的字幕以最佳切點拆成多則（全檔適用），回傳 (新列表, 拆分數)。"""
    out: list[dict] = []
    n_split = 0
    for cue, off in zip(cues, offsets):
        stack = [(cue["text"], off)]
        parts: list[tuple[str, int]] = []
        while stack:
            text, o = stack.pop(0)
            if text_width(text) <= MAX_WIDTH:
                parts.append((text, o))
                continue
            t_slice = times[o:o + len(text)]
            mid = len(text) // 2
            best_cut, best_score = None, -1e9
            for cut in range(1, len(text)):
                if text_width(text[:cut]) < MIN_WIDTH or text_width(text[cut:]) < MIN_WIDTH:
                    continue
                s = boundary_score(text, t_slice, cut, mid)
                if s > best_score:
                    best_cut, best_score = cut, s
            if best_cut is None:
                parts.append((text, o))
                continue
            n_split += 1
            stack.insert(0, (text[best_cut:], o + best_cut))
            stack.insert(0, (text[:best_cut], o))
        for text, o in parts:
            out.append({"text": text, "offset": o, "start": 0.0, "end": 0.0})
    return out, n_split


def main() -> None:
    parser = argparse.ArgumentParser(description="SRT 重新對時＋斷句邊界優化")
    parser.add_argument("--srt", required=True, help="要重新對時的 SRT 路徑（就地覆寫，先備份）")
    parser.add_argument("--asr", required=True, help="逐字時間戳 .asr.json 路徑")
    parser.add_argument("--redistribute-from", type=int, default=0,
                        help="從第 N 則（1-based）起優化斷句邊界；0＝不優化只對時")
    args = parser.parse_args()

    srt_path = (ROOT / args.srt) if not Path(args.srt).is_absolute() else Path(args.srt)
    asr_path = (ROOT / args.asr) if not Path(args.asr).is_absolute() else Path(args.asr)
    cues = parse_srt(srt_path)
    asr_chars = load_asr_chars(asr_path)
    srt_path.with_suffix(".srt.retime.bak").write_text(srt_path.read_text(encoding="utf-8"), encoding="utf-8")

    # 對時 → 尾段邊界優化 → 超寬拆分（邊界異動不增刪字元，時間表沿用；位移已同步更新）
    times, offsets = assign_char_times(cues, asr_chars)
    moved = 0
    if args.redistribute_from > 0:
        moved = redistribute(cues, times, offsets, args.redistribute_from - 1)
    new_cues, n_split = split_overwide(cues, times, offsets)

    for c in new_cues:
        spans = [times[c["offset"] + i] for i, ch in enumerate(c["text"]) if times[c["offset"] + i] is not None]
        if spans:
            c["start"], c["end"] = spans[0][0], spans[-1][1]
    # 沒有任何時間的字幕（罕見）：用前後字幕補
    for i, c in enumerate(new_cues):
        if c["end"] > 0.0:
            continue
        c["start"] = new_cues[i - 1]["end"] if i else 0.0
        c["end"] = c["start"] + 1.0
    # 時間軸保險：單調遞增、每則至少 0.4 秒
    for i, c in enumerate(new_cues):
        if i and c["start"] < new_cues[i - 1]["end"]:
            c["start"] = new_cues[i - 1]["end"]
        if c["end"] < c["start"] + 0.4:
            c["end"] = c["start"] + 0.4

    blocks = [f"{i}\n{fmt_time(c['start'])} --> {fmt_time(c['end'])}\n{c['text'].strip()}\n"
              for i, c in enumerate(new_cues, 1)]
    srt_path.write_text("\n".join(blocks), encoding="utf-8")
    widths = [text_width(c["text"].strip()) for c in new_cues]
    print(f"[對時] {len(cues)} → {len(new_cues)} 則；邊界移動 {moved} 處、超寬拆分 {n_split} 處；"
          f"最寬 {max(widths):.1f} 字（上限 {MAX_WIDTH:.0f}）")
    print(f"[對時] 原檔備份：{srt_path.with_suffix('.srt.retime.bak').name}")


if __name__ == "__main__":
    main()
