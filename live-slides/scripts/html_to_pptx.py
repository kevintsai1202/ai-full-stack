"""
live-slides/index.html → PPTX 原生投影片轉換器。

把 HTML 直播簡報（頁數以 index.html 的 .slide 為準）重建成「文字可編輯」的 PowerPoint 檔：
標題、條列、提示詞區塊、表格全部還原成原生 PPTX 文字框與圖形，
截圖以圖片插入，data-note 講者備註寫進 PPTX 的備註頁。

設計要點：
1. 座標系。HTML deck 是 1600x900，PPTX 16:9 是 13.333 x 7.5 英吋，
   剛好 120px = 1 inch，因此所有 px 座標與字級都能等比例直接換算
   （px * 0.6 = pt），版面比例不會走樣。
2. 佈局。原稿 .slide 是 flex column + justify-content:center，PPTX 沒有
   流式排版，所以本腳本自己做兩趟（two-pass）：先 measure() 量出每個
   區塊高度，加總後算出垂直置中的起點，再 render() 逐塊往下排。
3. 文字量測。中文字寬約等於字級、半形字約 0.52 倍，據此估算折行數，
   高度才會跟實際渲染接近。

執行（需 python-pptx 與 Pillow）：
    python live-slides/scripts/html_to_pptx.py
輸出：
    live-slides/別再問AI能不能做.pptx

轉換其他 deck（版面樣式共用同一套，維持全系列視覺一致）：
    python live-slides/scripts/html_to_pptx.py --html course-package/slides/00-course-orientation.html
輸出預設為同目錄同檔名的 .pptx，也可用 --output 指定。
"""
from __future__ import annotations

import argparse
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Emu, Inches, Pt

# ── 路徑 ────────────────────────────────────────────────
# 預設值維持原直播簡報，讓既有的無參數呼叫行為完全不變；
# 另一份 deck（例如課程包開場）以 --html/--output 指定即可共用同一套轉換器。
# SLIDES_DIR 是「圖片與影片的相對路徑基準」，因此必須跟著 --html 的所在目錄走，
# 否則換 deck 之後 <img src="assets/x.png"> 會去找錯資料夾。
SLIDES_DIR = Path(__file__).resolve().parent.parent
HTML_PATH = SLIDES_DIR / "index.html"
OUTPUT_PATH = SLIDES_DIR / "別再問AI能不能做.pptx"

# ── 配色（直接取自 index.html 的 :root CSS 變數）─────────
C_BG = RGBColor(0x05, 0x07, 0x0F)
C_PANEL = RGBColor(0x13, 0x1B, 0x33)
C_BORDER = RGBColor(0x2C, 0x3A, 0x63)
C_TEXT = RGBColor(0xF4, 0xF7, 0xFF)
C_DIM = RGBColor(0xAE, 0xBF, 0xE0)
C_ACCENT = RGBColor(0x7C, 0xB8, 0xFF)
C_BAD = RGBColor(0xFF, 0x8A, 0x80)
C_GOOD = RGBColor(0x57, 0xF0, 0x97)
C_WARN = RGBColor(0xFF, 0xD2, 0x3E)
C_LINE_NO = RGBColor(0xFF, 0xB4, 0xAE)   # .line.no 的文字色
C_LINE_YES = RGBColor(0xBB, 0xF7, 0xD0)  # .line.yes 的文字色
C_PRE_BG = RGBColor(0x06, 0x0A, 0x16)
C_PRE_TEXT = RGBColor(0xCB, 0xD5, 0xE1)
C_TH_BG = RGBColor(0x1B, 0x25, 0x42)
C_STATION_ON_BG = RGBColor(0x1B, 0x25, 0x42)
C_KP_BG = RGBColor(0x10, 0x1A, 0x36)
C_WHITE = RGBColor(0xFF, 0xFF, 0xFF)

# badge 配色：(底色, 文字色)
BADGE_COLORS = {
    "live": (RGBColor(0x7F, 0x1D, 0x1D), RGBColor(0xFE, 0xCA, 0xCA)),
    "quiz": (RGBColor(0x1E, 0x3A, 0x8A), RGBColor(0xBF, 0xDB, 0xFE)),
    "time": (RGBColor(0x14, 0x53, 0x2D), RGBColor(0xBB, 0xF7, 0xD0)),
}

# ── 字型 ────────────────────────────────────────────────
FONT_SANS = "Microsoft JhengHei"   # 對應原稿的 Noto Sans TC，取 Windows 通用的正黑體
FONT_MONO = "Consolas"             # 對應原稿的 Cascadia Code / Consolas

# ── 版面常數（px，1600x900 座標系）──────────────────────
DECK_W, DECK_H = 1600, 900
PAD_TOP, PAD_BOTTOM, PAD_X = 52, 72, 96
CONTENT_W = DECK_W - PAD_X * 2      # 1408
CONTENT_H = DECK_H - PAD_TOP - PAD_BOTTOM
BAR_H = 46                          # 底部列高度


# ── 單位換算 ────────────────────────────────────────────
def px(value: float) -> Emu:
    """px（1600x900 座標系）換算成 PPTX 的 EMU 長度。"""
    return Inches(value / 120.0)


def fpt(value_px: float) -> Pt:
    """px 字級換算成 pt（120px/inch → 72pt/inch，比例 0.6）。"""
    return Pt(round(value_px * 0.6, 1))


# ═══════════════════════════════════════════════════════
# 一、HTML 解析
# ═══════════════════════════════════════════════════════
class Node:
    """極簡 DOM 節點：只保留轉檔需要的 tag / class / style / 子節點。"""

    def __init__(self, tag: str, attrs: dict):
        self.tag = tag
        self.attrs = attrs
        self.children: list = []          # Node 或 str
        self.classes = attrs.get("class", "").split()
        self.style = attrs.get("style", "")

    def has(self, name: str) -> bool:
        """是否帶有指定的 class。"""
        return name in self.classes

    def find_all(self, tag: str) -> list:
        """深度優先找出所有指定 tag 的子孫節點。"""
        found = []
        for child in self.children:
            if isinstance(child, Node):
                if child.tag == tag:
                    found.append(child)
                found.extend(child.find_all(tag))
        return found

    def text(self) -> str:
        """取出純文字（<br> 轉成換行）。"""
        out = []
        for child in self.children:
            if isinstance(child, str):
                out.append(child)
            elif child.tag == "br":
                out.append("\n")
            else:
                out.append(child.text())
        return "".join(out)


# HTML 中不需要成對關閉的標籤
VOID_TAGS = {"br", "img", "meta", "link", "hr", "input"}


class DeckParser(HTMLParser):
    """把 index.html 的 <section class="slide"> 解析成 Node 樹。"""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.slides: list[Node] = []
        self.stack: list[Node] = []
        self.in_style = False

    def handle_starttag(self, tag, attrs):
        attr_dict = {k: (v or "") for k, v in attrs}
        if tag == "style":
            self.in_style = True
            return
        if tag in VOID_TAGS:
            if self.stack:
                self.stack[-1].children.append(Node(tag, attr_dict))
            return
        node = Node(tag, attr_dict)
        # 只從 slide section 開始收集，忽略 head / bar / notes 容器
        if tag == "section" and "slide" in node.classes:
            self.slides.append(node)
            self.stack = [node]
        elif self.stack:
            self.stack[-1].children.append(node)
            self.stack.append(node)

    def handle_endtag(self, tag):
        if tag == "style":
            self.in_style = False
            return
        if tag in VOID_TAGS or not self.stack:
            return
        # 往回找到對應的開標籤（容忍 HTML 的省略關閉）
        for i in range(len(self.stack) - 1, -1, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                break

    def handle_data(self, data):
        if self.in_style or not self.stack:
            return
        self.stack[-1].children.append(data)


# ═══════════════════════════════════════════════════════
# 二、行內文字 → 樣式化片段（run）
# ═══════════════════════════════════════════════════════
class Seg:
    """一段同樣式的文字。color=None 代表沿用該區塊的預設色。"""

    def __init__(self, text: str, bold=False, color=None, mono=False):
        self.text = text
        self.bold = bold
        self.color = color
        self.mono = mono


def inline_segs(node: Node, in_pre=False, in_kp=False) -> list[list[Seg]]:
    """
    把節點的行內內容攤平成「多行 × 多片段」。

    回傳 list of lines，每個 line 是 list[Seg]；<br> 與 pre 內的換行都會斷行。
    in_pre / in_kp 決定 <span class="hl"> 與 <b> 該套哪個顏色。
    """
    lines: list[list[Seg]] = [[]]

    def push(text: str, bold: bool, color, mono: bool):
        """把文字加進目前行，遇到 \n 就開新行。"""
        parts = text.split("\n")
        for idx, part in enumerate(parts):
            if idx > 0:
                lines.append([])
            if part:
                lines[-1].append(Seg(part, bold, color, mono))

    def walk(current: Node, bold: bool, color, mono: bool):
        for child in current.children:
            if isinstance(child, str):
                # pre 保留原始空白，一般文字則壓縮連續空白
                text = child if in_pre else re.sub(r"\s+", " ", child)
                if text:
                    push(text, bold, color, mono)
                continue
            if child.tag == "br":
                lines.append([])
            elif child.tag == "strong":
                walk(child, True, C_WARN, mono)
            elif child.tag == "em":
                walk(child, bold, C_ACCENT, mono)
            elif child.tag == "b":
                walk(child, True, C_ACCENT if in_kp else color, mono)
            elif child.tag == "span" and child.has("hl"):
                walk(child, True, C_WARN, mono)
            elif child.tag == "span" and child.has("tag"):
                walk(child, True, color, mono)
                # .tag 的 margin-right:12px，用兩個空白近似間距
                push("  ", bold, color, mono)
            elif child.tag == "span" and child.has("badge"):
                continue  # badge 另外用圖形渲染，不進文字流
            else:
                walk(child, bold, color, mono)

    walk(node, False, None, in_pre)
    # 去掉首尾的空行（pre 常有）
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return lines or [[]]


def strip_lead_space(lines: list[list[Seg]]) -> list[list[Seg]]:
    """移除每行開頭多餘的空白（HTML 縮排造成的）。"""
    for line in lines:
        if line:
            line[0].text = line[0].text.lstrip()
    return lines


# ═══════════════════════════════════════════════════════
# 三、文字量測
# ═══════════════════════════════════════════════════════
# 卡片內文字量測的安全係數：字寬估算難免有誤差，寧可高估行數把卡片留高
SAFE_W = 0.97


def char_width(ch: str, font_px: float, mono: bool) -> float:
    """單一字元的估計寬度（px）。中日韓與全形標點算一個字寬，半形約 0.52。"""
    if mono:
        # 等寬字型：CJK 佔兩格，其餘一格（Consolas 字advance約 0.55em）
        return font_px * (1.1 if ord(ch) > 0x2E80 else 0.60)
    code = ord(ch)
    if code > 0x2E80 or ch in "　—…「」【】（）":
        return font_px
    return font_px * 0.56


def wrap_count(lines: list[list[Seg]], font_px: float, max_w: float, mono=False) -> int:
    """估算折行後的總行數。"""
    total = 0
    for line in lines:
        if not line:
            total += 1
            continue
        width, rows = 0.0, 1
        for seg in line:
            for ch in seg.text:
                cw = char_width(ch, font_px, mono or seg.mono)
                if width + cw > max_w:
                    rows += 1
                    width = cw
                else:
                    width += cw
        total += rows
    return total


# ═══════════════════════════════════════════════════════
# 四、區塊模型：把 slide 的子節點轉成可量測 / 可渲染的 Block
# ═══════════════════════════════════════════════════════
class Block:
    """一個垂直排版單位。h 為量測出的高度（px）。"""

    def __init__(self, kind: str, **data):
        self.kind = kind
        self.data = data
        self.h = 0.0
        self.margin_top = 0.0
        self.margin_bottom = 0.0


# 各文字型別的 (字級px, 行高倍率, 下邊距px, 預設色)
# 字級對照 index.html 的 CSS（2026-09 全頁 E2E 體檢後放大約 20%）再乘 0.9：
# 正黑體在 PPTX 的實際字寬比瀏覽器的 Noto Sans TC 略寬，留一成餘裕避免折行溢出
TEXT_STYLES = {
    "h1": (61, 1.28, 20, C_TEXT),        # CSS 68
    "h1cover": (70, 1.28, 20, C_TEXT),   # CSS 78
    "h2": (50, 1.32, 28, C_WHITE),       # CSS 56
    "h3": (34, 1.40, 16, C_ACCENT),      # CSS 38
    "p": (27, 1.70, 14, C_TEXT),         # CSS 30
    "lead": (31, 1.70, 14, C_DIM),       # CSS 34
    "small": (22, 1.70, 14, C_DIM),      # CSS 24
    "big": (43, 1.55, 14, C_TEXT),       # CSS 48
    "sub": (31, 1.70, 14, C_DIM),        # CSS 34
    "by": (23, 1.70, 14, C_DIM),         # CSS 26；.cover .by 另有 margin-top:46px，於 _text_block 補上
}

# 目前正在轉換的投影片 class（dense / roomy 等頁級樣式修飾），由 render_slide 設定
SLIDE_CLASSES: set = set()

# 各元件字級（CSS × 0.9），集中在這裡方便跟 index.html 對照
FS_PRE, FS_PRE_ROOMY = 22, 26            # pre 25 / .roomy pre 29
FS_KP, FS_KP_DENSE = 22, 20              # .kp 24 / .dense .kp 22
FS_LINE, FS_LINE_DENSE = 28, 23          # .line 31 / .dense .line 26
FS_TABLE, FS_TABLE_COMPACT = 22, 21      # table 25 / table.compact 23
FS_BADGE = 21                            # .badge 23
BADGE_H = 40                             # padding 5*2 + 字高
FS_CAPTION = 22                          # figcaption 25
FS_MAP_NO, FS_MAP_NAME, FS_MAP_WHAT = 20, 29, 21   # .map 22 / 32 / 23
MAP_H = 206                              # .station padding 28*2 + 三行文字


def inline_font_px(node: "Node") -> float | None:
    """讀出 inline style 的 font-size（原稿少數地方直接寫在 style 上），同樣乘 0.9。"""
    match = re.search(r"font-size:\s*(\d+)px", node.style)
    return round(float(match.group(1)) * 0.9) if match else None

H2_UNDERLINE_H = 7      # h2::after 底線條高
H2_UNDERLINE_PAD = 16   # h2 的 padding-bottom


def style_margin_top(node: Node) -> float:
    """讀出 inline style 的 margin-top（原稿用它微調間距）。"""
    match = re.search(r"margin-top:\s*(\d+)px", node.style)
    return float(match.group(1)) if match else 0.0


def build_blocks(slide: Node) -> list[Block]:
    """把一張投影片的子節點轉成 Block 清單。"""
    blocks: list[Block] = []
    is_cover = slide.has("cover")

    for child in slide.children:
        if not isinstance(child, Node):
            continue
        tag, cls = child.tag, child.classes

        if tag == "h1":
            blocks.append(_text_block("h1cover" if is_cover else "h1", child))
        elif tag == "h2":
            blocks.append(_text_block("h2", child))
        elif tag == "h3":
            badges = [b for b in child.find_all("span") if b.has("badge")]
            block = _text_block("h3", child)
            if badges:
                block.data["badge"] = _badge_data(badges[0])
            blocks.append(block)
        elif tag == "p":
            blocks.extend(_p_blocks(child))
        elif tag == "pre":
            blocks.append(_pre_block(child))
        elif tag == "figure":
            blocks.append(_figure_block(child))
        elif tag == "video":
            blocks.append(_video_block(child))
        elif tag == "table":
            blocks.append(_table_block(child))
        elif tag == "div" and "pair" in cls:
            blocks.append(_pair_block(child))
        elif tag == "div" and "map" in cls:
            blocks.append(_map_block(child))
        elif tag == "div" and "job-ad-row" in cls:
            blocks.append(_job_ad_block(child))
        elif tag == "div" and "display:flex" in child.style.replace(" ", ""):
            blocks.append(_flex_row_block(child))

    # 原稿 CSS：pre + .lead { text-align:center }——提示詞下方的金句置中
    for prev, block in zip(blocks, blocks[1:]):
        if prev.kind == "pre" and block.kind == "text" and block.data.get("style") == "lead":
            block.data["center"] = True
    return blocks


def _text_block(kind: str, node: Node, override_color=None) -> Block:
    """建立一般文字區塊並完成高度量測。"""
    font_px, line_ratio, margin_b, color = TEXT_STYLES[kind]
    if kind == "lead" and "dense" in SLIDE_CLASSES:
        font_px = 26                                   # .dense .lead 29 × 0.9
    override = inline_font_px(node)
    if override:
        font_px = override
    in_kp = node.has("kp")
    lines = strip_lead_space(inline_segs(node, in_kp=in_kp))

    # inline style 也可能改顏色（原稿有幾處用 color:var(--dim)）
    if "var(--dim)" in node.style:
        color = C_DIM

    block = Block(
        "text", lines=lines, font_px=font_px, line_ratio=line_ratio,
        color=override_color or color, style=kind,
        center=False, mono=False,
    )
    rows = wrap_count(lines, font_px, CONTENT_W)
    block.h = rows * font_px * line_ratio
    if kind == "h2":
        block.h += H2_UNDERLINE_PAD + H2_UNDERLINE_H
    block.margin_top = style_margin_top(node)
    if node.has("by"):
        block.margin_top = 46      # .cover .by { margin-top: 46px }
    block.margin_bottom = margin_b
    return block


def _badge_data(node: Node) -> dict:
    """取出 badge 的文字與配色種類。"""
    kind = next((c for c in node.classes if c in BADGE_COLORS), "quiz")
    return {"text": node.text().strip(), "variant": kind}


def _p_blocks(node: Node) -> list[Block]:
    """<p> 可能是純文字、純 badge，或 badge + 文字（如 Q5 那頁）。"""
    badges = [b for b in node.find_all("span") if b.has("badge")]
    rest = node.text()
    for badge in badges:
        rest = rest.replace(badge.text(), "", 1)
    rest = rest.strip("　 \n")

    out: list[Block] = []
    # badge 後面接一小段文字（例如「加入碼　L5XML8」）：原稿是同一列，這裡也排同一列
    if badges and rest and len(rest) <= 12 and not node.has("kp"):
        strongs = node.find_all("strong")
        text_px = (inline_font_px(strongs[0]) if strongs else None) or TEXT_STYLES["p"][0]
        block = Block("badgetext", badge=_badge_data(badges[0]), text=rest,
                      text_px=text_px, bold=bool(strongs),
                      color=C_WARN if strongs else C_TEXT)
        block.h = max(BADGE_H, text_px * 1.2)
        block.margin_top = style_margin_top(node)
        block.margin_bottom = 14
        return [block]
    if badges:
        block = Block("badge", **_badge_data(badges[0]))
        block.h = BADGE_H
        block.margin_top = style_margin_top(node)
        block.margin_bottom = 14
        out.append(block)

    if rest:
        kind = "p"
        for candidate in ("big", "lead", "small", "sub", "by"):
            if node.has(candidate):
                kind = candidate
                break
        if node.has("kp"):
            out.append(_kp_block(node))
        else:
            text_block = _text_block(kind, node)
            if badges:
                # badge 已獨立成塊，文字塊不再重複套用 margin-top
                text_block.margin_top = 0
            out.append(text_block)
    elif not badges:
        pass
    return out


def _kp_block(node: Node) -> Block:
    """知識點小卡：深底 + 左側 accent 粗邊。"""
    lines = strip_lead_space(inline_segs(node, in_kp=True))
    font_px = FS_KP_DENSE if "dense" in SLIDE_CLASSES else FS_KP
    inner_w = (CONTENT_W - 20 - 20 - 5) * SAFE_W   # padding 14px 20px + border-left 5px
    rows = wrap_count(lines, font_px, inner_w)
    block = Block("kp", lines=lines, font_px=font_px)
    block.h = 14 * 2 + rows * font_px * 1.6
    block.margin_top = 14 if "dense" in SLIDE_CLASSES else 24
    block.margin_bottom = 0
    return block


def _pre_block(node: Node) -> Block:
    """提示詞 / 指令區塊。"""
    lines = inline_segs(node, in_pre=True)
    font_px = FS_PRE_ROOMY if "roomy" in SLIDE_CLASSES else FS_PRE
    inner_w = (CONTENT_W - 24 * 2) * SAFE_W
    rows = wrap_count(lines, font_px, inner_w, mono=True)
    block = Block("pre", lines=lines, font_px=font_px)
    block.h = 20 * 2 + rows * font_px * 1.6
    block.margin_bottom = 14
    return block


def _figure_block(node: Node) -> Block:
    """截圖區塊：依原始比例縮放到 max-width / max-height 內。"""
    imgs = node.find_all("img")
    caps = node.find_all("figcaption")
    if not imgs:
        return Block("spacer")

    src = imgs[0].attrs.get("src", "")
    path = SLIDES_DIR / src
    max_h = 594.0                                   # figure img { max-height: 594px }
    style = imgs[0].style.replace(" ", "")
    match = re.search(r"max-height:(\d+)px", style)
    if match:
        max_h = float(match.group(1))
    with Image.open(path) as im:
        iw, ih = im.size
    scale = min(CONTENT_W / iw, max_h / ih, 1.0)
    w, h = iw * scale, ih * scale

    cap_lines = strip_lead_space(inline_segs(caps[0])) if caps else []
    cap_rows = wrap_count(cap_lines, FS_CAPTION, CONTENT_W) if cap_lines else 0
    cap_h = (12 + cap_rows * FS_CAPTION * 1.7) if cap_lines else 0

    block = Block("figure", path=str(path), w=w, h_img=h, cap_lines=cap_lines)
    block.h = h + cap_h
    block.margin_top = style_margin_top(node)
    block.margin_bottom = 14
    return block


def _video_block(node: Node) -> Block:
    """備援影片：改用轉好的 MP4（PowerPoint 不支援 WebM）。"""
    src = node.attrs.get("src", "")
    mp4 = SLIDES_DIR / src.replace(".webm", ".mp4")
    poster = SLIDES_DIR / src.replace(".webm", "-poster.png")
    with Image.open(poster) as im:
        iw, ih = im.size
    max_h = 594.0
    scale = min(CONTENT_W / iw, max_h / ih, 1.0)
    block = Block("video", path=str(mp4), poster=str(poster),
                  w=iw * scale, h_vid=ih * scale)
    block.h = ih * scale
    block.margin_bottom = 14
    return block


def _pair_block(node: Node) -> Block:
    """句型對比：多張帶左側色條的卡片。"""
    rows = []
    for line_node in node.children:
        if not isinstance(line_node, Node) or "line" not in line_node.classes:
            continue
        if line_node.has("no"):
            border, color = C_BAD, C_LINE_NO
        elif line_node.has("yes"):
            border, color = C_GOOD, C_LINE_YES
        elif "var(--dim)" in line_node.style:
            border, color = C_DIM, C_DIM
        else:
            border, color = C_BORDER, C_TEXT
        bold = line_node.has("yes")
        lines = strip_lead_space(inline_segs(line_node))
        rows.append({"lines": lines, "border": border, "color": color, "bold": bold})

    dense = "dense" in SLIDE_CLASSES
    block = Block("pair", rows=rows, gap=12 if dense else 18,
                  font_px=FS_LINE_DENSE if dense else FS_LINE,
                  pad_y=14 if dense else 20)
    block.margin_top = 12 if dense else 18
    block.margin_bottom = 12 if dense else 18
    return block


def _map_block(node: Node) -> Block:
    """生命週期地圖：等寬車站卡 + 箭頭。"""
    stations = []
    for st in node.children:
        if isinstance(st, Node) and "station" in st.classes:
            parts = {c.classes[0]: c for c in st.children
                     if isinstance(c, Node) and c.classes}
            stations.append({
                "no": parts["no"].text() if "no" in parts else "",
                "name": parts["name"].text() if "name" in parts else "",
                "what": parts["what"].text() if "what" in parts else "",
                "on": st.has("on") or node.has("all"),
            })
    block = Block("map", stations=stations)
    block.h = MAP_H
    block.margin_top = 28
    block.margin_bottom = 28
    return block


def _job_ad_block(node: Node) -> Block:
    """職缺頁：左側對比卡 + 右側原始截圖證物（並排）。"""
    pair = next((c for c in node.children
                 if isinstance(c, Node) and "pair" in c.classes), None)
    fig = next((c for c in node.children
                if isinstance(c, Node) and c.tag == "figure"), None)
    pair_block = _pair_block(pair) if pair else Block("spacer")

    proof = None
    if fig:
        img = fig.find_all("img")[0]
        cap = fig.find_all("figcaption")
        path = SLIDES_DIR / img.attrs.get("src", "")
        with Image.open(path) as im:
            iw, ih = im.size
        col_w = CONTENT_W * 0.30
        scale = min(col_w / iw, 380.0 / ih)
        proof = {"path": str(path), "w": iw * scale, "h": ih * scale,
                 "cap": cap[0].text().strip() if cap else ""}

    block = Block("jobad", pair=pair_block, proof=proof, gap=24)
    block.margin_bottom = 14
    return block


def _table_block(node: Node) -> Block:
    """四句型總表。"""
    rows = []
    widths = []
    for tr in node.find_all("tr"):
        cells = []
        for cell in tr.children:
            if not isinstance(cell, Node) or cell.tag not in ("th", "td"):
                continue
            if cell.tag == "th" and not widths:
                match = re.search(r"width:\s*(\d+)%", cell.style)
                pass
            color = C_TEXT
            if cell.has("no"):
                color = C_LINE_NO
            elif cell.has("yes"):
                color = C_LINE_YES
            elif cell.tag == "th":
                color = C_WHITE
            cells.append({
                "text": cell.text().strip(),
                "header": cell.tag == "th",
                "color": color,
                "bold": cell.tag == "th" or cell.has("yes"),
            })
        if cells:
            rows.append(cells)

    # 欄寬取自 <th style="width:..%">，最後一欄補滿
    header_widths = []
    for cell in node.find_all("th"):
        match = re.search(r"width:\s*(\d+)%", cell.style)
        header_widths.append(float(match.group(1)) if match else 0.0)
    # 沒有表頭列的表格（只有 tbody）是合法 HTML，但這裡取不到任何欄寬，
    # 空的 widths 會讓 add_table 收到 0 欄而丟出難解讀的 ZeroDivisionError。
    # 退回「依第一列儲存格數平均分欄」，讓無表頭的表格也能正常輸出。
    if not header_widths and rows:
        header_widths = [100.0 / len(rows[0])] * len(rows[0])
    remain = 100.0 - sum(header_widths)
    header_widths = [w if w else remain for w in header_widths]

    compact = node.has("compact")
    font_px = FS_TABLE_COMPACT if compact else FS_TABLE
    pad_x, pad_y = (14, 8) if compact else (16, 12)
    # 逐列估高：取該列中最高的儲存格
    heights = []
    for cells in rows:
        tallest = 0.0
        for cell, pct in zip(cells, header_widths):
            inner = CONTENT_W * pct / 100 - pad_x * 2
            rows_n = wrap_count([[Seg(cell["text"])]], font_px, inner)
            tallest = max(tallest, pad_y * 2 + rows_n * font_px * 1.6)
        heights.append(max(tallest, pad_y * 2 + font_px * 1.6))

    block = Block("table", rows=rows, widths=header_widths, heights=heights,
                  font_px=font_px, pad_x=pad_x, pad_y=pad_y)
    block.h = sum(heights)
    block.margin_bottom = 0
    return block


def measure_pair(block: Block) -> float:
    """pair 區塊高度：每張卡片獨立折行後加總，再加卡間距。"""
    total = 0.0
    inner_w = (CONTENT_W - 26 * 2 - 6) * SAFE_W
    font_px, pad_y = block.data["font_px"], block.data["pad_y"]
    for row in block.data["rows"]:
        rows_n = wrap_count(row["lines"], font_px, inner_w)
        row["h"] = pad_y * 2 + rows_n * font_px * 1.7
        total += row["h"]
    if block.data["rows"]:
        total += block.data["gap"] * (len(block.data["rows"]) - 1)
    return total


# ═══════════════════════════════════════════════════════
# 五、渲染
# ═══════════════════════════════════════════════════════
def set_font(run, size_px: float, color: RGBColor, bold=False, mono=False):
    """設定 run 的字型。中文須額外指定 East Asian font，否則 PowerPoint 會退回預設字。"""
    font = run.font
    font.size = fpt(size_px)
    font.bold = bold
    font.color.rgb = color
    name = FONT_MONO if mono else FONT_SANS
    font.name = name
    # python-pptx 只設 latin typeface，這裡補上 ea/cs 讓中文也套用同一字型
    r_pr = run._r.get_or_add_rPr()
    for tag in ("a:ea", "a:cs"):
        element = r_pr.find(qn(tag))
        if element is None:
            element = r_pr.makeelement(qn(tag), {})
            r_pr.append(element)
        element.set("typeface", name)


def add_textbox(slide, left, top, width, height, lines, font_px, color,
                line_ratio=1.7, bold=False, center=False, mono=False,
                anchor=MSO_ANCHOR.TOP):
    """把樣式化片段畫成一個文字框。"""
    box = slide.shapes.add_textbox(px(left), px(top), px(width), px(height))
    frame = box.text_frame
    frame.word_wrap = True
    frame.margin_left = frame.margin_right = 0
    frame.margin_top = frame.margin_bottom = 0
    frame.vertical_anchor = anchor

    for index, segs in enumerate(lines):
        para = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        para.alignment = PP_ALIGN.CENTER if center else PP_ALIGN.LEFT
        # 用絕對行距（pt）而非倍數，才能精確對應 CSS 的 line-height
        para.line_spacing = Pt(round(font_px * line_ratio * 0.6, 2))
        if not segs:
            run = para.add_run()
            run.text = " "
            set_font(run, font_px, color, bold, mono)
            continue
        for seg in segs:
            run = para.add_run()
            run.text = seg.text
            set_font(run, font_px, seg.color or color,
                     bold or seg.bold, mono or seg.mono)
    return box


def add_rect(slide, left, top, width, height, fill, line=None,
             shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=None):
    """畫一個（圓角）矩形，用來還原卡片、色條與 badge。"""
    box = slide.shapes.add_shape(shape, px(left), px(top), px(width), px(height))
    box.fill.solid()
    box.fill.fore_color.rgb = fill
    if line is None:
        box.line.fill.background()
    else:
        box.line.color.rgb = line
        box.line.width = Pt(1)
    box.shadow.inherit = False
    if radius is not None and shape == MSO_SHAPE.ROUNDED_RECTANGLE:
        # adjustment 是相對短邊的比例
        box.adjustments[0] = min(0.5, radius / min(width, height))
    box.text_frame.text = ""
    return box


def render_block(slide, block: Block, top: float) -> float:
    """畫出一個區塊，回傳它實際佔用的高度。"""
    kind = block.kind
    data = block.data

    if kind == "text":
        center = data.get("center", False)
        text_h = block.h - ((H2_UNDERLINE_PAD + H2_UNDERLINE_H)
                            if data["style"] == "h2" else 0)
        add_textbox(slide, PAD_X, top, CONTENT_W, text_h, data["lines"],
                    data["font_px"], data["color"], data["line_ratio"],
                    bold=data["style"] in ("h1", "h1cover", "h2", "h3", "big"),
                    center=center)
        if data["style"] == "h2":
            # h2::after 的 accent 底線條（漸層以實心近似）
            under_y = top + text_h + H2_UNDERLINE_PAD
            under_x = PAD_X + (CONTENT_W - 96) / 2 if center else PAD_X
            add_rect(slide, under_x, under_y, 96, H2_UNDERLINE_H, C_ACCENT, radius=4)
        if data.get("badge"):
            # h3 後面接的 badge，畫在標題右側同一行
            width = badge_width(data["badge"]["text"])
            bg, fg = BADGE_COLORS[data["badge"]["variant"]]
            bx = PAD_X + text_width(data["lines"][0], data["font_px"]) + 24
            add_rect(slide, bx, top + 2, width, 30, bg, radius=15)
            add_textbox(slide, bx, top + 8, width, 20,
                        [[Seg(data["badge"]["text"])]], 16, fg,
                        line_ratio=1.0, bold=True, center=True)
        return block.h

    if kind == "badge":
        width = badge_width(data["text"])
        bg, fg = BADGE_COLORS[data["variant"]]
        left = PAD_X + (CONTENT_W - width) / 2 if data.get("center") else PAD_X
        add_rect(slide, left, top, width, BADGE_H, bg, radius=BADGE_H / 2)
        add_textbox(slide, left, top + 8, width, BADGE_H - 14, [[Seg(data["text"])]],
                    FS_BADGE, fg, line_ratio=1.0, bold=True, center=True)
        return block.h

    if kind == "badgetext":
        # badge + 短文字同一列（加入碼那類），整列依頁面置中或靠左
        b_w = badge_width(data["badge"]["text"])
        t_w = text_width([Seg(data["text"])], data["text_px"]) * 1.35 + 24   # letter-spacing 補償
        total_w = b_w + 24 + t_w
        left = PAD_X + (CONTENT_W - total_w) / 2 if data.get("center") else PAD_X
        bg, fg = BADGE_COLORS[data["badge"]["variant"]]
        b_top = top + (block.h - BADGE_H) / 2
        add_rect(slide, left, b_top, b_w, BADGE_H, bg, radius=BADGE_H / 2)
        add_textbox(slide, left, b_top + 8, b_w, BADGE_H - 14, [[Seg(data["badge"]["text"])]],
                    FS_BADGE, fg, line_ratio=1.0, bold=True, center=True)
        add_textbox(slide, left + b_w + 24, top, t_w, block.h,
                    [[Seg(data["text"])]], data["text_px"], data["color"],
                    line_ratio=1.2, bold=data["bold"], anchor=MSO_ANCHOR.MIDDLE)
        return block.h

    if kind == "flexrow":
        return render_flexrow(slide, block, top)

    if kind == "pair":
        return render_pair(slide, block, PAD_X, top, CONTENT_W)

    if kind == "map":
        return render_map(slide, block, top)

    if kind == "pre":
        add_rect(slide, PAD_X, top, CONTENT_W, block.h, C_PRE_BG,
                 line=C_BORDER, radius=10)
        add_textbox(slide, PAD_X + 24, top + 20, CONTENT_W - 48, block.h - 40,
                    data["lines"], data["font_px"], C_PRE_TEXT, line_ratio=1.6, mono=True)
        return block.h

    if kind == "kp":
        add_rect(slide, PAD_X, top, CONTENT_W, block.h, C_KP_BG,
                 line=C_BORDER, radius=10)
        add_rect(slide, PAD_X, top, 5, block.h, C_ACCENT,
                 shape=MSO_SHAPE.RECTANGLE)
        add_textbox(slide, PAD_X + 25, top + 14, CONTENT_W - 50, block.h - 28,
                    data["lines"], data["font_px"], C_DIM, line_ratio=1.6,
                    anchor=MSO_ANCHOR.MIDDLE)
        return block.h

    if kind == "figure":
        left = PAD_X + (CONTENT_W - data["w"]) / 2
        slide.shapes.add_picture(data["path"], px(left), px(top),
                                 px(data["w"]), px(data["h_img"]))
        if data["cap_lines"]:
            add_textbox(slide, PAD_X, top + data["h_img"] + 12, CONTENT_W,
                        block.h - data["h_img"] - 12, data["cap_lines"],
                        FS_CAPTION, C_DIM, center=True)
        return block.h

    if kind == "video":
        left = PAD_X + (CONTENT_W - data["w"]) / 2
        slide.shapes.add_movie(data["path"], px(left), px(top),
                               px(data["w"]), px(data["h_vid"]),
                               poster_frame_image=data["poster"],
                               mime_type="video/mp4")
        return block.h

    if kind == "table":
        render_table(slide, block, top)
        return block.h

    if kind == "jobad":
        return render_jobad(slide, block, top)

    return block.h


def text_width(segs: list[Seg], font_px: float) -> float:
    """量一行文字的寬度（px），用來把 badge 接在標題右邊。"""
    return sum(char_width(ch, font_px, seg.mono)
               for seg in segs for ch in seg.text)


def badge_width(text: str) -> float:
    """badge 寬度 = 文字寬 + 左右 padding 16px。"""
    return text_width([Seg(text)], FS_BADGE) + 32


# ── flex 列（掃碼加入頁：左 QR、右文字欄）──────────────
class _column_width:
    """暫時把版面寬度改成某一欄，讓文字量測與渲染沿用同一套函式。"""

    def __init__(self, left: float, width: float):
        self.left, self.width = left, width

    def __enter__(self):
        global PAD_X, CONTENT_W
        self.saved = (PAD_X, CONTENT_W)
        PAD_X, CONTENT_W = self.left, self.width

    def __exit__(self, *exc):
        global PAD_X, CONTENT_W
        PAD_X, CONTENT_W = self.saved


def _flex_row_block(node: Node) -> Block:
    """<div style="display:flex">：一張圖 + 一欄文字（原稿只有掃碼加入頁用到）。"""
    img = next((c for c in node.children if isinstance(c, Node) and c.tag == "img"), None)
    column = next((c for c in node.children if isinstance(c, Node) and c.tag == "div"), None)
    gap = 48.0
    match = re.search(r"gap:\s*(\d+)px", node.style)
    if match:
        gap = float(match.group(1))

    picture = None
    if img is not None:
        path = SLIDES_DIR / img.attrs.get("src", "")
        with Image.open(path) as im:
            iw, ih = im.size
        height = 252.0
        match = re.search(r"(?<!max-)height:(\d+)px", img.style.replace(" ", ""))
        if match:
            height = float(match.group(1))
        picture = {"path": str(path), "w": iw * height / ih, "h": height}

    col_left = PAD_X + (picture["w"] + gap if picture else 0)
    col_w = CONTENT_W - (picture["w"] + gap if picture else 0)
    col_blocks: list[Block] = []
    if column is not None:
        with _column_width(col_left, col_w):
            for child in column.children:
                if isinstance(child, Node) and child.tag == "p":
                    col_blocks.extend(_p_blocks(child))
    for b in col_blocks:
        b.data["center"] = False
    col_h = sum(b.h + b.margin_top + b.margin_bottom for b in col_blocks)
    col_h -= col_blocks[-1].margin_bottom if col_blocks else 0

    block = Block("flexrow", picture=picture, blocks=col_blocks,
                  col_left=col_left, col_w=col_w, col_h=col_h)
    block.h = max(picture["h"] if picture else 0, col_h)
    block.margin_top = style_margin_top(node)
    block.margin_bottom = 14
    return block


def render_flexrow(slide, block: Block, top: float) -> float:
    """畫 flex 列：圖靠左、文字欄靠右，兩者各自垂直置中。"""
    data = block.data
    picture = data["picture"]
    if picture:
        slide.shapes.add_picture(picture["path"], px(PAD_X), px(top + (block.h - picture["h"]) / 2),
                                 px(picture["w"]), px(picture["h"]))
    y = top + (block.h - data["col_h"]) / 2
    with _column_width(data["col_left"], data["col_w"]):
        for b in data["blocks"]:
            y += b.margin_top
            y += render_block(slide, b, y)
            y += b.margin_bottom
    return block.h


def render_pair(slide, block: Block, left: float, top: float, width: float) -> float:
    """畫句型對比卡片組。"""
    y = top
    for row in block.data["rows"]:
        add_rect(slide, left, y, width, row["h"], C_PANEL, radius=12)
        add_rect(slide, left, y, 6, row["h"], row["border"],
                 shape=MSO_SHAPE.RECTANGLE)
        pad_y = block.data["pad_y"]
        add_textbox(slide, left + 32, y + pad_y, width - 58, row["h"] - pad_y * 2,
                    row["lines"], block.data["font_px"], row["color"], bold=row["bold"],
                    anchor=MSO_ANCHOR.MIDDLE)
        y += row["h"] + block.data["gap"]
    return block.h


def render_map(slide, block: Block, top: float) -> float:
    """畫車站流程圖：等寬卡片，中間夾箭頭。"""
    stations = block.data["stations"]
    count = len(stations)
    arrow_w, gap = 34.0, 14.0
    total_gap = gap * (count - 1) * 2 + arrow_w * (count - 1)
    card_w = (CONTENT_W - total_gap) / count
    height = block.h

    x = PAD_X
    for index, station in enumerate(stations):
        on = station["on"]
        add_rect(slide, x, top, card_w, height,
                 C_STATION_ON_BG if on else C_PANEL,
                 line=C_ACCENT if on else C_BORDER, radius=12)
        # 未選中的車站在原稿是 opacity .38，這裡用暗一階的文字色近似
        no_color = C_DIM if on else RGBColor(0x5A, 0x66, 0x84)
        name_color = C_TEXT if on else RGBColor(0x6E, 0x7B, 0x9B)
        what_color = C_DIM if on else RGBColor(0x5A, 0x66, 0x84)

        add_textbox(slide, x + 8, top + 26, card_w - 16, 30,
                    [[Seg(station["no"])]], FS_MAP_NO, no_color,
                    line_ratio=1.4, center=True)
        add_textbox(slide, x + 8, top + 60, card_w - 16, 44,
                    [[Seg(station["name"])]], FS_MAP_NAME, name_color,
                    line_ratio=1.4, bold=True, center=True)
        what_lines = [[Seg(part)] for part in station["what"].split("\n")]
        add_textbox(slide, x + 8, top + 112, card_w - 16, 72,
                    what_lines, FS_MAP_WHAT, what_color, line_ratio=1.5, center=True)

        x += card_w
        if index < count - 1:
            add_textbox(slide, x + gap, top + height / 2 - 24, arrow_w, 44,
                        [[Seg("→")]], 34, C_BORDER, line_ratio=1.2, center=True)
            x += gap * 2 + arrow_w
    return height


def render_jobad(slide, block: Block, top: float) -> float:
    """畫職缺頁的左右並排版面。"""
    proof = block.data["proof"]
    gap = block.data["gap"]
    right_w = CONTENT_W * 0.30 if proof else 0
    left_w = CONTENT_W - right_w - (gap if proof else 0)

    pair = block.data["pair"]
    # 左欄較窄，pair 高度需依左欄寬度重新量測
    inner = (left_w - 26 * 2 - 6) * SAFE_W
    total = 0.0
    for row in pair.data["rows"]:
        rows_n = wrap_count(row["lines"], pair.data["font_px"], inner)
        row["h"] = pair.data["pad_y"] * 2 + rows_n * pair.data["font_px"] * 1.7
        total += row["h"]
    total += pair.data["gap"] * (len(pair.data["rows"]) - 1)
    pair.h = total
    render_pair(slide, pair, PAD_X, top, left_w)

    if proof:
        left = PAD_X + left_w + gap + (right_w - proof["w"]) / 2
        slide.shapes.add_picture(proof["path"], px(left), px(top),
                                 px(proof["w"]), px(proof["h"]))
        if proof["cap"]:
            add_textbox(slide, PAD_X + left_w + gap, top + proof["h"] + 8,
                        right_w, 32, [[Seg(proof["cap"])]], 20, C_DIM,
                        center=True)
        return max(total, proof["h"] + 32)
    return total


def render_table(slide, block: Block, top: float):
    """畫四句型總表。"""
    rows, widths, heights = block.data["rows"], block.data["widths"], block.data["heights"]
    shape = slide.shapes.add_table(len(rows), len(widths), px(PAD_X), px(top),
                                   px(CONTENT_W), px(block.h))
    table = shape.table
    table.first_row = False           # 關掉樣式化的表頭底色，改用自訂配色
    table.horz_banding = False

    for index, pct in enumerate(widths):
        table.columns[index].width = px(CONTENT_W * pct / 100)
    for index, height in enumerate(heights):
        table.rows[index].height = px(height)

    for r_index, cells in enumerate(rows):
        for c_index, cell_data in enumerate(cells):
            cell = table.cell(r_index, c_index)
            cell.fill.solid()
            cell.fill.fore_color.rgb = C_TH_BG if cell_data["header"] else C_BG
            cell.margin_left = cell.margin_right = px(block.data["pad_x"])
            cell.margin_top = cell.margin_bottom = px(block.data["pad_y"])
            cell.vertical_anchor = MSO_ANCHOR.TOP
            frame = cell.text_frame
            frame.word_wrap = True
            para = frame.paragraphs[0]
            para.line_spacing = Pt(round(block.data["font_px"] * 1.6 * 0.6, 2))
            run = para.add_run()
            run.text = cell_data["text"]
            set_font(run, block.data["font_px"], cell_data["color"], cell_data["bold"])


# ═══════════════════════════════════════════════════════
# 六、組裝整份簡報
# ═══════════════════════════════════════════════════════
def render_slide(prs, slide_node: Node, index: int, total: int):
    """建立一張投影片：背景、垂直置中的內容、底部列、講者備註。"""
    layout = prs.slide_layouts[6]          # 空白版面
    slide = prs.slides.add_slide(layout)

    # 深色背景
    background = slide.background
    background.fill.solid()
    background.fill.fore_color.rgb = C_BG

    is_center = slide_node.has("center") or slide_node.has("cover")
    SLIDE_CLASSES.clear()
    SLIDE_CLASSES.update(slide_node.classes)
    blocks = build_blocks(slide_node)

    # 第一趟：量測（pair / jobad 需要延後計算）
    for block in blocks:
        if block.kind == "pair":
            block.h = measure_pair(block)
        elif block.kind == "jobad":
            pair = block.data["pair"]
            inner_left = (CONTENT_W * 0.70 - 26 * 2 - 6) * SAFE_W
            total_h = 0.0
            for row in pair.data["rows"]:
                rows_n = wrap_count(row["lines"], pair.data["font_px"], inner_left)
                row["h"] = pair.data["pad_y"] * 2 + rows_n * pair.data["font_px"] * 1.7
                total_h += row["h"]
            total_h += pair.data["gap"] * (len(pair.data["rows"]) - 1)
            proof = block.data["proof"]
            block.h = max(total_h, proof["h"] + 32 if proof else 0)
        if is_center:
            block.data["center"] = True

    # 第二趟：算出垂直置中的起點（內容過高時退回貼齊上緣）
    content_h = sum(b.h + b.margin_top + b.margin_bottom for b in blocks)
    content_h -= blocks[-1].margin_bottom if blocks else 0
    available = DECK_H - PAD_TOP - PAD_BOTTOM
    start = PAD_TOP + max(0.0, (available - content_h) / 2)

    y = start
    for block in blocks:
        y += block.margin_top
        y += render_block(slide, block, y)
        y += block.margin_bottom

    # 底部列：站別 + 操作提示 + 頁碼
    add_rect(slide, 0, DECK_H - BAR_H, DECK_W, BAR_H, C_PRE_BG,
             shape=MSO_SHAPE.RECTANGLE)
    station = slide_node.attrs.get("data-station", "")
    add_textbox(slide, 24, DECK_H - BAR_H + 13, 400, 22,
                [[Seg(station)]], 14, C_ACCENT, line_ratio=1.2)
    add_textbox(slide, DECK_W - 424, DECK_H - BAR_H + 13, 400, 22,
                [[Seg(f"{index} / {total}")]], 14, C_DIM, line_ratio=1.2)

    # 講者備註
    note = slide_node.attrs.get("data-note", "")
    if note:
        slide.notes_slide.notes_text_frame.text = note


def _parse_args():
    """解析命令列參數；兩個都省略時等同原本的無參數行為。"""
    parser = argparse.ArgumentParser(description="HTML deck → 原生 PPTX 轉換器")
    parser.add_argument("--html", help=f"來源 HTML deck（預設：{HTML_PATH}）")
    parser.add_argument("--output", help="輸出 PPTX 路徑（預設：與 HTML 同名的 .pptx）")
    return parser.parse_args()


def main():
    global SLIDES_DIR, HTML_PATH, OUTPUT_PATH
    args = _parse_args()
    if args.html:
        HTML_PATH = Path(args.html).resolve()
        # 資產基準跟著 HTML 走（見檔頭路徑段說明）
        SLIDES_DIR = HTML_PATH.parent
        # 未指定輸出時，與 HTML 同目錄同檔名、副檔名換成 .pptx
        OUTPUT_PATH = HTML_PATH.with_suffix(".pptx")
    if args.output:
        OUTPUT_PATH = Path(args.output).resolve()

    html = HTML_PATH.read_text(encoding="utf-8")
    parser = DeckParser()
    parser.feed(html)
    slides = parser.slides
    if not slides:
        print("找不到任何 .slide，請確認 index.html 結構。", file=sys.stderr)
        return 1

    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    for index, node in enumerate(slides, start=1):
        render_slide(prs, node, index, len(slides))

    prs.save(OUTPUT_PATH)
    print(f"已輸出 {len(slides)} 頁：{OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
