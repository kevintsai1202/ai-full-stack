# -*- coding: utf-8 -*-
"""SRT 專有名詞校正：修正 ASR 同音／誤認詞，不改動語句內容。

用法（PowerShell）：
    python scripts/fix_srt_terms.py course-package/ch01-env-and-ai-workflow/02.srt

設計原則（承 align_script_srt.py 的「口白為主」）：
    只替換「確定是 ASR 誤認」的詞——同音字（城市→程式）、專有名詞拼錯
    （NTEGRAT→Antigravity）——絕不增刪或改寫講者實際說出的內容。
    替換表依課程內容（02-project-scaffold.md）與旁白稿人工核對後列入。
    執行後列出每項替換次數供人工複查；原檔先備份成 .bak。
"""
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")  # Windows 主控台預設 cp950，避免中文亂碼

ROOT = Path(__file__).resolve().parent.parent

# 校正表：(正規表示式, 替換結果, 說明)。長詞在前避免被短詞先吃掉。
REPLACEMENTS: list[tuple[str, str, str]] = [
    (r"Spring\s*泥泥鰍歷史", "Spring Initializr", "Initializr 誤認"),
    (r"Spring Initialize(?!r)", "Spring Initializr", "Initializr 缺尾字母"),
    (r"InnixoList", "Initializr", "Initializr 誤認"),
    (r"NTPub體", "Antigravity", "Antigravity 誤認"),
    (r"NTGobt|NTEGRAT|NTQQT", "Antigravity", "Antigravity 誤認"),
    (r"CloudCode", "Claude Code", "Claude Code 誤認"),
    (r"count點example", "com點example", "com.example 誤認"),
    (r"AICM", "AI-CRM", "ai-crm 資料夾名稱誤認"),
    (r"哈好", "Hahow", "Hahow 同音誤認"),
    (r"Leap檔", "zip檔", "zip 檔誤認"),
    (r"胖檔", "pom檔", "pom 檔同音誤認"),
    (r"城市", "程式", "程式同音誤認"),
    (r"專欄", "專案", "專案同音誤認"),
    (r"轉案", "專案", "專案同音誤認"),
    (r"噹噹成", "當當成", "當成（口語重複）同音誤認"),
]


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for arg in sys.argv[1:]:
        path = Path(arg)
        if not path.is_absolute():
            path = ROOT / path
        if not path.exists():
            sys.exit(f"找不到檔案：{path}")
        text = path.read_text(encoding="utf-8")
        path.with_suffix(path.suffix + ".bak").write_text(text, encoding="utf-8")
        total = 0
        for pattern, repl, note in REPLACEMENTS:
            text, n = re.subn(pattern, repl, text)
            if n:
                print(f"[修正] {pattern} → {repl}（{note}）×{n}")
                total += n
        path.write_text(text, encoding="utf-8")
        print(f"[完成] {path.name}：共 {total} 處修正（原檔備份 {path.name}.bak）")


if __name__ == "__main__":
    main()
