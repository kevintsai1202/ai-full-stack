"""
把產出的 PPTX 轉成逐頁 PNG，供人眼檢查排版有沒有破版。

轉檔腳本改完就要能立刻看到結果，所以這件事寫成可重跑的腳本而不是一次性指令。
流程：PPTX --LibreOffice--> PDF --PyMuPDF--> PNG。

執行：
    python live-slides/scripts/preview_pptx.py                # 全部 46 頁
    python live-slides/scripts/preview_pptx.py 2 11 33        # 只轉指定頁

預覽其他 deck（預覽圖輸出到該 PPTX 同目錄的 pptx-preview/）：
    python live-slides/scripts/preview_pptx.py --pptx course-package/slides/00-course-orientation.pptx
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import fitz  # PyMuPDF

# 預設值維持原直播簡報，讓既有的無參數呼叫行為完全不變
SLIDES_DIR = Path(__file__).resolve().parent.parent
PPTX_PATH = SLIDES_DIR / "別再問AI能不能做.pptx"
OUTPUT_DIR = SLIDES_DIR / "pptx-preview"

# LibreOffice 可執行檔的候選位置（Windows 預設安裝路徑優先）
SOFFICE_CANDIDATES = [
    r"C:\Program Files\LibreOffice\program\soffice.exe",
    r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
    "soffice",
]


def find_soffice() -> str:
    """找出可用的 LibreOffice 執行檔。"""
    for candidate in SOFFICE_CANDIDATES:
        if Path(candidate).exists() or shutil.which(candidate):
            return candidate
    raise RuntimeError("找不到 LibreOffice（soffice），無法轉 PDF。")


def main(pages: list[int]) -> int:
    if not PPTX_PATH.exists():
        print(f"找不到 {PPTX_PATH}，請先執行 html_to_pptx.py", file=sys.stderr)
        return 1

    OUTPUT_DIR.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        # LibreOffice 轉 PDF（--headless 不開視窗）
        subprocess.run(
            [find_soffice(), "--headless", "--convert-to", "pdf",
             "--outdir", tmp, str(PPTX_PATH)],
            check=True, capture_output=True,
        )
        pdf_path = next(Path(tmp).glob("*.pdf"))
        doc = fitz.open(pdf_path)
        targets = pages or range(1, doc.page_count + 1)
        for number in targets:
            if not 1 <= number <= doc.page_count:
                continue
            # 100 dpi 已足夠看出破版，又不會產生過大的檔案
            doc[number - 1].get_pixmap(dpi=100).save(
                OUTPUT_DIR / f"slide-{number:02d}.png")
        doc.close()
        print(f"已輸出 {len(list(targets))} 張預覽圖到 {OUTPUT_DIR}")
    return 0


def _parse_args():
    """解析參數；--pptx 省略時等同原本的無參數行為，位置參數仍是頁碼。"""
    parser = argparse.ArgumentParser(description="PPTX → 逐頁 PNG 預覽")
    parser.add_argument("--pptx", help=f"待預覽的 PPTX（預設：{PPTX_PATH}）")
    parser.add_argument("pages", nargs="*", type=int, help="只轉指定頁碼，省略為全部")
    return parser.parse_args()


if __name__ == "__main__":
    _args = _parse_args()
    if _args.pptx:
        PPTX_PATH = Path(_args.pptx).resolve()
        OUTPUT_DIR = PPTX_PATH.parent / "pptx-preview"
    raise SystemExit(main(_args.pages))
