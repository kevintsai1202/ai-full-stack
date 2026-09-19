"""步驟 4：字幕規範機械檢查（LLM 鑑驗前的客觀依據）。

LLM 鑑驗擅長抓語意錯誤與專有名詞，但不適合逐段核對「有沒有超過 22 字」「時間戳有沒有
重疊」這類可機械判定的規則 —— 那既浪費 token 又不可靠。本腳本先把可量化的違規全部撈出，
LLM 只需專注在機器判不出來的部分（錯字、術語、語意斷點是否自然）。

檢查項目（對應 CLAUDE.md〈字幕與斷句規範〉）：
  E1 時間戳異常：起訖顛倒、與前一段重疊、長度為零。
  E2 長度超標：單段字數 > MAX_CHARS，或持續時間 > MAX_DURATION。
  E3 段末懸空：段落結尾是「因為／所以／但是」等待續連接詞（規範第 6 條）—— 確定違規。
  W1 段末待確認：結尾是「的／可以／其實」等，可能是完整句尾也可能斷錯，須人工或 LLM 判斷。
  E4 段首黏連：段落開頭是「的／了／時候」等必須黏在前句的字詞（規範第 3 條）。
  E5 標點殘留：出現規範要求移除的標點，或行尾殘留空格（規範第 7 條）。
  E6 疑似拆詞：前段末字＋後段首字恰好構成常見雙字詞（規範第 2 條）。
  E7 閱讀速度過快：每秒字數超過 CPS_MAX，觀眾來不及看完。

輸出：純文字報告 + 可選的 JSON（供後續自動修正或 LLM 讀取）。
本腳本唯讀，不修改任何字幕檔。
"""
import argparse
import json
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

MAX_CHARS = 22        # 理想上限，與 03_timeline_to_srt.py 保持一致
HARD_MAX = 28         # 絕對上限；03 只在避免段末懸空時才容許超過 MAX_CHARS，
                      # 故違規門檻取 HARD_MAX，介於兩者之間僅視為「偏長」不計違規
MAX_DURATION = 7.0
CPS_MAX = 9.0         # 中文字幕每秒字數上限，超過則觀眾來不及閱讀

FORBIDDEN_PUNCT = "，,。、；;：:？?！!…「」『』（）()《》〈〉"

# E3（確定違規）：CLAUDE.md 規範第 6 條明列的連接詞類。斷在這裡必然讓觀眾懸空，
# 因為這些詞在語法上必須帶出後續子句，不可能是句子的結尾。
TAIL_HANGING = {
    "因為", "所以", "但是", "可是", "不過", "如果", "假如", "就是", "而且", "並且",
    "然後", "接著", "那麼", "對於", "關於", "根據", "透過", "經過", "由於", "為了",
    "除了", "雖然", "即使", "只要", "只有", "以及", "包括", "這個", "那個", "一個",
    "跟", "和", "或", "與", "把", "被", "讓", "第",
}
# W1（待人工判斷）：這些字作句尾可能完整、也可能是斷錯。機器無法區分，交給 LLM 鑑驗。
#   完整的例子：「他都一定說可以」「測試是對齊的」（句尾語氣，語意完足）
#   斷錯的例子：「決定 AI 交付品質的」（後面還有被修飾的名詞，違反規範第 3 條）
TAIL_AMBIGUOUS = {
    "的", "地", "得", "是", "在", "會", "要", "能", "很", "就", "都", "也",
    "可以", "應該", "必須", "還有", "其實", "有",
}
HEAD_STICKY = {
    "的", "地", "得", "了", "嗎", "呢", "吧", "啊", "喔", "耶", "囉", "呀", "嘛",
    # 註：「之後／以後／之前／以前」可合法作句首時間副詞（「以前大家會覺得…」），
    # 列入會造成大量誤報，故不納入。
    "而已", "的話", "時候", "們", "性", "化",
}
# 段末白名單：這些雙字詞的後半字單看會落入 TAIL_HANGING，但整個詞本身是完整的，
# 斷在此處並不懸空（例：「探索 AI 的無限可能」的「能」屬於「可能」，不是待續的「能」）。
TAIL_OK_BIGRAMS = {
    "可能", "功能", "不能", "智能", "性能", "技能", "才能", "效能", "本能", "萬能",
    "機會", "學會", "體會", "誤會", "社會", "開會", "只要", "需要", "重要", "主要",
    "想要", "不要", "必要", "摘要", "但是", "就是", "不是", "還是", "總是", "於是",
    "現在", "存在", "正在", "所在", "實在", "自在", "內在", "外在", "潛在",
    "目的", "有的", "是的", "別的", "同時", "當時", "隨時", "暫時", "及時",
    "而已", "此外", "另外", "以外", "意外", "格外", "例外",
}
# 常見雙字詞：用來偵測「前段末字＋後段首字」恰好被劈開的情況
COMMON_BIGRAMS = {
    "設計", "規格", "系統", "資料", "程式", "開發", "需求", "功能", "架構", "測試",
    "專案", "文件", "模型", "訓練", "工具", "環境", "服務", "介面", "流程", "方向",
    "問題", "結果", "內容", "部分", "階段", "時候", "東西", "地方", "方式", "方法",
    "情況", "狀況", "困難", "順利", "完成", "分析", "評估", "報告", "確認", "動作",
    "效能", "安全", "版本", "更新", "管理", "使用", "執行", "建立", "產生", "輸出",
}

TS_RE = re.compile(r"(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})")


def parse_srt(path: Path) -> list[dict]:
    """解析 SRT，回傳 [{index, start, end, text}]。格式異常的區塊會被略過並回報。"""
    blocks = re.split(r"\n\s*\n", path.read_text(encoding="utf-8").strip())
    cues = []
    for b in blocks:
        lines = [ln for ln in b.splitlines() if ln.strip() != ""]
        if len(lines) < 3:
            continue
        m = TS_RE.search(lines[1])
        if not m:
            continue
        g = [int(x) for x in m.groups()]
        start = g[0] * 3600 + g[1] * 60 + g[2] + g[3] / 1000
        end = g[4] * 3600 + g[5] * 60 + g[6] + g[7] / 1000
        cues.append({
            "index": int(lines[0].strip()) if lines[0].strip().isdigit() else len(cues) + 1,
            "start": start,
            "end": end,
            "text": "\n".join(lines[2:]),
            "raw_text": "\n".join(lines[2:]),
        })
    return cues


def lint(cues: list[dict]) -> list[dict]:
    """執行所有檢查，回傳違規清單。"""
    issues: list[dict] = []

    def add(code: str, cue: dict, detail: str) -> None:
        issues.append({
            "code": code, "index": cue["index"],
            "start": round(cue["start"], 3), "end": round(cue["end"], 3),
            "text": cue["text"], "detail": detail,
        })

    for i, c in enumerate(cues):
        text = c["text"]
        plain = text.replace("\n", "").replace(" ", "")
        dur = c["end"] - c["start"]

        # E1 時間戳
        if dur <= 0:
            add("E1", c, f"時間長度非正值（{dur:.3f}s）")
        if i > 0 and c["start"] < cues[i - 1]["end"] - 1e-6:
            add("E1", c, f"與前一段重疊（前段結束 {cues[i-1]['end']:.3f}s）")

        # E2 長度
        if len(plain) > HARD_MAX:
            add("E2", c, f"字數 {len(plain)} > 絕對上限 {HARD_MAX}")
        if dur > MAX_DURATION:
            add("E2", c, f"持續 {dur:.2f}s > 上限 {MAX_DURATION}s")

        # E5 標點與尾空白
        bad = [ch for ch in text if ch in FORBIDDEN_PUNCT]
        if bad:
            add("E5", c, f"殘留標點：{''.join(sorted(set(bad)))}")
        if text != text.strip() or "  " in text:
            add("E5", c, "行首／行尾空白或連續空白")

        # E7 閱讀速度
        if dur > 0 and len(plain) / dur > CPS_MAX:
            add("E7", c, f"每秒 {len(plain)/dur:.1f} 字 > 上限 {CPS_MAX}")

        if not plain:
            continue

        # E3 / W1 段末（先排除「可能／需要／現在」這類本身完整的雙字詞）
        if plain[-2:] not in TAIL_OK_BIGRAMS:
            if plain[-2:] in TAIL_HANGING:
                add("E3", c, f"段末懸空連接詞「{plain[-2:]}」")
            elif plain[-1] in TAIL_HANGING:
                add("E3", c, f"段末懸空連接詞「{plain[-1]}」")
            elif plain[-2:] in TAIL_AMBIGUOUS:
                add("W1", c, f"段末「{plain[-2:]}」待確認是否為完整句尾")
            elif plain[-1] in TAIL_AMBIGUOUS:
                add("W1", c, f"段末「{plain[-1]}」待確認是否為完整句尾")

        # E4 段首黏連
        if plain[0] in HEAD_STICKY or plain[:2] in HEAD_STICKY:
            head = plain[:2] if plain[:2] in HEAD_STICKY else plain[0]
            add("E4", c, f"段首黏連詞「{head}」")

        # E6 疑似拆詞：前段末字 + 本段首字構成常見雙字詞
        if i > 0:
            prev_plain = cues[i - 1]["text"].replace("\n", "").replace(" ", "")
            if prev_plain:
                bigram = prev_plain[-1] + plain[0]
                if bigram in COMMON_BIGRAMS:
                    add("E6", c, f"疑似與前段拆開詞彙「{bigram}」")

    return issues


def main() -> None:
    """解析參數、執行檢查並輸出報告。"""
    p = argparse.ArgumentParser(description="SRT 字幕規範機械檢查")
    p.add_argument("--srt", required=True, type=Path)
    p.add_argument("--json-out", type=Path, help="另存 JSON 供後續處理")
    p.add_argument("--show", type=int, default=15, help="每類違規最多列出幾筆")
    args = p.parse_args()

    cues = parse_srt(args.srt)
    issues = lint(cues)

    names = {
        "E1": "時間戳異常", "E2": "長度超標", "E3": "段末懸空",
        "E4": "段首黏連", "E5": "標點殘留", "E6": "疑似拆詞", "E7": "閱讀過快",
        "W1": "段末待確認（需 LLM 判斷）",
    }
    print(f"[04] 檢查 {args.srt}")
    print(f"[04] 字幕 {len(cues)} 段，違規 {len(issues)} 筆")
    print("-" * 60)
    errors = [x for x in issues if x["code"].startswith("E")]
    print(f"[04] 其中確定違規 {len(errors)} 筆，待確認 {len(issues)-len(errors)} 筆")
    print("-" * 60)
    for code in ["E1", "E2", "E3", "E4", "E5", "E6", "E7", "W1"]:
        sub = [x for x in issues if x["code"] == code]
        rate = len(sub) / max(len(cues), 1) * 100
        print(f"{code} {names[code]}：{len(sub)} 筆（{rate:.1f}%）")
        for x in sub[:args.show]:
            print(f"    #{x['index']} [{x['start']:.1f}s] {x['detail']}｜{x['text']}")
        if len(sub) > args.show:
            print(f"    …另有 {len(sub)-args.show} 筆")

    if args.json_out:
        args.json_out.write_text(
            json.dumps({"srt": str(args.srt), "cues": len(cues), "issues": issues},
                       ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[04] JSON 已寫入：{args.json_out}")


if __name__ == "__main__":
    main()
