"""
產生直播 EXAM 測驗加入連結的 QR Code，輸出到 live-slides/assets/exam-join-qr.png。

加入碼換了就改 ACCESS_CODE 重跑一次；投影片 P8 直接引用這張圖。
依賴：segno（純 Python，無需 Pillow）。

用法（專案根目錄）：
  python live-slides/scripts/make_exam_qr.py            # 使用預設加入碼
  python live-slides/scripts/make_exam_qr.py ABC123     # 指定加入碼
"""
import sys
from pathlib import Path

import segno

# EXAM 系統學員加入頁（正式站）
JOIN_BASE = "https://exam.zeabur.app/student/join?accessCode="
# 本場直播的 6 位加入碼
ACCESS_CODE = "L5XML8"
# 輸出位置：投影片 assets 目錄
OUTPUT = Path(__file__).resolve().parent.parent / "assets" / "exam-join-qr.png"


def main() -> int:
    """依加入碼產生高容錯 QR Code PNG，並印出連結與輸出路徑。"""
    code = (sys.argv[1] if len(sys.argv) > 1 else ACCESS_CODE).strip().upper()
    url = JOIN_BASE + code
    # error='h'：30% 容錯，投影到布幕、手機拍攝仍可掃；scale 放大到約 1000px 保持清晰
    qr = segno.make(url, error="h")
    qr.save(str(OUTPUT), scale=24, border=2, dark="#111111", light="#ffffff")
    print(f"URL   : {url}")
    print(f"Output: {OUTPUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
