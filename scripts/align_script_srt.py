# -*- coding: utf-8 -*-
"""以「實際口白（ASR）為主、原稿為輔」產生 SRT 字幕。

用法（PowerShell）：
    python scripts/align_script_srt.py `
        --script "course-package/00-course-orientation-旁白重錄稿.md" `
        --asr    "course-package/ch01-env-and-ai-workflow/00.asr.json" `
        --out    "course-package/ch01-env-and-ai-workflow/00.srt"

設計（權威順序：口白 > 原稿）：
    1. 字幕內容以 ASR 逐字辨識結果（實際口白）為主；
       原稿只用來「修正 ASR 誤認」與提供標點斷句位置：
       - 兩邊一致 → 直接用原稿寫法（繁體、英文拼寫正確）。
       - 兩邊不同（replace）→ 讀音比對（pypinyin 無聲調拼音）：
         讀音相同＝ASR 同音誤認 → 用原稿修正（例：十做→實作、哈豪→Hahow）；
         讀音不同＝口白真的偏離原稿 → 保留口白（例：準備好→安裝好）。
         原稿片段含英文/數字時一律視為專有名詞，用原稿修正（图口令→Tool Calling）。
       - 口白多唸（insert）→ 保留，OpenCC s2twp 轉繁體。
       - 口白沒唸（delete）→ 捨棄；唯英文單字內部的缺字（ASR 拼不全，
         如 Antigravity 被辨成 ntgravity）補回原稿字母，避免專有名詞殘缺。
    2. 斷句：只在原稿標點處與口白停頓（間隔 > 0.8 秒）處斷；
       單則寬度上限 20 字（全形=1、半形=0.5），超過時從「最接近中間」的
       斷點二分，四層優先序：口白停頓（≥0.25 秒，語句邊界）> 頓號／破折號
       > 英文空格 > 全形字邊界，絕不把一段話從中間切開、不切開英文單字。
    3. 另輸出 --report 差異報告，標明每處不一致最後採用了哪一邊，供人工核對。

前置：先用 scripts/asr_to_srt.py 產生 .asr.json 逐字時間戳快取。
"""
import argparse
import difflib
import json
import sys
import unicodedata
from pathlib import Path

from opencc import OpenCC
from pypinyin import lazy_pinyin

sys.stdout.reconfigure(encoding="utf-8")  # Windows 主控台預設 cp950，避免中文亂碼

ROOT = Path(__file__).resolve().parent.parent

T2S = OpenCC("t2s")    # 繁→簡：只用於對齊比對
S2TWP = OpenCC("s2twp")  # 簡→繁（含台灣用語）：口白 insert 片段輸出用

MAX_WIDTH = 20.0   # 單則字幕寬度上限：全形字算 1、半形字算 0.5
MIN_WIDTH = 3.0    # 避免切出過短的孤兒字幕
GAP_BREAK = 0.8    # 口白停頓超過此秒數視為斷句點（主要用於 insert 片段）
PAUSE_SPLIT = 0.25  # 超寬二分時：停頓超過此秒數視為語句邊界（最高優先，避免把一段話從中間切開）
PINYIN_SAME = 0.55  # replace 讀音相似度 ≥ 此值判定為 ASR 同音誤認

MAJOR_PUNCT = "。！？；：，"   # 主要斷點：在此標點後結束一則字幕
MINOR_PUNCT = "、—"            # 次要斷點：超寬二分時最優先的位置
OPEN_PUNCT = "「『（"          # 開引號：附掛到後一個字
STRIP_TRAILING = "，。、；：—"  # 字幕尾端要移除的標點（？！保留語氣）


def char_width(ch: str) -> float:
    """單一字元顯示寬度：全形（CJK 等）=1、半形=0.5。"""
    return 1.0 if unicodedata.east_asian_width(ch) in ("W", "F") else 0.5


def text_width(text: str) -> float:
    """整段文字顯示寬度。"""
    return sum(char_width(c) for c in text)


def is_content_char(ch: str) -> bool:
    """是否為參與對齊的內容字元（排除標點、空白、引號、破折號）。"""
    return ch.isalnum()


def extract_script_text(md_path: Path) -> str:
    """從旁白重錄稿 md 抽出旁白本文（略過標題、備註、檔名清單等）。"""
    lines = md_path.read_text(encoding="utf-8").splitlines()
    paragraphs: list[str] = []
    in_body = False
    in_code = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            continue
        if stripped.startswith("## "):
            if "交付檔案命名" in stripped:
                break
            in_body = True
            continue
        if not in_body or not stripped:
            continue
        if stripped.startswith(("#", ">", "---", "建議檔名", "參考長度", "人工重錄備註")):
            continue
        paragraphs.append(stripped)
    return "".join(paragraphs)


def norm_char(ch: str) -> str:
    """單字元正規化：繁→簡、小寫（供對齊比對）。"""
    s = T2S.convert(ch)
    return (s[0] if s else ch).lower()


def load_asr_chars(asr_path: Path) -> list[dict]:
    """把 .asr.json 逐字 segments 展開成字元流，每字帶時間戳與原始字。

    英文 token（如 Hello）拆成多字元，時間在 token 區間內線性內插。
    """
    segments = json.loads(asr_path.read_text(encoding="utf-8"))
    chars: list[dict] = []
    for seg in segments:
        content = [c for c in seg["text"] if is_content_char(c)]
        if not content:
            continue
        step = (seg["end"] - seg["start"]) / len(content)
        for k, ch in enumerate(content):
            chars.append({
                "raw": ch,                    # ASR 原始字（簡體）
                "norm": norm_char(ch),        # 對齊用正規化字
                "start": seg["start"] + k * step,
                "end": seg["start"] + (k + 1) * step,
            })
    return chars


def pinyin_key(text: str) -> str:
    """取無聲調拼音字串（先轉簡體讓 pypinyin 發音更穩）。"""
    return " ".join(lazy_pinyin(T2S.convert(text)))


def same_pronunciation(a: str, b: str) -> bool:
    """判斷兩段文字讀音是否足夠相似（ASR 同音誤認的判準）。"""
    ratio = difflib.SequenceMatcher(None, pinyin_key(a), pinyin_key(b)).ratio()
    return ratio >= PINYIN_SAME


def trailing_punct(script_text: str, orig_i: int) -> tuple[str, int]:
    """取原稿內容字元後緊接的標點串，回傳 (標點文字, 斷句等級 0/1/2)。"""
    punct = ""
    level = 0
    j = orig_i + 1
    while j < len(script_text) and not is_content_char(script_text[j]):
        ch = script_text[j]
        if not ch.isspace():
            punct += ch
        if ch in MAJOR_PUNCT:
            level = 2
        elif ch in MINOR_PUNCT and level < 2:
            level = 1
        j += 1
    return punct, level


def build_tokens(script_text: str, asr_chars: list[dict]) -> tuple[list[dict], list[str]]:
    """對齊口白與原稿，產生輸出 token 流（口白為主、原稿修正）。

    token 欄位：text（輸出文字）、start/end（秒）、brk（斷句等級 0/1/2）。
    回傳 (tokens, 差異報告列表)。
    """
    # 原稿內容字元序列與對應原文索引
    s_norm: list[str] = []
    s_orig: list[int] = []
    for i, ch in enumerate(script_text):
        if is_content_char(ch):
            s_norm.append(norm_char(ch))
            s_orig.append(i)
    a_norm = [c["norm"] for c in asr_chars]

    matcher = difflib.SequenceMatcher(None, s_norm, a_norm, autojunk=False)
    tokens: list[dict] = []
    diffs: list[str] = []
    pending_open = ""  # 待附掛到下一 token 的開引號

    def emit(text: str, start: float, end: float, brk: int) -> None:
        """輸出一個 token（自動附掛先前的開引號）。"""
        nonlocal pending_open
        tokens.append({"text": pending_open + text, "start": start, "end": end, "brk": brk})
        pending_open = ""

    def emit_script_char(idx_in_seq: int, start: float, end: float) -> None:
        """以原稿字元輸出（含其後標點與斷句等級、開引號附掛）。"""
        nonlocal pending_open
        orig_i = s_orig[idx_in_seq]
        # 檢查此字前是否有開引號（且前一字元不是內容字元才算「開頭」）
        if orig_i > 0 and script_text[orig_i - 1] in OPEN_PUNCT:
            pending_open += script_text[orig_i - 1]
        punct, level = trailing_punct(script_text, orig_i)
        # 空一格的英文邊界：原稿此字與下一內容字之間有空白 → 補空格
        space = ""
        j = orig_i + 1
        while j < len(script_text) and not is_content_char(script_text[j]):
            j += 1
        if j < len(script_text) and " " in script_text[orig_i + 1:j] and not punct:
            space = " "
        emit(script_text[orig_i] + punct + space, start, end, level)

    def asr_time(j: int) -> tuple[float, float]:
        c = asr_chars[min(j, len(asr_chars) - 1)]
        return c["start"], c["end"]

    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            for off in range(i2 - i1):
                st, en = asr_time(j1 + off)
                emit_script_char(i1 + off, st, en)
        elif tag == "insert":
            # 口白多唸：保留，整段 s2twp 轉繁體
            run = "".join(asr_chars[j]["raw"] for j in range(j1, j2))
            trad = S2TWP.convert(run)
            st, en = asr_chars[j1]["start"], asr_chars[j2 - 1]["end"]
            diffs.append(f"[insert ] {st:6.1f}s｜口白多唸「{trad}」→ 保留口白")
            # 逐字輸出以保留字級時間
            for k, j in enumerate(range(j1, j2)):
                ch = trad[k] if k < len(trad) else asr_chars[j]["raw"]
                emit(ch, asr_chars[j]["start"], asr_chars[j]["end"], 0)
        elif tag == "delete":
            frag = "".join(script_text[s_orig[i]] for i in range(i1, i2))
            # 英文單字內部缺字（ASR 拼不全）→ 補回原稿字母，時間掛前一 token 尾
            keep: list[int] = []
            for i in range(i1, i2):
                orig_i = s_orig[i]
                prev_adj = orig_i > 0 and script_text[orig_i - 1].isascii() and is_content_char(script_text[orig_i - 1])
                next_adj = orig_i + 1 < len(script_text) and script_text[orig_i + 1].isascii() and is_content_char(script_text[orig_i + 1])
                if script_text[orig_i].isascii() and (prev_adj or next_adj):
                    keep.append(i)
            if keep:
                t = tokens[-1]["end"] if tokens else 0.0
                for i in keep:
                    emit_script_char(i, t, t)
                diffs.append(f"[delete ] {'':>6}｜口白漏「{frag}」→ 補回英文字母「{''.join(script_text[s_orig[i]] for i in keep)}」")
            else:
                st = asr_chars[j1]["start"] if j1 < len(asr_chars) else None
                stamp = f"{st:6.1f}s" if st is not None else "  尾端"
                diffs.append(f"[delete ] {stamp}｜口白沒唸「{frag}」→ 捨棄")
        else:  # replace
            s_frag = "".join(script_text[s_orig[i]] for i in range(i1, i2))
            a_run = "".join(asr_chars[j]["raw"] for j in range(j1, j2))
            st = asr_chars[j1]["start"]
            has_ascii = any(c.isascii() for c in s_frag)
            if has_ascii or same_pronunciation(s_frag, a_run):
                # ASR 同音誤認或英文專有名詞 → 用原稿修正，時間線性配對
                reason = "英文專有名詞" if has_ascii else "同音誤認"
                diffs.append(f"[replace] {st:6.1f}s｜口白辨成「{a_run}」→ 依原稿修正為「{s_frag}」（{reason}）")
                n = i2 - i1
                for off in range(n):
                    j = j1 + min(int(off * (j2 - j1) / max(n, 1)), j2 - j1 - 1)
                    t_st, t_en = asr_time(j)
                    emit_script_char(i1 + off, t_st, t_en)
            else:
                # 讀音不同 → 口白真的偏離原稿，保留口白
                trad = S2TWP.convert(a_run)
                diffs.append(f"[replace] {st:6.1f}s｜原稿「{s_frag}」但口白唸「{trad}」→ 保留口白")
                for k, j in enumerate(range(j1, j2)):
                    ch = trad[k] if k < len(trad) else asr_chars[j]["raw"]
                    emit(ch, asr_chars[j]["start"], asr_chars[j]["end"], 0)

    sim = matcher.ratio()
    diffs.insert(0, f"整體相似度：{sim:.1%}（原稿 {len(s_norm)} 字 vs 口白 {len(a_norm)} 字）")
    return tokens, diffs


def split_long(grp: list[dict], base: int) -> list[tuple[int, int]]:
    """對超寬的 token 區段「從中間」遞迴二分，回傳 token 索引區間列表。

    斷點四層優先序（高 → 低）：
      0. 口白實際停頓 ≥ PAUSE_SPLIT 秒（語句邊界，優先於一切，不把一段話從中間切開）
      1. 頓號／破折號（token 尾）
      2. 英文空格（token 尾）
      3. 全形字邊界
    候選依（層級, 離中點距離）排序後逐一嘗試；切出過短片段時換下一個候選，
    而非放棄整段（舊版一失敗就放棄，造成含英文空格的長句永遠切不開）。
    """
    joined = "".join(t["text"] for t in grp)
    if text_width(joined) <= MAX_WIDTH:
        return [(base, base + len(grp))]
    mid = len(grp) / 2
    candidates: list[tuple[int, float, int]] = []  # (層級, 離中點距離, 切點索引)
    for i in range(1, len(grp)):
        prev, cur = grp[i - 1]["text"], grp[i]["text"]
        if not prev or not cur:
            continue
        if grp[i]["start"] - grp[i - 1]["end"] >= PAUSE_SPLIT:
            tier = 0
        elif prev[-1] in MINOR_PUNCT:
            tier = 1
        elif prev.endswith(" "):
            tier = 2
        elif char_width(prev[-1]) == 1.0 and char_width(cur[0]) == 1.0:
            tier = 3
        else:
            continue
        candidates.append((tier, abs(i - mid), i))
    for _tier, _dist, cut in sorted(candidates):
        left, right = grp[:cut], grp[cut:]
        if text_width("".join(t["text"] for t in left)) < MIN_WIDTH:
            continue
        if text_width("".join(t["text"] for t in right)) < MIN_WIDTH:
            continue
        return split_long(left, base) + split_long(right, base + cut)
    return [(base, base + len(grp))]


def build_cues(tokens: list[dict]) -> list[dict]:
    """token 流 → 字幕列表：先依主要標點與口白停頓切句，再做寬度二分。"""
    # 第一階段：主要標點（brk==2）與時間停頓切成句群
    groups: list[list[dict]] = []
    cur: list[dict] = []
    for k, tok in enumerate(tokens):
        cur.append(tok)
        nxt = tokens[k + 1] if k + 1 < len(tokens) else None
        pause = nxt is not None and (nxt["start"] - tok["end"]) > GAP_BREAK
        if tok["brk"] == 2 or pause or nxt is None:
            groups.append(cur)
            cur = []
    # 第二階段：每句群做寬度上限二分
    cues: list[dict] = []
    for grp in groups:
        for s_i, e_i in split_long(grp, 0):
            seg = grp[s_i:e_i]
            text = "".join(t["text"] for t in seg).strip()
            text = text.strip(STRIP_TRAILING).strip()
            if not text or all(not is_content_char(c) for c in text):
                continue
            cues.append({"start": seg[0]["start"], "end": seg[-1]["end"], "text": text})
    # 時間軸保險：單調遞增、長度為正
    for i in range(1, len(cues)):
        if cues[i]["start"] < cues[i - 1]["end"]:
            cues[i]["start"] = cues[i - 1]["end"]
        if cues[i]["end"] < cues[i]["start"]:
            cues[i]["end"] = cues[i]["start"] + 0.5
    return cues


def fmt_time(seconds: float) -> str:
    """秒數 → SRT 時間格式 HH:MM:SS,mmm。"""
    ms = round(seconds * 1000)
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1_000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def main() -> None:
    parser = argparse.ArgumentParser(description="口白為主×原稿修正 產生 SRT")
    parser.add_argument("--script", required=True, help="旁白原稿 md 路徑")
    parser.add_argument("--asr", required=True, help="asr_to_srt.py 產生的 .asr.json 路徑")
    parser.add_argument("--out", required=True, help="輸出 SRT 路徑")
    parser.add_argument("--report", help="差異報告輸出路徑（預設：SRT 同名 .diff.txt）")
    args = parser.parse_args()

    script_path = (ROOT / args.script) if not Path(args.script).is_absolute() else Path(args.script)
    asr_path = (ROOT / args.asr) if not Path(args.asr).is_absolute() else Path(args.asr)
    out_path = (ROOT / args.out) if not Path(args.out).is_absolute() else Path(args.out)
    report_path = Path(args.report) if args.report else out_path.with_suffix(".diff.txt")

    script_text = extract_script_text(script_path)
    asr_chars = load_asr_chars(asr_path)
    tokens, diffs = build_tokens(script_text, asr_chars)
    cues = build_cues(tokens)

    blocks = [f"{i}\n{fmt_time(c['start'])} --> {fmt_time(c['end'])}\n{c['text']}\n" for i, c in enumerate(cues, 1)]
    out_path.write_text("\n".join(blocks), encoding="utf-8")
    report_path.write_text("\n".join(diffs) + "\n", encoding="utf-8")

    widths = [text_width(c["text"]) for c in cues]
    print(f"[ALIGN] 口白 {len(asr_chars)} 字 → {len(cues)} 則字幕，最寬 {max(widths):.1f} 字（上限 {MAX_WIDTH:.0f}）")
    over = [c["text"] for c, w in zip(cues, widths) if w > MAX_WIDTH]
    if over:
        print(f"[ALIGN][警告] {len(over)} 則超寬：", *over, sep="\n  ")
    print(f"[ALIGN] 字幕：{out_path}")
    print(f"[ALIGN] 差異報告：{report_path}（{len(diffs) - 1} 處，含採用側說明）")


if __name__ == "__main__":
    main()
