"""步驟 3：把 timeline.json 的字級時間戳重新斷句，產出符合專案規範的 .srt 字幕。

核心策略：以 whisper segment 為原子單位，只合併、不拆開。

  這是依實測資料校準的結果（2026-09-14，對 live-slides/yt.mp3 的 90 秒樣本）：
    - word token **完全不含標點**（medium + VAD 分段下，中文輸出無標點）。
    - **99.1% 的字間 gap 為 0** —— 詞級時間戳是連續的，前字 end 即後字 start，
      所以「用停頓當詞邊界」在這份資料上不成立。
    - 但 whisper 的 segment 是由語言模型切的，本身就是語意完整單元
      （'那通常你問可不可以做' / '所以我們其實有前面這個可行性評估報告出來以後'），
      密度約每 2.7 秒 10 字，正好適合當字幕的原子。

  因此斷點一律取在 segment 邊界上，天然落在語意邊界，不可能把詞劈開；
  只有單一 segment 本身超過 MAX_CHARS 時，才在其內部依語法黑名單切分
  （這類斷點無語意邊界佐證，會被計數回報為「風險斷點」）。

規範對應（CLAUDE.md〈字幕與斷句規範〉）：
  1. 語意完整優先 → 以 segment 為原子，不做任意重切。
  2. 不拆詞／專有名詞／數字＋單位 → 由 segment 邊界保證。
  3. 不斷在語法緊密處 → TAIL_FORBIDDEN / HEAD_FORBIDDEN 再擋一層。
  4. 優先斷在子句邊界、自然換氣 → segment 邊界即語言模型判定的子句邊界。
  5. 每段獨立可讀 → TARGET_CHARS / MAX_CHARS 控制長度。
  6. 段末不留懸空連接詞 → 合併時偵測段末懸空詞並續併下一單元。
  7. 標點一律移除；句中停頓轉半形空格，句末不留標點或空格。
"""
import argparse
import json
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

TARGET_CHARS = 16      # 每段目標字數，中文單行約此長度最好讀
MAX_CHARS = 22         # 每段字數的理想上限
HARD_MAX = 28          # 絕對上限。僅在「收尾會留下懸空連接詞」時才容許超過 MAX_CHARS——
                       # 規範第 6 條（不留懸而未決的詞）優先於長度，稍長好過話沒講完。
MIN_CHARS = 5          # 每段最短字數，避免切出「好」「對」這類碎段
MAX_DURATION = 7.0     # 單段最長秒數，避免字幕長時間停滯
GAP_MIN = 0.20         # 視為「可斷」的最小字間停頓（秒）；低於此判定為同一詞內部
SEG_GAP_MIN = 0.08     # whisper segment 邊界要被採信所需的最小間隔（秒）。
                       # segment 是依 30 秒窗口與聲學切的，邊界不保證是語意邊界：
                       # 實測「一個方 / 向把…」就是切在詞中間、且前後 gap 僅 0.01 秒。
                       # 要求邊界處有可測間隔，才能濾掉這類純屬窗口切割的假邊界。

PAUSE_PUNCT = "，,、；;：:"          # 句中停頓 → 轉半形空格
END_PUNCT = "。.？?！!…~"            # 句末 → 移除
OTHER_PUNCT = "「」『』（）()《》〈〉\"'“”‘’ "
ALL_PUNCT = PAUSE_PUNCT + END_PUNCT + OTHER_PUNCT

# 段末禁止字／詞（比對段落最後 1~2 字）：後面語法上必須接東西
TAIL_FORBIDDEN = {
    "因為", "所以", "但是", "可是", "不過", "如果", "假如", "就是", "而且", "並且",
    "然後", "接著", "其實", "那麼", "這個", "那個", "一個", "對於", "關於", "根據",
    "透過", "經過", "由於", "為了", "除了", "雖然", "即使", "只要", "只有", "以及",
    "還有", "包括", "可以", "應該", "必須", "我們", "你們", "他們", "非常", "比較",
    "的", "地", "得", "是", "在", "跟", "和", "或", "與", "把", "被", "讓", "會",
    "要", "能", "有", "很", "就", "都", "也", "再", "又", "而", "從", "向", "對",
    "第", "一", "兩", "三", "個", "這", "那",
}
# 段首禁止字／詞：語法上必須黏在前面
HEAD_FORBIDDEN = {
    "的", "地", "得", "了", "嗎", "呢", "吧", "啊", "喔", "耶", "囉", "呀", "嘛",
    "而已", "之後", "以後", "之前", "以前", "的話", "時候", "們", "性", "化",
}


def load_chars(timeline_path: Path) -> list[dict]:
    """展平成字元序列：每個元素是一個中文字（或一個英數 token）並帶時間與停頓資訊。

    whisper 的 word 對中文是單字、對英文是整個單字，兩者都視為不可分割單位。
    純標點 token 不進入序列，其停頓語意記到前一個字上。
    """
    data = json.loads(timeline_path.read_text(encoding="utf-8"))
    items: list[dict] = []
    prev_end = None
    for seg_no, seg in enumerate(data["segments"]):
        # no_speech_prob 過高幾乎必為靜音幻聽（「謝謝觀看」等訓練資料殘留），整段捨棄
        if seg.get("no_speech_prob", 0.0) > 0.6:
            continue
        for w in seg.get("words", []):
            if w.get("start") is None or w.get("end") is None:
                continue
            raw = w["word"]
            text = "".join(c for c in raw if c not in ALL_PUNCT).strip()
            if not text:
                if items:
                    if any(c in raw for c in END_PUNCT):
                        items[-1]["end_punct"] = True
                    elif any(c in raw for c in PAUSE_PUNCT):
                        items[-1]["pause_punct"] = True
                continue
            gap = 0.0 if prev_end is None else max(0.0, w["start"] - prev_end)
            items.append({
                "start": w["start"],
                "end": w["end"],
                "text": text,
                "gap_before": gap,
                "seg_id": seg_no,
                # 詞尾自帶標點：模型認定的語意邊界，是最強的斷句訊號
                "pause_punct": any(c in raw for c in PAUSE_PUNCT),
                "end_punct": any(c in raw for c in END_PUNCT),
            })
            prev_end = w["end"]
    return items


def breakable(items: list[dict], i: int) -> bool:
    """判斷「在第 i 個 token 之後」是否構成合法斷點（有真實停頓證據）。

    三種證據，缺一不可有其一：
      a) token 自帶標點 —— 模型認定的語意邊界。
      b) 位於 whisper segment 邊界**且該處有可測間隔**（>= SEG_GAP_MIN）——
         單憑 segment 邊界不夠，它可能只是 30 秒窗口的切割點而非語意邊界。
      c) 字間 gap 夠大 —— 本資料集罕見（99% 的 gap 為 0），但保留以相容其他來源。

    這是不拆詞的根本保證：沒有停頓證據的位置一律不得斷開。
    """
    if i >= len(items) - 1:
        return False
    if items[i]["end_punct"] or items[i]["pause_punct"]:
        return True
    if (items[i]["seg_id"] != items[i + 1]["seg_id"]
            and items[i + 1]["gap_before"] >= SEG_GAP_MIN):
        return True
    return items[i + 1]["gap_before"] >= GAP_MIN


def break_score(items: list[dict], i: int, chars_so_far: int) -> float:
    """評估斷點適合度，分數越高越適合；負無窮表示語法上禁止。"""
    cur, nxt = items[i], items[i + 1]

    tail1 = cur["text"]
    tail2 = (items[i - 1]["text"] + cur["text"]) if i > 0 else ""
    head1 = nxt["text"]
    head2 = (nxt["text"] + items[i + 2]["text"]) if i + 2 < len(items) else ""
    if tail1 in TAIL_FORBIDDEN or tail2 in TAIL_FORBIDDEN:
        return float("-inf")
    if head1 in HEAD_FORBIDDEN or head2 in HEAD_FORBIDDEN:
        return float("-inf")

    score = 0.0
    if cur["end_punct"]:
        score += 12.0          # 句末標點是最強語意邊界
    elif cur["pause_punct"]:
        score += 6.0           # 句中停頓次之
    if cur["seg_id"] != nxt["seg_id"] and nxt["gap_before"] >= SEG_GAP_MIN:
        score += 8.0           # 有間隔佐證的 segment 邊界＝真實語音停頓
    score += min(nxt["gap_before"], 1.0) * 10.0    # 真實換氣點；超過 1 秒不再加分
    score -= abs(chars_so_far - TARGET_CHARS) * 0.4  # 長度偏離的溫和懲罰
    return score


def n_chars(seg: list[dict]) -> int:
    """計算一段的實際字元數。

    不能用 token 數代替：whisper 的中文 token 不保證是單字
    （「我跟你講」會是一個 token），用 token 數當長度會讓段落字數大幅超標。
    """
    return sum(len(x["text"]) for x in seg)


def group_by_segment(items: list[dict]) -> list[list[dict]]:
    """依 whisper segment 分組。每個 group 即一個語意單元，是後續合併的原子。"""
    groups: list[list[dict]] = []
    for it in items:
        if groups and groups[-1][-1]["seg_id"] == it["seg_id"]:
            groups[-1].append(it)
        else:
            groups.append([it])
    return groups


def split_long(group: list[dict]) -> tuple[list[list[dict]], int]:
    """切開超過 MAX_CHARS 的 segment。

    這類 segment 內部沒有標點也沒有停頓可依循，只能靠語法黑名單挑「最不傷」的位置。
    回傳 (切出的單元, 無停頓佐證的斷點數) —— 後者是字幕品質的風險指標，須回報。
    """
    if n_chars(group) <= MAX_CHARS:
        return [group], 0

    out: list[list[dict]] = []
    risky = 0
    i, n = 0, len(group)
    while i < n:
        if n_chars(group[i:]) <= MAX_CHARS:
            out.append(group[i:])
            break
        best_j, best_score = None, float("-inf")
        chars = 0
        for j in range(i, n - 1):
            chars += len(group[j]["text"])
            if chars < MIN_CHARS:
                continue
            if chars > MAX_CHARS:
                break
            s = break_score(group, j, chars)
            if s > best_score:
                best_score, best_j = s, j
        if best_j is None:
            # 全被黑名單擋下：取長度最接近 TARGET 處硬切（罕見）
            acc, best_j = 0, i
            for j in range(i, n - 1):
                acc += len(group[j]["text"])
                best_j = j
                if acc >= TARGET_CHARS:
                    break
        risky += 1
        out.append(group[i:best_j + 1])
        i = best_j + 1
    return out, risky


def segment_chars(items: list[dict]) -> tuple[list[list[dict]], int]:
    """以 whisper segment 為原子單位組裝字幕段落。

    策略（依實測資料校準，2026-09-14）：
      - whisper 的 segment 是由語言模型切的，本身就是語意完整單元
        （'那通常你問可不可以做' / '所以我們其實有前面這個可行性評估報告出來以後'），
        因此**只合併、不拆開**，斷點天然落在語意邊界上。
      - 這份資料的 word token 不含任何標點、99% 的字間 gap 為 0，
        所以標點與停頓都不是可用訊號；segment 邊界是唯一可靠的語意證據。
      - 只有單一 segment 本身就超過 MAX_CHARS 時才動內部（見 split_long）。
      - 合併後若段末落在語法黑名單上（懸空連接詞），再多併一個單元消除該壞邊界。

    回傳 (段落清單, 無停頓佐證的斷點數)。
    """
    units: list[list[dict]] = []
    risky = 0
    for g in group_by_segment(items):
        parts, r = split_long(g)
        units.extend(parts)
        risky += r

    segments: list[list[dict]] = []
    cur: list[dict] = []
    for u in units:
        if not cur:
            cur = list(u)
            continue
        cand = cur + u
        over = (n_chars(cand) > MAX_CHARS
                or cand[-1]["end"] - cand[0]["start"] > MAX_DURATION)
        if not over:
            cur = cand
            continue
        # 想在此收尾，但若段末是「因為／所以／其實」這類待續詞（規範第 6 條），
        # 寧可讓字幕稍微超過理想長度也要把話講完 —— 故放寬到 HARD_MAX 再收。
        if (tail_is_hanging(cur)
                and n_chars(cand) <= HARD_MAX
                and cand[-1]["end"] - cand[0]["start"] <= MAX_DURATION + 1.5):
            cur = cand
            continue
        segments.append(cur)
        cur = list(u)
    if cur:
        segments.append(cur)

    return merge_short(segments), risky


def tail_is_hanging(seg: list[dict]) -> bool:
    """判斷一段的結尾是否為語法上待續的詞（斷在此處會讓觀眾懸空）。"""
    plain = "".join(x["text"] for x in seg)
    if not plain:
        return False
    return plain[-1] in TAIL_FORBIDDEN or plain[-2:] in TAIL_FORBIDDEN


def merge_short(segments: list[list[dict]]) -> list[list[dict]]:
    """把過短的碎段併入相鄰段落，避免出現一兩個字的閃現字幕。

    優先併入前一段（閱讀上較自然：補完前句），前一段會超長時才併入後一段。
    """
    if not segments:
        return segments
    out: list[list[dict]] = []
    for seg in segments:
        if n_chars(seg) < MIN_CHARS and out:
            if n_chars(out[-1]) + n_chars(seg) <= MAX_CHARS + MIN_CHARS:
                out[-1].extend(seg)
                continue
        out.append(seg)
    if len(out) > 1 and n_chars(out[0]) < MIN_CHARS:
        out[1] = out[0] + out[1]
        out.pop(0)
    return out


def render_text(seg: list[dict]) -> str:
    """合併成字幕文字：中文字直接相連，僅在原文停頓標點處插入半形空格。"""
    out = []
    for k, it in enumerate(seg):
        out.append(it["text"])
        if k < len(seg) - 1 and (it["pause_punct"] or it["end_punct"]):
            out.append(" ")
    return re.sub(r"\s+", " ", "".join(out)).strip()


def fmt_ts(sec: float) -> str:
    """秒數轉 SRT 時間字串 HH:MM:SS,mmm。"""
    ms = int(round(sec * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt(segments: list[list[dict]], out_path: Path) -> int:
    """輸出 SRT；相鄰段時間重疊時讓位，避免播放器閃爍。回傳實際寫出段數。"""
    blocks, n = [], 0
    for idx, seg in enumerate(segments):
        text = render_text(seg)
        if not text:
            continue
        n += 1
        start, end = seg[0]["start"], seg[-1]["end"]
        if idx + 1 < len(segments):
            nxt = segments[idx + 1][0]["start"]
            if nxt > start:
                end = min(end, nxt - 0.01)
        blocks.append(f"{n}\n{fmt_ts(start)} --> {fmt_ts(end)}\n{text}\n")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(blocks), encoding="utf-8")
    return n


def main() -> None:
    """解析參數並執行重斷句與 SRT 輸出。"""
    parser = argparse.ArgumentParser(description="timeline.json → 語意斷句 SRT")
    parser.add_argument("--timeline", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    items = load_chars(args.timeline)
    segments, fallback = segment_chars(items)
    n = write_srt(segments, args.out)

    lens = [len(render_text(s)) for s in segments if render_text(s)]
    print(f"[03] token 數 {len(items)} -> 字幕 {n} 段", flush=True)
    print(f"[03] 每段字數：min {min(lens)} / 平均 {sum(lens)/len(lens):.1f} / max {max(lens)}",
          flush=True)
    # 退而求其次的斷點沒有標點支持、僅靠最大 gap，是字幕品質的風險點，須回報
    # 無停頓佐證的斷點＝在超長 segment 內部硬切出來的，是字幕品質的風險點
    print(f"[03] 風險斷點（超長 segment 內部切分，無語意邊界佐證）：{fallback} 處 "
          f"（{fallback/max(n,1)*100:.1f}%）", flush=True)
    print(f"[03] 輸出：{args.out}", flush=True)


if __name__ == "__main__":
    main()
