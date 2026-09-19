"""步驟 5：套用字幕校正表（LLM 鑑驗結果的落地工具）。

LLM 鑑驗會找出 ASR 的專有名詞與同音錯字（例：Playwright 被聽成 PlayLite／playitright、
「大語言模型」被聽成「大魚的模型」）。這類修正必須可稽核、可重跑，不能靠手動編輯 SRT——
手改無法追溯改了什麼，下次重新轉錄又得重來一遍。

設計原則：
  - 校正表是獨立的 JSON 檔，與字幕分離。重新轉錄後套同一張表即可，不必重做鑑驗。
  - 預設 dry-run：先印出每一筆會改什麼、改幾處，確認無誤才用 --apply 寫檔。
  - 每筆規則都會回報命中次數，**命中 0 次會特別標示** —— 這通常代表 ASR 這次聽成了
    別的錯字，規則該更新，而不是靜靜地不生效。
  - 只改字幕文字，絕不改動時間戳。

校正表格式（corrections.json）：
  {
    "rules": [
      {"from": "PlayLite", "to": "Playwright", "note": "工具名，ASR 常誤聽"},
      {"from": "大魚的模型", "to": "大語言模型", "note": "術語"}
    ]
  }
"""
import argparse
import json
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

TS_RE = re.compile(r"\d{2}:\d{2}:\d{2},\d{3}\s*-->\s*\d{2}:\d{2}:\d{2},\d{3}")


def apply_rules(srt_text: str, rules: list[dict]) -> tuple[str, list[dict]]:
    """逐行套用校正規則，回傳 (新字幕文字, 每條規則的命中統計)。

    只處理字幕內容行 —— 序號行與時間戳行原樣保留，確保時間軸絕不被規則誤傷。
    """
    lines = srt_text.splitlines()
    stats = [{"from": r["from"], "to": r["to"], "note": r.get("note", ""),
              "hits": 0, "samples": []} for r in rules]

    out_lines = []
    for ln in lines:
        # 序號行、時間戳行、空行一律不動
        if ln.strip() == "" or ln.strip().isdigit() or TS_RE.search(ln):
            out_lines.append(ln)
            continue
        new = ln
        for k, r in enumerate(rules):
            if r["from"] in new:
                cnt = new.count(r["from"])
                stats[k]["hits"] += cnt
                if len(stats[k]["samples"]) < 3:
                    stats[k]["samples"].append(ln.strip())
                new = new.replace(r["from"], r["to"])
        out_lines.append(new)
    return "\n".join(out_lines) + "\n", stats


def main() -> None:
    """解析參數，預設僅預覽；加 --apply 才實際寫檔。"""
    p = argparse.ArgumentParser(description="套用字幕校正表")
    p.add_argument("--srt", required=True, type=Path, help="來源 SRT")
    p.add_argument("--corrections", required=True, type=Path, help="校正表 JSON")
    p.add_argument("--out", type=Path, help="輸出 SRT（預設為來源檔名加 -corrected）")
    p.add_argument("--apply", action="store_true", help="實際寫檔；未指定則僅預覽")
    args = p.parse_args()

    rules = json.loads(args.corrections.read_text(encoding="utf-8"))["rules"]
    text = args.srt.read_text(encoding="utf-8")
    new_text, stats = apply_rules(text, rules)

    total = sum(s["hits"] for s in stats)
    dead = [s for s in stats if s["hits"] == 0]
    print(f"[05] 校正表 {len(rules)} 條規則，合計命中 {total} 處")
    print("-" * 60)
    for s in sorted(stats, key=lambda x: -x["hits"]):
        mark = "  " if s["hits"] else "!!"
        print(f"{mark} {s['hits']:3d} 處｜{s['from']} -> {s['to']}"
              + (f"｜{s['note']}" if s["note"] else ""))
        for sample in s["samples"]:
            print(f"       例：{sample}")
    if dead:
        print("-" * 60)
        print(f"[05] 警告：{len(dead)} 條規則命中 0 次 —— 多半是 ASR 這次聽成了別的錯字，"
              "請核對後更新校正表，不要讓規則靜默失效：")
        for s in dead:
            print(f"       {s['from']} -> {s['to']}")

    out = args.out or args.srt.with_name(args.srt.stem + "-corrected.srt")
    if args.apply:
        out.write_text(new_text, encoding="utf-8")
        print(f"[05] 已寫入：{out}")
    else:
        print(f"[05] 預覽模式，未寫檔。確認無誤後加 --apply 輸出至：{out}")


if __name__ == "__main__":
    main()
