#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
學員資料包建置腳本：把 handout/live-prompt-pack.md 轉成單檔可寄送的 HTML。

用途：
  直播散場後要把「提示詞全文＋規格書範本＋SOP 範本＋資安稽核清單」寄給學員。
  Markdown 是唯一真實來源（source of truth），HTML 一律由本腳本重生，
  不要手改 HTML，否則兩份會分叉。

執行：
  python scripts/build_handout.py                 # 由 live-slides/ 目錄執行
  python scripts/build_handout.py --check         # 只檢查 HTML 是否為最新，不寫檔

相依：pip install markdown
"""

import argparse
import io
import sys
from pathlib import Path

try:
    import markdown
except ImportError:  # 明確報錯，不要靜默失敗
    sys.exit('缺少相依套件，請先執行：pip install markdown')

# 專案根目錄＝本檔案的上一層（live-slides/）
ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'handout' / 'live-prompt-pack.md'
DST = ROOT / 'handout' / 'live-prompt-pack.html'

# 內嵌樣式：單檔、無外部相依，直接夾在 Email 附件也能開
CSS = '''body{font-family:"Noto Sans TC","Microsoft JhengHei","Segoe UI",sans-serif;max-width:900px;margin:40px auto;padding:0 24px;line-height:1.75;color:#1f2937}
h1{font-size:28px;border-bottom:3px solid #2563eb;padding-bottom:10px}h2{font-size:22px;margin-top:44px;border-left:6px solid #2563eb;padding-left:12px}h3{font-size:18px;margin-top:28px}
pre{background:#0f172a;color:#e2e8f0;padding:16px 20px;border-radius:8px;overflow:auto;font-size:14px;line-height:1.6;white-space:pre-wrap}
code{font-family:Consolas,"Cascadia Code",monospace}p code{background:#f1f5f9;padding:1px 6px;border-radius:4px}
table{border-collapse:collapse;width:100%;font-size:14px;margin:12px 0}th,td{border:1px solid #cbd5e1;padding:6px 10px;text-align:left;vertical-align:top}th{background:#e2e8f0}
blockquote{border-left:4px solid #94a3b8;margin:0;padding:6px 16px;color:#475569;background:#f8fafc}
hr{border:0;border-top:1px solid #cbd5e1;margin:36px 0}@media print{body{margin:0;max-width:none}pre{white-space:pre-wrap}}'''


def extract_title(md_text):
    """從 Markdown 第一個 H1 取出文件標題，供 <title> 使用。"""
    for line in md_text.splitlines():
        if line.startswith('# '):
            return line[2:].strip()
    return '學員資料包'


def render(md_text):
    """把 Markdown 轉成完整的單檔 HTML 字串。"""
    body = markdown.markdown(
        md_text,
        extensions=['tables', 'fenced_code', 'sane_lists'],
        output_format='xhtml',
    )
    title = extract_title(md_text)
    return (
        '<!doctype html><html lang="zh-TW"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        f'<title>{title}</title><style>{CSS}</style></head><body>\n{body}\n</body></html>\n'
    )


def main():
    parser = argparse.ArgumentParser(description='建置學員資料包 HTML')
    parser.add_argument('--check', action='store_true',
                        help='只比對現有 HTML 是否與 Markdown 同步，不寫檔')
    args = parser.parse_args()

    if not SRC.exists():
        sys.exit(f'找不到來源檔：{SRC}')

    md_text = io.open(SRC, encoding='utf-8').read()
    html = render(md_text)

    if args.check:
        current = io.open(DST, encoding='utf-8').read() if DST.exists() else ''
        if current == html:
            print(f'[OK] HTML 與 Markdown 同步：{DST}')
            return 0
        print(f'[NG] HTML 已過期，請執行 python scripts/build_handout.py 重生：{DST}')
        return 1

    io.open(DST, 'w', encoding='utf-8', newline='\n').write(html)
    print(f'[OK] 已產生 {DST}（{len(html):,} bytes，來源 {SRC.name} {len(md_text):,} 字元）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
