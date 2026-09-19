"""
電子報用截圖前處理：自動裁掉四周白邊，讓有效內容在信件中盡量大。

背景：直播投影片的截圖（live-slides/assets/）四周常有大片留白，
而電子報在信件客戶端通常被限制在 600px 寬——等於整張縮一半。
先把無效留白裁掉，同樣的顯示寬度就能換到更大的字。

用法：
    python newsletter/assets/prepare-screenshots.py

輸出：newsletter/assets/png/012-c-*.png、012-d-*.png、013-c-*.png、013-d-*.png、
      015-c-*.png 至 015-f-*.png（可直接上傳媒體庫）
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SRC_DIR = REPO / "live-slides" / "assets"
OUT_DIR = HERE / "png"

# 來源截圖 → 輸出檔名。
# 註：012-a／012-b 已配給主題二（EVENT 系列 E0）的示意圖，
#     主題一的可行性評估報告截圖接續編為 c／d。
JOBS = [
    ("01-feasibility-difficulty.png", "012-c-feasibility-difficulty.png"),
    ("02-feasibility-uncertainties.png", "012-d-feasibility-uncertainties.png"),
    ("03-tech-selection.png", "013-c-tech-selection.png"),
    ("04-milestone-acceptance.png", "013-d-milestone-acceptance.png"),
    # 015 主題一的資安稽核截圖；a／b 已配給 EVENT 系列 E3 示意圖。
    ("09-security-audit.png", "015-c-security-audit.png"),
    ("09b-owasp-a10.png", "015-d-owasp-a10.png"),
    ("10-dependency-audit.png", "015-e-dependency-audit.png"),
    ("11-fix-before-after.png", "015-f-fix-before-after.png"),
]

WHITE_THRESHOLD = 248   # 亮度高於此值視為白底
PADDING = 16            # 裁切後補回的白邊，避免內容貼齊圖片邊緣


def trim_white_border(image: Image.Image) -> Image.Image:
    """裁掉四周接近白色的邊，再補回固定 padding。"""
    rgb = image.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()

    def row_is_blank(y: int) -> bool:
        """整列都接近白色才算空白列（每 4 px 取樣一次，夠準也夠快）"""
        return all(min(pixels[x, y]) >= WHITE_THRESHOLD for x in range(0, width, 4))

    def col_is_blank(x: int) -> bool:
        return all(min(pixels[x, y]) >= WHITE_THRESHOLD for y in range(0, height, 4))

    top = 0
    while top < height - 1 and row_is_blank(top):
        top += 1
    bottom = height - 1
    while bottom > top and row_is_blank(bottom):
        bottom -= 1
    left = 0
    while left < width - 1 and col_is_blank(left):
        left += 1
    right = width - 1
    while right > left and col_is_blank(right):
        right -= 1

    box = (max(0, left - PADDING), max(0, top - PADDING),
           min(width, right + 1 + PADDING), min(height, bottom + 1 + PADDING))
    return rgb.crop(box)


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for src_name, out_name in JOBS:
        src = SRC_DIR / src_name
        if not src.exists():
            print(f"找不到來源截圖：{src}")
            return 1
        with Image.open(src) as image:
            before = image.size
            trimmed = trim_white_border(image)
            trimmed.save(OUT_DIR / out_name, optimize=True)
        saved = (1 - (trimmed.size[1] / before[1])) * 100
        print(f"{src_name} {before[0]}x{before[1]} → {out_name} "
              f"{trimmed.size[0]}x{trimmed.size[1]}（高度減少 {saved:.0f}%）")
    print(f"\n輸出目錄：{OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
