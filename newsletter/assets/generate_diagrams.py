# -*- coding: utf-8 -*-
"""即時通訊系列（008-011）技術示意圖產生器。

用共用繪圖 helper 產出 8 張風格一致的 SVG 原稿；
之後由 render-diagrams.mjs 轉成 PNG（Email 客戶端不支援 SVG）。
可重跑：每次執行覆寫全部 SVG。

用法：python newsletter/assets/generate_diagrams.py
"""
import os

# ── 風格常數（沿用電子報品牌色系）──────────────────────────
FONT = "'Noto Sans TC','Microsoft JhengHei',sans-serif"
INK = "#1e293b"      # 主文字
MUTED = "#64748b"    # 次要文字
LINE = "#94a3b8"     # 箭頭/連線
PANEL = "#f8fafc"    # 面板底
BORDER = "#e2e8f0"   # 面板框
TEAL = "#0f766e"     # 品牌主色（標頭/強調）
TEAL_BG = "#d9f1ec"  # 品牌淺底
BLUE = "#1d4ed8"     # 問卷藍（第二強調）
BLUE_BG = "#eef3fb"
AMBER = "#d97706"    # 優惠琥珀（警示/重點）
AMBER_BG = "#fef3c7"
RED = "#dc2626"

OUT = os.path.dirname(os.path.abspath(__file__))


def svg_open(w, h, title):
    """SVG 開頭：白底＋標題列"""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}" font-family="{FONT}">'
            f'<rect width="{w}" height="{h}" fill="#ffffff"/>'
            f'<text x="{w//2}" y="42" text-anchor="middle" font-size="24" font-weight="700" fill="{INK}">{title}</text>')


def box(x, y, w, h, label, sub=None, fill=PANEL, stroke=BORDER, label_fill=INK, fs=17):
    """圓角方塊＋置中文字（可帶第二行小字）"""
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>'
    cy = y + h / 2 + (0 if sub else 6)
    if sub:
        s += f'<text x="{x+w/2}" y="{cy-4}" text-anchor="middle" font-size="{fs}" font-weight="700" fill="{label_fill}">{label}</text>'
        s += f'<text x="{x+w/2}" y="{cy+18}" text-anchor="middle" font-size="13" fill="{MUTED}">{sub}</text>'
    else:
        s += f'<text x="{x+w/2}" y="{cy}" text-anchor="middle" font-size="{fs}" font-weight="700" fill="{label_fill}">{label}</text>'
    return s


def arrow(x1, y1, x2, y2, color=LINE, label=None, dash=None, width=2.5, label_dy=-8):
    """直線箭頭＋可選標籤（標籤置於線段中點上方）"""
    d = f' stroke-dasharray="{dash}"' if dash else ''
    s = (f'<defs><marker id="m{abs(hash((x1,y1,x2,y2,color)))%99999}" markerWidth="9" markerHeight="9" '
         f'refX="7" refY="4.5" orient="auto"><path d="M0,0 L8,4.5 L0,9 Z" fill="{color}"/></marker></defs>')
    s += (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{width}"{d} '
          f'marker-end="url(#m{abs(hash((x1,y1,x2,y2,color)))%99999})"/>')
    if label:
        s += (f'<text x="{(x1+x2)/2}" y="{(y1+y2)/2+label_dy}" text-anchor="middle" '
              f'font-size="14" fill="{color}" font-weight="600">{label}</text>')
    return s


def note(x, y, text_str, color=MUTED, fs=14, anchor="middle", weight="400"):
    """浮動說明文字"""
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{fs}" '
            f'font-weight="{weight}" fill="{color}">{text_str}</text>')


def write(name, content):
    path = os.path.join(OUT, name)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content + '</svg>')
    print('已產生', name)


# ── 008-a 你去問 vs 它來說 ─────────────────────────────
def d008a():
    s = svg_open(1000, 430, '「你去問」 vs 「它來說」')
    # 左：短輪詢
    s += box(40, 70, 440, 330, '', fill='#ffffff', stroke=BORDER)
    s += note(260, 100, '短輪詢：你去問', TEAL, 18, weight='700')
    s += box(70, 130, 120, 210, '瀏覽器', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += box(330, 130, 120, 210, '伺服器', fill=PANEL, stroke=BORDER)
    for i, (dy, ans, col) in enumerate([(0, '沒資料', MUTED), (52, '沒資料', MUTED), (104, '沒資料', MUTED), (156, '有資料！', TEAL)]):
        y = 155 + dy
        s += arrow(190, y, 330, y, MUTED, '有新資料嗎？' if i == 0 else None, width=1.8)
        s += arrow(330, y + 20, 190, y + 20, col, ans, dash='5,4', width=1.8, label_dy=14)
    s += note(260, 385, '99% 的請求得到「沒資料」——負載 ∝ 人數 × 頻率', MUTED, 13)
    # 右：持久連線
    s += box(520, 70, 440, 330, '', fill='#ffffff', stroke=BORDER)
    s += note(740, 100, '持久連線：它來說（SSE / WebSocket）', TEAL, 18, weight='700')
    s += box(550, 130, 120, 210, '瀏覽器', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += box(810, 130, 120, 210, '伺服器', fill=PANEL, stroke=BORDER)
    s += arrow(670, 155, 810, 155, TEAL, '建立連線（一次）', width=2.2)
    s += f'<line x1="670" y1="180" x2="810" y2="180" stroke="{TEAL}" stroke-width="4" opacity="0.25"/>'
    s += f'<line x1="670" y1="330" x2="810" y2="330" stroke="{TEAL}" stroke-width="4" opacity="0.25"/>'
    for dy, lab in [(215, '事件 1'), (260, '事件 2'), (305, '事件 3')]:
        s += arrow(810, dy, 670, dy, TEAL, lab, width=2.2, label_dy=-6)
    s += note(740, 385, '連線放著，資料到了才推——一萬條安靜連線成本接近零', MUTED, 13)
    write('008-a-poll-vs-push.svg', s)


# ── 008-b 長輪詢時序 ───────────────────────────────────
def d008b():
    s = svg_open(1000, 400, '長輪詢（Long Polling）：掛著等，有事才回')
    s += box(60, 90, 130, 260, '瀏覽器', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += box(810, 90, 130, 260, '伺服器', fill=PANEL, stroke=BORDER)
    s += arrow(190, 130, 810, 130, MUTED, '① 請求發出', width=2)
    s += f'<rect x="480" y="150" width="180" height="34" rx="8" fill="{AMBER_BG}" stroke="{AMBER}"/>'
    s += note(570, 172, '② 伺服器先不回，掛著等…', AMBER, 13, weight='600')
    s += arrow(810, 215, 190, 215, TEAL, '③ 有新資料（或超時）才回應', width=2.2)
    s += arrow(190, 265, 810, 265, MUTED, '④ 收到後立刻再發下一輪', width=2, dash='6,4')
    s += note(500, 320, '循環結果：永遠有一個請求在伺服器待命——延遲接近即時', MUTED, 14)
    s += note(500, 344, '伺服器端切記用 DeferredResult 之類的非同步等待，別佔住執行緒', RED, 13)
    write('008-b-long-polling.svg', s)


# ── 009-a SSE 串流與自動重連 ───────────────────────────
def d009a():
    s = svg_open(1000, 430, 'SSE：一條不掛斷的 HTTP，瀏覽器原生自動重連')
    s += box(60, 90, 150, 250, '瀏覽器', 'new EventSource()', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += box(790, 90, 150, 250, '伺服器', 'text/event-stream', fill=PANEL, stroke=BORDER)
    s += arrow(210, 125, 790, 125, MUTED, 'GET /api/stream（普通 HTTP）', width=2)
    s += f'<line x1="210" y1="150" x2="790" y2="150" stroke="{TEAL}" stroke-width="4" opacity="0.25"/>'
    for x, tok in [(700, 'data: 今'), (590, 'data: 天'), (480, 'data: 天'), (370, 'data: 氣'), (260, 'data: …')]:
        s += f'<rect x="{x}" y="168" width="86" height="30" rx="6" fill="{TEAL_BG}" stroke="{TEAL}"/>'
        s += note(x + 43, 188, tok, TEAL, 13, weight='600')
    s += note(500, 226, '◀── 事件一段一段推過來（AI 打字機效果的本體）', TEAL, 14, weight='600')
    s += f'<rect x="330" y="250" width="340" height="34" rx="8" fill="#fde8e8" stroke="{RED}"/>'
    s += note(500, 272, '✂ 連線中斷', RED, 14, weight='700')
    s += arrow(210, 315, 790, 315, BLUE, '瀏覽器自動重連，帶 Last-Event-ID: 42', width=2.2)
    s += note(500, 355, '伺服器據此補發漏掉的事件——重連機制是規格內建，不用自己寫', MUTED, 14)
    s += note(500, 395, '限制：單向（伺服器→瀏覽器）、純文字 UTF-8', MUTED, 13)
    write('009-a-sse-stream.svg', s)


# ── 009-b Nginx 緩衝坑 ─────────────────────────────────
def d009b():
    s = svg_open(1000, 460, '最經典的 SSE 上線坑：反向代理緩衝')
    # 上半：壞
    s += note(80, 100, '✗ 預設（proxy_buffering on）：打字機變一次全吐', RED, 17, 'start', '700')
    s += box(60, 120, 120, 90, '後端', fill=PANEL, stroke=BORDER)
    s += box(420, 120, 160, 90, 'Nginx', '緩衝區把 token 憋住', fill='#fde8e8', stroke=RED, label_fill=RED)
    s += box(820, 120, 120, 90, '瀏覽器', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    for x in (200, 250, 300, 350):
        s += f'<rect x="{x}" y="152" width="36" height="26" rx="5" fill="{TEAL_BG}" stroke="{TEAL}"/>'
    s += arrow(590, 165, 820, 165, RED, '……最後一次全吐出來', width=2.2)
    # 下半：好
    s += note(80, 280, '✓ 對 SSE 路徑關閉緩衝（proxy_buffering off）', TEAL, 17, 'start', '700')
    s += box(60, 300, 120, 90, '後端', fill=PANEL, stroke=BORDER)
    s += box(420, 300, 160, 90, 'Nginx', '直通不憋', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL)
    s += box(820, 300, 120, 90, '瀏覽器', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    for x in (210, 300, 620, 710):
        s += f'<rect x="{x}" y="332" width="36" height="26" rx="5" fill="{TEAL_BG}" stroke="{TEAL}"/>'
        s += arrow(x + 40, 345, x + 76, 345, TEAL, None, width=1.6)
    s += note(500, 430, '症狀辨識：本機順暢、上線整段憋住＝九成是代理緩衝', MUTED, 14)
    write('009-b-nginx-buffering.svg', s)


# ── 010-a 四技術方向對比 ───────────────────────────────
def d010a():
    s = svg_open(1000, 470, '一張圖看懂四種技術的「方向」')
    rows = [
        ('輪詢', '瀏覽器反覆問', MUTED, [('→', '問'), ('←', '答')], '低頻、鬆即時'),
        ('SSE', '單向串流', TEAL, [('←', '推')], '通知、AI 打字機'),
        ('WebSocket', '全雙工對講', BLUE, [('→', '說'), ('←', '說')], '聊天、協作、遊戲'),
        ('WebRTC', '點對點（不經你的伺服器）', AMBER, [('⇄', 'P2P')], '視訊、傳檔'),
    ]
    y = 85
    for name, desc, color, arrows, scene in rows:
        s += box(50, y, 160, 64, name, desc, fill='#ffffff', stroke=color, label_fill=color, fs=18)
        s += box(300, y, 110, 64, '瀏覽器', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=15)
        peer = '瀏覽器 B' if name == 'WebRTC' else '伺服器'
        s += box(640, y, 110, 64, peer, fill=PANEL, stroke=BORDER, fs=15)
        mid = y + 32
        if name == '輪詢':
            s += arrow(410, mid - 12, 640, mid - 12, MUTED, '有資料嗎？', width=1.8)
            s += arrow(640, mid + 14, 410, mid + 14, MUTED, None, width=1.8, dash='5,4')
        elif name == 'SSE':
            s += arrow(640, mid, 410, mid, TEAL, '事件流 ▶▶▶', width=2.4)
        elif name == 'WebSocket':
            s += arrow(410, mid - 12, 640, mid - 12, BLUE, None, width=2.2)
            s += arrow(640, mid + 14, 410, mid + 14, BLUE, None, width=2.2)
        else:
            s += arrow(410, mid, 640, mid, AMBER, '媒體流／DataChannel', width=2.6)
            s += arrow(640, mid + 18, 410, mid + 18, AMBER, None, width=2.6)
        s += note(870, mid + 6, scene, MUTED, 14)
        y += 88
    s += note(500, 448, '選型第一問：資料往哪個方向流？多頻繁？', INK, 15, weight='700')
    write('010-a-four-directions.svg', s)


# ── 010-b WebRTC 信令三步 ──────────────────────────────
def d010b():
    s = svg_open(1000, 440, 'WebRTC：連線前的三步曲')
    s += box(80, 250, 160, 100, '瀏覽器 A', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += box(760, 250, 160, 100, '瀏覽器 B', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += box(420, 80, 160, 70, '信令伺服器', '通常用 WebSocket', fill=PANEL, stroke=BORDER)
    s += box(240, 80, 130, 70, 'STUN', '發現公網位址', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL)
    s += box(640, 80, 130, 70, 'TURN', '穿不透時中繼（花錢）', fill=AMBER_BG, stroke=AMBER, label_fill=AMBER)
    s += arrow(200, 260, 430, 150, MUTED, '① offer', width=2)
    s += arrow(570, 150, 790, 260, MUTED, None, width=2)
    s += arrow(760, 280, 590, 155, MUTED, '② answer', width=2, dash='6,4')
    s += arrow(410, 155, 240, 280, MUTED, None, width=2, dash='6,4')
    s += arrow(240, 330, 760, 330, AMBER, '③ ICE 候選位址互換 → 打通 P2P 通道（媒體不經你的伺服器）', width=3)
    s += note(500, 395, 'offer/answer 換能力・ICE 換路徑・STUN/TURN 幫穿牆', INK, 15, weight='700')
    s += note(500, 420, '「點對點」≠「不需要伺服器」——信令與 TURN 都是你要準備的', MUTED, 13)
    write('010-b-webrtc-signaling.svg', s)


# ── 011-a Webhook 互補鏈 ───────────────────────────────
def d011a():
    s = svg_open(1000, 320, 'Webhook 是互補鏈，不是替代品')
    s += box(50, 120, 190, 90, '外部服務', '金流／GitHub／LINE', fill=PANEL, stroke=BORDER)
    s += box(400, 120, 200, 90, '你的後端', '驗章→去重→處理', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL)
    s += box(760, 120, 190, 90, '使用者的瀏覽器', '畫面即時更新', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE)
    s += arrow(240, 165, 400, 165, AMBER, 'Webhook（HTTP POST）', width=2.6)
    s += arrow(600, 165, 760, 165, TEAL, 'SSE / WebSocket', width=2.6)
    s += note(320, 230, '伺服器對伺服器', AMBER, 14, weight='600')
    s += note(680, 230, '伺服器對瀏覽器（前三期）', TEAL, 14, weight='600')
    s += note(500, 280, '瀏覽器沒有公網位址，接不到 webhook——要觸及使用者，必須接力', INK, 15, weight='700')
    write('011-a-webhook-chain.svg', s)


# ── 011-b 接收端四道關卡 ───────────────────────────────
def d011b():
    s = svg_open(1000, 360, '收 Webhook 的正確姿勢：四道關卡')
    steps = [
        ('① 驗章', 'HMAC＋常數時間比較', TEAL),
        ('② 去重', 'event_id 唯一約束兜底', TEAL),
        ('③ 立刻回 200', '先簽收、重活丟佇列', TEAL),
        ('④ 狀態機', '只進不退，擋亂序', TEAL),
    ]
    x = 60
    s += arrow(10, 155, 58, 155, AMBER, None, width=2.6)
    s += note(30, 135, 'webhook', AMBER, 13, weight='600')
    for i, (t, d, c) in enumerate(steps):
        s += box(x, 110, 200, 90, t, d, fill=TEAL_BG if i != 2 else AMBER_BG,
                 stroke=TEAL if i != 2 else AMBER, label_fill=TEAL if i != 2 else AMBER)
        if i < 3:
            s += arrow(x + 200, 155, x + 238, 155, LINE, None, width=2.2)
        x += 240
    fails = ['假請求 → 401 拒收', '重複事件 → 安靜返回', '逾時重送風暴 ✗', '舊事件晚到 → 忽略']
    x = 60
    for i, f in enumerate(fails):
        s += arrow(x + 100, 200, x + 100, 240, RED if i != 2 else MUTED, None, width=1.6, dash='4,4')
        s += note(x + 100, 262, f, RED if i != 2 else MUTED, 13)
        x += 240
    s += note(500, 320, '這四道工，本質是在 HTTP 上手工補回「訊息佇列」原生提供的保證', INK, 15, weight='700')
    write('011-b-webhook-gates.svg', s)


if __name__ == '__main__':
    d008a(); d008b(); d009a(); d009b(); d010a(); d010b(); d011a(); d011b()
    print('全部 8 張 SVG 產生完成')
