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


# ── 012-a 直接呼叫 vs 發事件（EVENT 系列 E0）────────────
def d012a():
    s = svg_open(1000, 430, '同一個需求，兩種接法')
    # 中央分隔虛線：左右各一種做法
    s += (f'<line x1="500" y1="70" x2="500" y2="380" stroke="{BORDER}" '
          f'stroke-width="1.5" stroke-dasharray="6,6"/>')
    downstream = ['AI 摘要', '業務通知', '稽核紀錄']

    # 左：直接呼叫——上游得認識每一個下游
    s += note(250, 88, '直接呼叫：上游得認識每一個下游', INK, 16, weight='700')
    s += box(150, 140, 200, 56, 'updateCustomer', fill=PANEL, stroke=BORDER)
    for i, name in enumerate(downstream):
        cx = 130 + i * 120
        s += arrow(250, 196, cx, 266, LINE, None, width=2.0)
        s += box(cx - 55, 268, 110, 48, name, fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=15)
    s += note(250, 348, '每多一個下游，就回頭改一次 updateCustomer', RED, 14, weight='600')

    # 右：發事件——上游只宣告發生了什麼
    s += note(735, 88, '發事件：上游只宣告發生了什麼', INK, 16, weight='700')
    s += box(635, 108, 200, 52, 'updateCustomer', fill=PANEL, stroke=BORDER)
    s += arrow(735, 160, 735, 186, LINE, None, width=2.0)
    s += box(650, 188, 170, 48, 'CustomerUpdated', fill=AMBER_BG, stroke=AMBER,
             label_fill=AMBER, fs=15)
    for i, name in enumerate(downstream):
        cx = 615 + i * 120
        s += arrow(735, 236, cx, 266, LINE, None, width=2.0)
        s += box(cx - 55, 268, 110, 48, name, fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=15)
    s += note(735, 348, '再加第四個下游，updateCustomer 一個字都不用改', TEAL, 14, weight='600')

    s += note(500, 400, '事件解的是「誰認識誰」的問題——是下游依賴上游，不是上游去認識下游', INK, 15, weight='700')
    write('012-a-coupling-vs-event.svg', s)


# ── 012-b 同步事件的執行時序（EVENT 系列 E0）────────────
def d012b():
    s = svg_open(1000, 400, 'publishEvent() 預設是同步的')
    # publishEvent 的涵蓋範圍：用虛線框圈住三個監聽器，強調這一行會卡在這裡
    s += (f'<rect x="232" y="150" width="530" height="104" rx="10" fill="none" '
          f'stroke="{AMBER}" stroke-width="1.8" stroke-dasharray="6,5"/>')
    s += note(497, 142, 'publishEvent() 這一行，卡在這整段', AMBER, 14, weight='700')

    # 主執行緒時間軸：所有工作都串在同一條線上
    s += note(40, 205, 'main', MUTED, 13, anchor='start', weight='600')
    s += arrow(40, 230, 962, 230, LINE, None, width=2.0)
    s += note(950, 258, '時間', MUTED, 12, anchor='end')

    s += box(80, 172, 140, 58, 'updateCustomer', fill=PANEL, stroke=BORDER, fs=14)
    for i, name in enumerate(['AI 摘要', '業務通知', '稽核紀錄']):
        s += box(248 + i * 170, 172, 150, 58, name, fill=TEAL_BG, stroke=TEAL,
                 label_fill=TEAL, fs=15)
    s += box(778, 172, 150, 58, '方法返回', fill=PANEL, stroke=BORDER, fs=14)

    # 例外回傳：同步呼叫的必然結果
    s += arrow(323, 254, 323, 296, RED, None, width=1.6, dash='4,4')
    s += note(323, 318, '監聽器爆炸', RED, 13, weight='600')
    s += (f'<line x1="323" y1="326" x2="150" y2="326" stroke="{RED}" stroke-width="1.6" '
          f'stroke-dasharray="4,4"/>')
    s += arrow(150, 326, 150, 240, RED, None, width=1.6, dash='4,4')
    s += note(560, 318, '例外一路傳回發布端，連交易一起 rollback', RED, 13, weight='600')

    s += note(500, 370, '同一條執行緒、同一個交易——解耦 ≠ 非同步', INK, 15, weight='700')
    write('012-b-sync-event-thread.svg', s)


# ── 013-a 同步 vs 非同步的等待時間（EVENT 系列 E1）──────
def d013a():
    s = svg_open(1000, 480, '同一件事，使用者要等多久')

    # 上半：同步——使用者陪著 LLM 一起等
    s += note(500, 88, '同步：使用者陪著 LLM 一起等', INK, 16, weight='700')
    s += box(150, 105, 100, 48, '存檔', fill=PANEL, stroke=BORDER, fs=15)
    s += box(254, 105, 400, 48, '呼叫 LLM 產生摘要', fill=AMBER_BG, stroke=AMBER,
             label_fill=AMBER, fs=15)
    s += box(658, 105, 120, 48, '回應使用者', fill=PANEL, stroke=BORDER, fs=14)
    s += (f'<line x1="150" y1="176" x2="778" y2="176" stroke="{RED}" stroke-width="1.6" '
          f'stroke-dasharray="4,4"/>')
    s += note(464, 200, '使用者從按下儲存到畫面有反應：三十秒', RED, 14, weight='600')
    s += note(464, 222, '等不及就重新整理再按一次 → LLM 再跑一次、再付一次錢', RED, 13)

    s += (f'<line x1="40" y1="244" x2="960" y2="244" stroke="{BORDER}" '
          f'stroke-width="1.5" stroke-dasharray="6,6"/>')

    # 下半：非同步——存檔立刻回應，摘要丟到背景
    s += note(500, 274, '非同步：存檔立刻回應，摘要自己在背景跑', INK, 16, weight='700')
    s += note(60, 320, 'main', MUTED, 13, anchor='start', weight='600')
    s += box(150, 292, 100, 48, '存檔', fill=PANEL, stroke=BORDER, fs=15)
    s += box(254, 292, 130, 48, '回應使用者', fill=TEAL_BG, stroke=TEAL,
             label_fill=TEAL, fs=14)
    s += note(319, 362, '零點幾秒就結束', TEAL, 13, weight='600')

    s += note(60, 404, '背景', MUTED, 13, anchor='start', weight='600')
    s += arrow(200, 340, 200, 376, LINE, None, width=2.0)
    s += box(254, 380, 400, 44, '呼叫 LLM 產生摘要', fill=TEAL_BG, stroke=TEAL,
             label_fill=TEAL, fs=15)

    s += note(500, 462, '同一件事花的時間沒變——變的是「誰在等」', INK, 15, weight='700')
    write('013-a-sync-vs-async-timeline.svg', s)


# ── 013-b @Async 的三個陷阱（EVENT 系列 E1）─────────────
def d013b():
    s = svg_open(1000, 400, '@Async 的三個陷阱：都不會報錯')
    cards = [
        (40, '① 自己呼叫自己', ['同一個 bean 內', 'this.generate()', '繞過了 proxy'],
         ['實測 caller=main', '實測 執行於=main', '→ @Async 等於沒加']),
        (370, '② 例外被吞掉', ['void 方法拋例外時', '呼叫端早就返回了', '沒有人在等它'],
         ['呼叫端抓不到例外', '只剩一行 SEVERE log', '→ 摘要空白且無提示']),
        (700, '③ 預設執行器', ['沒有 taskExecutor bean', '就退回', 'SimpleAsyncTaskExecutor'],
         ['送 5 次＝5 條新執行緒', '不重用，也沒有上限', '→ 100 人存檔 100 條']),
    ]
    for x, title, body, symptom in cards:
        cx = x + 130
        s += box(x, 90, 260, 215, '', fill=PANEL, stroke=BORDER)
        s += note(cx, 126, title, AMBER, 17, weight='700')
        for i, line in enumerate(body):
            s += note(cx, 160 + i * 22, line, MUTED, 13)
        s += (f'<line x1="{x+30}" y1="242" x2="{x+230}" y2="242" stroke="{BORDER}" '
              f'stroke-width="1.2"/>')
        for i, line in enumerate(symptom):
            s += note(cx, 266 + i * 21, line, RED, 13, weight='600')

    s += note(500, 350, '三個都不會報錯——程式照樣編譯、照樣執行，只是沒照你以為的方式跑', INK, 15, weight='700')
    write('013-b-async-pitfalls.svg', s)


# ── 013-e 設計期三段式產物鏈（可審查產物系列・站 2）──────
def d013e():
    """繪製設計期從限制前置到第一階段實作的可審查流程圖。"""
    s = svg_open(1000, 420, '設計期：一句「幫我寫」拆成三段')
    s += note(500, 76, '先把決定攤開，再讓程式碼承接已審查的假設', MUTED, 14)

    steps = [
        (35, 190, '需求＋限制', '環境／技能／時程', BLUE_BG, BLUE, BLUE),
        (285, 190, '① 選型分析', '候選方案＋有效邊界', AMBER_BG, AMBER, AMBER),
        (535, 190, '② 開發計畫', '模組／里程碑／驗收', TEAL_BG, TEAL, TEAL),
        (785, 180, '③ 第一階段實作', '照計畫動手', BLUE_BG, BLUE, BLUE),
    ]
    for index, (x, width, label, sub, fill, stroke, label_fill) in enumerate(steps):
        s += box(x, 112, width, 70, label, sub, fill=fill, stroke=stroke,
                 label_fill=label_fill, fs=15)
        if index < len(steps) - 1:
            next_x = steps[index + 1][0]
            s += arrow(x + width, 147, next_x - 8, 147, LINE, None, width=2.4)

    s += box(180, 238, 640, 52, '先不要寫程式碼', fill=AMBER_BG, stroke=AMBER,
             label_fill=AMBER, fs=18)
    s += note(500, 333, '把假設先變成便宜的審查點：計畫改一行，程式碼改一天', INK, 15,
              weight='700')
    s += note(500, 370, '這一站的驗收標準，下一站會直接變成測試', MUTED, 13)
    write('013-e-design-flow.svg', s)


# ── 013-f @Async 的 proxy 與執行器邊界（EVENT 系列 E1）───
def d013f():
    """繪製 @Async 正確路徑與三個常見旁路的架構圖。"""
    s = svg_open(1000, 500, '@Async 的正確邊界：外部呼叫要先經過 proxy')
    s += note(500, 76, '正確路徑：外部呼叫 → Spring proxy → 有界執行器 → 背景工作', TEAL, 14,
              weight='700')

    pipeline = [
        (40, 150, '外部呼叫', 'Controller／事件', PANEL, BORDER, INK),
        (230, 150, 'Spring proxy', '@Async 攔截', BLUE_BG, BLUE, BLUE),
        (420, 150, 'taskExecutor', 'core=2／queue=50', TEAL_BG, TEAL, TEAL),
        (610, 150, '背景 worker', '另一條執行緒', TEAL_BG, TEAL, TEAL),
        (800, 160, 'LLM 摘要', '慢，但不堵住使用者', AMBER_BG, AMBER, AMBER),
    ]
    for index, (x, width, label, sub, fill, stroke, label_fill) in enumerate(pipeline):
        s += box(x, 108, width, 64, label, sub, fill=fill, stroke=stroke,
                 label_fill=label_fill, fs=14)
        if index < len(pipeline) - 1:
            next_x = pipeline[index + 1][0]
            s += arrow(x + width, 140, next_x - 8, 140, TEAL, None, width=2.4)

    cards = [
        (40, 'this.generate()', ['繞過 proxy', '仍在 main', '→ @Async 等於沒加']),
        (370, 'void 拋例外', ['呼叫端早已返回', '只剩 log 可見', '→ 應寫回 FAILED']),
        (700, '沒有 taskExecutor', ['退回 SimpleAsyncTaskExecutor', '每次開新執行緒', '→ 不重用、沒上限']),
    ]
    for x, title, lines in cards:
        s += box(x, 250, 260, 150, '', fill='#fef2f2', stroke=RED)
        s += note(x + 130, 283, title, RED, 16, weight='700')
        for index, line in enumerate(lines):
            s += note(x + 130, 318 + index * 22, line, RED, 13,
                      weight='600' if index == 2 else '400')

    s += note(500, 454, '三個旁路不一定讓程式啟動失敗，卻會讓執行方式偏離你的設計', INK, 15,
              weight='700')
    write('013-f-async-proxy-boundary.svg', s)


# ── 014-a 事件送出時機的三種結果（EVENT 系列 E2）────────
def d014a():
    s = svg_open(1000, 430, '事件到底什麼時候該送出去')
    red_bg = '#fee2e2'

    rows = [
        (96, '① @EventListener ＋ 交易 rollback',
         [('更新客戶資料', 40, 130, PANEL, BORDER, INK),
          ('發事件', 178, 100, AMBER_BG, AMBER, AMBER),
          ('摘要開始跑', 286, 120, red_bg, RED, RED),
          ('rollback', 414, 100, red_bg, RED, RED)],
         '→ 資料退回原狀，摘要卻已經產生並存檔', RED),
        (196, '② @TransactionalEventListener ＋ 交易 rollback',
         [('更新客戶資料', 40, 130, PANEL, BORDER, INK),
          ('事件掛起，等 commit', 178, 190, AMBER_BG, AMBER, AMBER),
          ('rollback', 376, 100, PANEL, BORDER, MUTED)],
         '→ 事件直接丟棄，摘要不會產生', TEAL),
        (296, '③ @TransactionalEventListener ＋ commit 後當機',
         [('更新客戶資料', 40, 130, PANEL, BORDER, INK),
          ('事件掛起', 178, 110, AMBER_BG, AMBER, AMBER),
          ('commit 成功', 296, 110, TEAL_BG, TEAL, TEAL),
          ('當機', 414, 90, red_bg, RED, RED)],
         '→ 資料在，事件消失，沒有任何紀錄', RED),
    ]
    for y, title, boxes, verdict, verdict_color in rows:
        s += note(40, y, title, INK, 14, anchor='start', weight='700')
        for label, bx, bw, fill, stroke, ink in boxes:
            s += box(bx, y + 12, bw, 44, label, fill=fill, stroke=stroke,
                     label_fill=ink, fs=14)
        s += note(540, y + 40, verdict, verdict_color, 14, anchor='start', weight='600')

    s += note(500, 400, 'Outbox 補的就是第三種：事件不放記憶體，跟資料寫進同一個交易', INK, 15, weight='700')
    write('014-a-event-timing.svg', s)


# ── 014-b Outbox 的原子性（EVENT 系列 E2）───────────────
def d014b():
    s = svg_open(1000, 400, 'Outbox：把事件變成一筆資料')
    red_bg = '#fee2e2'

    # 左：同一個交易框住「改資料」與「寫 outbox」
    s += (f'<rect x="50" y="98" width="340" height="150" rx="12" fill="none" '
          f'stroke="{AMBER}" stroke-width="1.8" stroke-dasharray="6,5"/>')
    s += note(220, 90, '同一個交易', AMBER, 14, weight='700')
    s += box(75, 118, 290, 50, '更新客戶資料', fill=PANEL, stroke=BORDER, fs=15)
    s += box(75, 182, 290, 50, '寫入 outbox 一筆', fill=TEAL_BG, stroke=TEAL,
             label_fill=TEAL, fs=15)

    # 中：兩種結果，沒有中間態
    s += arrow(392, 143, 456, 128, LINE, None, width=2.0)
    s += box(466, 104, 200, 48, 'commit：兩筆都在', fill=TEAL_BG, stroke=TEAL,
             label_fill=TEAL, fs=14)
    s += arrow(392, 207, 456, 222, LINE, None, width=2.0)
    s += box(466, 198, 200, 48, 'rollback：兩筆都沒有', fill=red_bg, stroke=RED,
             label_fill=RED, fs=14)

    # 右：排程把 outbox 真的送出去
    s += arrow(670, 128, 722, 128, LINE, None, width=2.0)
    s += box(732, 104, 220, 48, '排程撈出未送出的事件', fill=PANEL, stroke=BORDER, fs=14)
    s += arrow(842, 152, 842, 194, LINE, None, width=2.0)
    s += box(732, 198, 220, 48, '真的送出去', fill=PANEL, stroke=BORDER, fs=14)

    s += note(500, 312, 'Outbox 把「兩件事要一起成功」降級成「一次資料庫交易」', INK, 15, weight='700')
    s += note(500, 344, '代價：多一張表、多一個排程——下游漏了會出事才值得', MUTED, 13)
    write('014-b-outbox-atomicity.svg', s)


# ── 015-a 至少一次送達是怎麼來的（EVENT 系列 E3）────────
def d015a():
    s = svg_open(1000, 390, '為什麼事件一定會重送')
    red_bg = '#fee2e2'

    s += note(500, 86, '排程送出事件的三個步驟', INK, 16, weight='700')
    s += box(110, 104, 180, 50, '① 撈出未送出的', fill=PANEL, stroke=BORDER, fs=15)
    s += arrow(292, 129, 326, 129, LINE, None, width=2.0)
    s += box(336, 104, 160, 50, '② 送出去', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=15)
    s += arrow(498, 129, 532, 129, RED, None, width=2.0)
    s += box(542, 104, 140, 50, '當機', fill=red_bg, stroke=RED, label_fill=RED, fs=15)
    s += box(722, 104, 180, 50, '③ 標記已送出', fill='#ffffff', stroke=BORDER,
             label_fill='#c2cad8', fs=15)
    s += note(812, 176, '這一步沒跑到', MUTED, 13, weight='600')
    s += note(612, 176, '在 ② 和 ③ 之間當機', RED, 13, weight='600')

    s += (f'<line x1="40" y1="206" x2="960" y2="206" stroke="{BORDER}" '
          f'stroke-width="1.5" stroke-dasharray="6,6"/>')

    s += note(500, 240, '重開之後：資料庫還記著「這筆沒送」', INK, 16, weight='700')
    s += box(110, 258, 180, 50, '① 又撈到同一筆', fill=PANEL, stroke=BORDER, fs=15)
    s += arrow(292, 283, 326, 283, LINE, None, width=2.0)
    s += box(336, 258, 160, 50, '② 再送一次', fill=AMBER_BG, stroke=AMBER,
             label_fill=AMBER, fs=15)
    s += note(520, 289, '→ 下游收到第二次：同一個事件、兩次處理', AMBER, 14,
              anchor='start', weight='600')

    s += note(500, 356, '這就是「至少一次送達」——去重是接收端的責任，不是送出端的', INK, 15, weight='700')
    write('015-a-at-least-once.svg', s)


# ── 015-b 冪等保護的順序（EVENT 系列 E3）────────────────
def d015b():
    s = svg_open(1000, 430, '同樣 10 條執行緒搶同一個事件')
    red_bg = '#fee2e2'

    s += (f'<line x1="500" y1="70" x2="500" y2="360" stroke="{BORDER}" '
          f'stroke-width="1.5" stroke-dasharray="6,6"/>')

    # 左：先卡鍵，資料庫擋
    s += note(255, 92, '先卡鍵：insert 讓資料庫擋', TEAL, 16, weight='700')
    s += box(135, 110, 240, 46, '10 條執行緒同時進來', fill=PANEL, stroke=BORDER, fs=14)
    s += arrow(255, 156, 255, 186, LINE, None, width=2.0)
    s += box(135, 190, 240, 50, 'insert 冪等鍵', fill=AMBER_BG, stroke=AMBER,
             label_fill=AMBER, fs=15)
    s += note(255, 262, '主鍵約束：檢查與佔位', MUTED, 13)
    s += note(255, 282, '是同一個原子操作', MUTED, 13)
    s += arrow(255, 296, 255, 322, LINE, None, width=2.0)
    s += box(180, 326, 150, 44, '1 條通過', fill=TEAL_BG, stroke=TEAL,
             label_fill=TEAL, fs=15)
    s += note(255, 402, '實測 LLM 呼叫次數 ＝ 1', TEAL, 17, weight='700')

    # 右：先查再做，中間有空隙
    s += note(745, 92, '先查再做：中間有空隙', RED, 16, weight='700')
    s += box(625, 110, 240, 46, '10 條執行緒同時進來', fill=PANEL, stroke=BORDER, fs=14)
    s += arrow(745, 156, 745, 186, LINE, None, width=2.0)
    s += box(625, 190, 240, 50, 'select 查處理過沒有', fill=red_bg, stroke=RED,
             label_fill=RED, fs=15)
    s += note(745, 262, '查與寫之間的空隙裡，', RED, 13)
    s += note(745, 282, '十條全都查到「還沒處理」', RED, 13)
    s += arrow(745, 296, 745, 322, RED, None, width=2.0)
    s += box(670, 326, 150, 44, '10 條全過', fill=red_bg, stroke=RED,
             label_fill=RED, fs=15)
    s += note(745, 402, '實測 LLM 呼叫次數 ＝ 10', RED, 17, weight='700')

    write('015-b-idempotency-order.svg', s)


# ── 016-a 任務狀態機與死信（EVENT 系列 E4）──────────────
def d016a():
    s = svg_open(1000, 400, '一個任務的一生：只進不退')
    red_bg = '#fee2e2'

    s += box(70, 150, 150, 54, 'PENDING', fill=PANEL, stroke=BORDER, fs=16)
    s += note(145, 224, '已登記，還沒開始', MUTED, 12)
    s += arrow(222, 177, 276, 177, LINE, None, width=2.2)

    s += box(286, 150, 150, 54, 'RUNNING', fill=AMBER_BG, stroke=AMBER,
             label_fill=AMBER, fs=16)
    s += note(361, 224, '正在呼叫 LLM', MUTED, 12)

    s += arrow(438, 168, 496, 134, LINE, None, width=2.2)
    s += box(506, 106, 150, 54, 'DONE', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=16)
    s += note(581, 180, '✗ 不可退回 RUNNING', RED, 12, weight='600')

    s += arrow(438, 190, 496, 226, LINE, None, width=2.2)
    s += box(506, 202, 150, 54, 'FAILED', fill=red_bg, stroke=RED, label_fill=RED, fs=16)
    s += note(581, 276, '重試到上限，放棄', MUTED, 12)

    s += arrow(658, 229, 706, 229, RED, None, width=2.2)
    s += box(716, 202, 190, 54, '死信表', fill=red_bg, stroke=RED, label_fill=RED, fs=16)
    s += note(811, 276, '查得到、可人工重跑', MUTED, 12)

    s += note(500, 340, 'update … where status = \'PENDING\' —— 更新到 0 筆就代表輪不到你，直接跳過',
              MUTED, 13)
    s += note(500, 372, '只進不退，擋掉「重送的舊事件蓋掉新結果」這種極難查的問題', INK, 15, weight='700')
    write('016-a-status-machine.svg', s)


# ── 016-b 指數退避與溢位反例（EVENT 系列 E4）────────────
def d016b():
    s = svg_open(1000, 430, '退避要愈退愈慢——除非它溢位了')
    red_bg = '#fee2e2'

    # 上半：正確的退避序列，用長條高度呈現指數增長
    s += note(500, 86, '正確版：夾住指數再位移', TEAL, 16, weight='700')
    values = [1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000]
    labels = ['1s', '2s', '4s', '8s', '16s', '30s', '30s', '30s']
    baseline = 232
    x = 150
    for i, (value, label) in enumerate(zip(values, labels), start=1):
        h = 12 + value / 30000 * 108
        capped = value == 30000
        s += (f'<rect x="{x}" y="{baseline - h}" width="58" height="{h}" rx="6" '
              f'fill="{AMBER_BG if capped else TEAL_BG}" '
              f'stroke="{AMBER if capped else TEAL}" stroke-width="1.5"/>')
        s += note(x + 29, baseline - h - 10, label, AMBER if capped else TEAL, 14, weight='700')
        s += note(x + 29, baseline + 20, f'第 {i} 次', MUTED, 11)
        x += 80
    s += note(500, 276, '第 6 次之後碰到 30 秒上限，維持不變', MUTED, 13)

    s += (f'<line x1="40" y1="300" x2="960" y2="300" stroke="{BORDER}" '
          f'stroke-width="1.5" stroke-dasharray="6,6"/>')

    # 下半：溢位反例
    s += note(500, 330, '反例：沒夾住指數，attempts 一路累加下去', RED, 16, weight='700')
    s += box(180, 344, 300, 46, 'attempt = 55 → 退避 −4.3×10¹⁷ 毫秒',
             fill=red_bg, stroke=RED, label_fill=RED, fs=14)
    s += box(520, 344, 300, 46, 'attempt = 64 → 退避 0 毫秒',
             fill=red_bg, stroke=RED, label_fill=RED, fs=14)
    s += note(500, 414, '負的或零的退避，效果一樣：完全不等，變成對著故障下游的重試風暴',
              INK, 15, weight='700')
    write('016-b-backoff-dlq.svg', s)


# ── 017-a 一次 Agent 執行的軌跡（EVENT 系列 E5）─────────
def d017a():
    s = svg_open(1000, 490, '一次摘要產生，留下六行軌跡')
    s += note(500, 76, 'trace_id = trace-a（同一次執行的六個步驟共用）', MUTED, 13)

    steps = [
        ('PROMPT', 'system prompt v3 ＋ 客戶 42 的近況請求', 320, AMBER_BG, AMBER),
        ('TOOL_CALL', 'findRecentInteractions(customerId=42)', None, PANEL, BORDER),
        ('TOOL_RESULT', '回傳 5 筆互動紀錄', 210, PANEL, BORDER),
        ('TOOL_CALL', 'calculateHealthScore(customerId=42)', None, PANEL, BORDER),
        ('TOOL_RESULT', '健康分數 72', 40, PANEL, BORDER),
        ('LLM_RESPONSE', '客戶 42 近期互動頻率下降，建議主動聯繫', 180, TEAL_BG, TEAL),
    ]
    y = 96
    for name, content, tokens, fill, stroke in steps:
        ink = stroke if stroke in (AMBER, TEAL) else MUTED
        s += box(60, y, 190, 40, name, fill=fill, stroke=stroke, label_fill=ink, fs=14)
        s += note(266, y + 26, content, INK, 14, anchor='start')
        s += note(940, y + 26, f'{tokens} tokens' if tokens else '—', MUTED, 13, anchor='end')
        y += 48

    s += (f'<line x1="60" y1="{y + 4}" x2="940" y2="{y + 4}" stroke="{BORDER}" '
          f'stroke-width="1.2"/>')
    s += note(940, y + 30, '總計 750 tokens', INK, 15, anchor='end', weight='700')
    s += note(500, y + 66, '「這份摘要是怎麼來的」——六行就交代完了', INK, 15, weight='700')
    write('017-a-agent-trace.svg', s)


# ── 017-b 軌跡表的三個用途（EVENT 系列 E5）──────────────
def d017b():
    s = svg_open(1000, 396, '同一張表，三個答案')
    s += box(380, 84, 240, 56, 'agent_steps 軌跡表', fill=TEAL_BG, stroke=TEAL,
             label_fill=TEAL, fs=16)

    cards = [
        (50, '回放', 'order by step_no', '它讀了什麼？'),
        (380, '成本歸屬', 'sum(tokens)', '這次花了多少？'),
        (710, '稽核', '依 step_type 查', '這結論怎麼來的？'),
    ]
    for x, title, sql, question in cards:
        cx = x + 120
        s += arrow(500, 146, cx, 186, LINE, None, width=2.0)
        s += box(x, 190, 240, 112, '', fill=PANEL, stroke=BORDER)
        s += note(cx, 224, title, TEAL, 17, weight='700')
        s += note(cx, 254, sql, MUTED, 13)
        s += note(cx, 284, question, INK, 14, weight='600')

    # 說明文字放在卡片下方，避開上方三條分岔箭頭的路徑
    s += note(500, 334, '只 insert、不 update——事實不會被改寫', MUTED, 13)
    s += note(500, 368, '你要求 AI 交出可審查的產物——這是系統對自己提同一個要求', INK, 15, weight='700')
    write('017-b-trace-three-uses.svg', s)


# ── 017-c 傳統入站 vs Tunnel 反向通道（TUNNEL 系列 T0）──
def d017c():
    s = svg_open(1000, 420, '傳統入站被擋 vs Tunnel 反向通道')
    red_bg = '#fee2e2'

    # 左側：傳統入站方式
    s += box(40, 70, 430, 310, '', fill='#ffffff', stroke=BORDER)
    s += note(255, 100, '傳統入站：被防火牆擋下', RED, 17, weight='700')
    s += box(65, 150, 110, 80, '外部使用者\n/ Webhook', fill=PANEL, stroke=BORDER, fs=14)
    s += box(205, 130, 100, 120, '路由器\nNAT / 防火牆', fill=red_bg, stroke=RED, label_fill=RED, fs=14)
    s += box(335, 150, 110, 80, '本地電腦\n:8080', fill=PANEL, stroke=BORDER, fs=14)
    s += arrow(175, 190, 205, 190, RED, 'Inbound 請求', width=2.0)
    s += note(255, 275, '❌ 防火牆預設阻擋未知的連入請求', RED, 13, weight='600')
    s += note(255, 305, '開 Port 轉發危險、無固定 IP 連不到', MUTED, 12)
    s += note(255, 355, '老派解法：申請固定 IP / 開 Port Forwarding', MUTED, 13)

    # 右側：Tunnel 反向通道方式
    s += box(530, 70, 430, 310, '', fill='#ffffff', stroke=BORDER)
    s += note(745, 100, 'Tunnel：主動向外建立通道', TEAL, 17, weight='700')
    s += box(555, 150, 110, 80, '本地電腦\n(cloudflared)', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=14)
    s += box(825, 150, 110, 80, '雲端邊緣\n(Anycast Edge)', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=14)
    
    # 本地出站連線箭頭（下方）
    s += arrow(665, 175, 825, 175, TEAL, '① 主動建立出站連線 (Outbound)', width=2.2, label_dy=-10)
    # 外部請求與流量回傳
    s += arrow(825, 205, 665, 205, BLUE, '② 順著通道塞回請求', width=2.0, label_dy=18)
    s += note(745, 275, '✓ 防火牆放行出站長連線（QUIC / HTTP2）', TEAL, 13, weight='600')
    s += note(745, 305, '免開 Port、免固定 IP、自帶免費 SSL', INK, 12, weight='600')
    s += note(745, 355, '現代解法：Cloudflare Tunnel / ngrok', INK, 13, weight='700')

    write('017-c-reverse-tunnel.svg', s)


# ── 018-a Cloudflare Tunnel Ingress 路由（TUNNEL 系列 T1）──
def d018a():
    s = svg_open(1000, 562, 'Cloudflare Tunnel Ingress：同站路由與 CORS 消除')
    s += note(500, 75, '一份 config.yml，把前後端收攏進同一網域名稱 crm.yourdomain.com', MUTED, 14)

    # 左側：瀏覽器
    s += box(40, 110, 160, 360, '瀏覽器使用者', sub='Single Origin', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=16)
    s += note(120, 220, '請求頁面', INK, 13, weight='600')
    s += note(120, 240, 'https://crm.../', MUTED, 12)
    s += note(120, 320, '呼叫 API', INK, 13, weight='600')
    s += note(120, 340, 'fetch("/api/customers")', MUTED, 12)

    # 中間：Cloudflare 邊緣 Ingress 規則
    s += box(260, 105, 420, 370, '', fill='#ffffff', stroke=BORDER)
    s += note(470, 135, 'Cloudflare 雲端邊緣 (Anycast Edge)', TEAL, 17, weight='700')
    s += note(470, 160, 'config.yml Ingress 規則由上而下比對 (First-match-wins)', MUTED, 13)

    # 規則 1
    s += box(280, 185, 380, 75, '規則 1: path: ^/api', sub='命中！轉送至後端 Spring Boot', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=14)
    # 規則 2
    s += box(280, 275, 380, 75, '規則 2: hostname: crm.yourdomain.com', sub='命中！轉送至前端 React Vite', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=14)
    # 規則 3
    s += box(280, 365, 380, 60, '規則 3: - service: http_status:404', sub='未匹配之請求一律回傳 404 兜底', fill=PANEL, stroke=BORDER, fs=13)

    # 右側：本地伺服器
    s += box(740, 105, 220, 370, '', fill='#ffffff', stroke=BORDER)
    s += note(850, 135, '本地開發機 (Localhost)', INK, 16, weight='700')

    s += box(760, 175, 180, 110, 'Spring Boot 後端', sub='localhost:8080\n處理 /api/* 與 SSE', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=15)
    s += box(760, 310, 180, 110, 'React Vite 前端', sub='localhost:5173\n處理 SPA 頁面與靜態資源', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=15)

    # 箭頭連線
    s += arrow(200, 230, 260, 230, BLUE, '同站請求', width=2.0)
    s += arrow(660, 220, 760, 220, TEAL, 'QUIC 轉發 :8080', width=2.2)
    s += arrow(660, 310, 760, 360, BLUE, 'QUIC 轉發 :5173', width=2.2)

    # 底部說明
    s += note(500, 505, '✓ 瀏覽器視角完全同源（Same-Origin）：永遠不發送 OPTIONS preflight 預檢請求，延遲省一半', INK, 14, weight='700')
    s += note(500, 535, '✓ 前端 API URL 寫相對路徑 /api 即可，彻底拔除 Spring Security 繁瑣脆弱的 CORS 配置', MUTED, 13)

    write('018-a-tunnel-ingress-routing.svg', s)


# ── 018-b QUIC 多工傳輸長連線（TUNNEL 系列 T1）──────────
def d018b():
    s = svg_open(1000, 562, 'Tunnel 底層連線：4 條 QUIC 平行長連線與多工傳輸')
    s += note(500, 75, 'cloudflared 不是一般 HTTP 代理，而是主動與邊緣建立 4 條 QUIC 加密通道', MUTED, 14)

    # 左側：本地電腦
    s += box(40, 110, 180, 360, '本地電腦', sub='cloudflared Agent', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=17)
    s += note(130, 230, 'Spring Boot :8080', INK, 13, weight='600')
    s += note(130, 265, 'React Vite :5173', INK, 13, weight='600')
    s += note(130, 300, 'WebSocket /ws', INK, 13, weight='600')
    s += note(130, 335, 'SSE AI Stream', INK, 13, weight='600')
    s += note(130, 390, '單一 Agent 出站', MUTED, 12)

    # 中間：4 條 QUIC 管道
    tunnels = [
        ('QUIC 通道 #1 (UDP 7844)', 'Stream A: REST API 請求與回應', TEAL, TEAL_BG),
        ('QUIC 通道 #2 (UDP 7844)', 'Stream B: React HTML / JS / CSS 靜態資源', BLUE, BLUE_BG),
        ('QUIC 通道 #3 (UDP 7844)', 'Stream C: Spring AI SSE 串流打字機', AMBER, AMBER_BG),
        ('QUIC 通道 #4 (UDP 7844)', 'Stream D: WebSocket 雙向即時通訊', TEAL, TEAL_BG),
    ]
    y = 120
    for name, desc, color, bg in tunnels:
        s += box(270, y, 460, 75, name, sub=desc, fill=bg, stroke=color, label_fill=color, fs=14)
        s += arrow(220, y + 37, 270, y + 37, color, None, width=2.0)
        s += arrow(730, y + 37, 780, y + 37, color, None, width=2.0)
        y += 90

    # 右側：Cloudflare Anycast 邊緣節點
    s += box(780, 110, 180, 360, 'Cloudflare 邊緣', sub='Anycast Edge Mesh', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=17)
    s += note(870, 230, '全球 300+ 城市節點', INK, 13, weight='600')
    s += note(870, 265, '自動負載平衡', INK, 13, weight='600')
    s += note(870, 300, 'DDoS 防護 + Anycast', INK, 13, weight='600')
    s += note(870, 335, '自動容錯轉移 (HA)', INK, 13, weight='600')
    s += note(870, 390, '免費自動 SSL 憑證', MUTED, 12)

    # 底部說明
    s += note(500, 505, '✓ 多工傳輸（Multiplexing）：所有請求在 4 條連線內並行分流，徹底消滅 TCP 隊頭阻塞（Head-of-Line Blocking）', INK, 14, weight='700')
    s += note(500, 535, '✓ 高可用架構：4 條連線分別連往不同邊緣伺服器，單點斷線毫秒級自動切換，連線不中斷', MUTED, 13)

    write('018-b-quic-multiplexing.svg', s)


# ── 019-a 本地真實 Webhook 斷點與秒級解耦（TUNNEL 系列 T2）──
def d019a():
    s = svg_open(1000, 562, '真實 Webhook 本地斷點除錯與秒級解耦架構')
    s += note(500, 75, '告別 Mock，讓 Stripe / LINE / GitHub 的真實事件直接打進本地 IDE', MUTED, 14)

    # 左側：外部 Webhook 來源
    s += box(40, 110, 170, 360, '外部平台', sub='Stripe / LINE / GitHub', fill=PANEL, stroke=BORDER, fs=16)
    s += note(125, 210, '發送 Webhook', INK, 13, weight='600')
    s += note(125, 235, 'Header: 簽章', MUTED, 12)
    s += note(125, 255, 'Body: JSON Payload', MUTED, 12)
    s += box(55, 300, 140, 70, '⚡ 超時限制\n通常僅 5 秒！', fill='#fee2e2', stroke=RED, label_fill=RED, fs=13)
    s += note(125, 410, '超時即發動重試風暴', RED, 12, weight='600')

    # 中間：本地 Spring Boot 接收端
    s += box(260, 105, 430, 370, '', fill='#ffffff', stroke=BORDER)
    s += note(475, 135, '本地 Spring Boot Webhook Controller', TEAL, 16, weight='700')

    s += box(280, 160, 390, 60, '① IDE 中斷點（Breakpoint）攔截', sub='查看真實複雜 JSON 結構與 Metadata', fill=AMBER_BG, stroke=AMBER, label_fill=AMBER, fs=13)
    s += box(280, 230, 390, 60, '② HMAC-SHA256 驗簽比對', sub='使用原始 Raw Bytes 運算，防篡改', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=13)
    s += box(280, 300, 390, 60, '③ 50ms 內立即回傳 200 OK', sub='通知第三方「已安全收單」，終止重試', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=13)
    s += box(280, 370, 390, 85, '④ publishEvent(PaymentCompletedEvent)', sub='拋入 Spring 事件總線，解耦業務邏輯', fill=PANEL, stroke=TEAL, label_fill=TEAL, fs=13)

    # 右側：非同步背景處理
    s += box(740, 105, 220, 370, '', fill='#ffffff', stroke=BORDER)
    s += note(850, 135, '非同步背景執行緒池', INK, 16, weight='700')
    s += box(760, 175, 180, 80, '@Async\n監聽處理', sub='異步重度任務', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=14)
    s += note(850, 285, '• 寫入 DB 資料庫', INK, 13)
    s += note(850, 315, '• 呼叫 LLM 生成摘要', INK, 13)
    s += note(850, 345, '• 發送 Email / LINE', INK, 13)
    s += note(850, 375, '• 015 冪等表去重', TEAL, 13, weight='600')

    # 連線箭頭
    s += arrow(210, 230, 260, 230, TEAL, 'Tunnel 穿透', width=2.0)
    s += arrow(670, 410, 760, 215, TEAL, '內部事件解耦', width=2.2)
    s += arrow(280, 330, 210, 330, BLUE, '200 OK (50ms)', width=2.0)

    # 底部說明
    s += note(500, 505, '✓ 接收端黃金法則：驗簽通過立刻秒回 200 OK，不要在 Webhook Thread 裡面執行耗時的業務邏輯', INK, 14, weight='700')
    s += note(500, 535, '✓ 結合 015 冪等鍵（Idempotency Key）：即便本地斷點超時引發第三方重試，後端也能天然安全去重', MUTED, 13)

    write('019-a-webhook-breakpoint-flow.svg', s)


# ── 019-b 串流枯竭與 HMAC 常數時間驗簽（TUNNEL 系列 T2）──
def d019b():
    s = svg_open(1000, 562, 'InputStream 串流不可逆性 vs HMAC 常數時間驗簽')
    s += note(500, 75, '搞懂 Request Body 只能讀一次的底層限制，與防範時序攻擊的驗簽實踐', MUTED, 14)

    # 上半部：串流枯竭反例
    s += box(40, 100, 920, 160, '', fill='#ffffff', stroke=BORDER)
    s += note(80, 125, '❌ 串流枯竭陷阱（ServletInputStream Stream Exhaustion）', RED, 16, anchor='start', weight='700')

    s += box(70, 145, 230, 95, 'HTTP Request Body\n(TCP Socket Stream)', sub='單向前進指針 (Cursor)', fill=PANEL, stroke=BORDER, fs=14)
    s += arrow(300, 192, 380, 192, RED, '@RequestBody 讀取', width=2.0)
    s += box(380, 145, 260, 95, 'Spring HttpMessageConverter\n(Jackson 反序列化)', sub='消耗 Stream 轉為 DTO 物件\n游標抵達 EOF', fill='#fee2e2', stroke=RED, label_fill=RED, fs=14)
    s += arrow(640, 192, 720, 192, RED, '二次讀取', width=2.0)
    s += box(720, 145, 210, 95, 'request.getInputStream()\n讀取 Raw Bytes', sub='❌ 游標在 EOF，回傳空值！\n導致驗簽必定失敗', fill='#fee2e2', stroke=RED, label_fill=RED, fs=14)

    # 下半部：正確做法
    s += box(40, 280, 920, 210, '', fill='#ffffff', stroke=BORDER)
    s += note(80, 305, '✓ 正確解法：提取 Raw Bytes ＋ HMAC-SHA256 常數時間比對', TEAL, 16, anchor='start', weight='700')

    steps = [
        (70, '① 讀取原始 Bytes', 'request.getInputStream()\n.readAllBytes()', TEAL_BG, TEAL),
        (300, '② 本地重算 HMAC', 'HMAC_SHA256(rawBytes, secret)\n運算出 localSignature', PANEL, BORDER),
        (530, '③ 常數時間比對', 'MessageDigest.isEqual()\n(防 Timing Attack 時序攻擊)', BLUE_BG, BLUE),
        (760, '④ 手動反序列化', 'objectMapper.readValue()\n安全轉換為 Event DTO', TEAL_BG, TEAL),
    ]
    for x, title, desc, bg, stroke in steps:
        s += box(x, 325, 200, 105, title, sub=desc, fill=bg, stroke=stroke, label_fill=stroke if stroke != BORDER else INK, fs=14)
        if x < 760:
            s += arrow(x + 200, 377, x + 230, 377, TEAL, None, width=2.0)

    s += note(500, 460, '✓ 原始 bytes 未經任何 JSON 格式化更動，確保雜湊完全吻合；比對使用常數時間演算法防禦微秒時間差探測', TEAL, 13, weight='600')

    # 底部總結
    s += note(500, 525, '底層原則：HTTP Request Stream 是不可倒帶的（Non-rewindable），驗簽必須以最原始未解析的字節為準', INK, 14, weight='700')

    write('019-b-request-wrapper-hmac.svg', s)


# ── 020-a Zero Trust 邊緣攔截與 Bypass 混合防護（TUNNEL 系列 T3）──
def d020a():
    s = svg_open(1000, 562, 'Zero Trust 邊緣身分驗證與 Webhook Bypass 混合防護')
    s += note(500, 75, '防線前移到全球 Anycast 邊緣：人類走 SSO/OTP 驗證，機器 Webhook 走 Bypass 繞過', MUTED, 14)

    # 左側：3 類請求
    s += box(40, 110, 190, 360, '公網存取來源', fill=PANEL, stroke=BORDER, fs=16)

    s += box(60, 145, 150, 80, '人類訪客 / 員工\n(瀏覽器存取)', sub='存取 CRM 與 AI 介面', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=13)
    s += box(60, 250, 150, 80, '第三方 Webhook\n(Stripe / LINE)', sub='機器伺服器回調', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=13)
    s += box(60, 355, 150, 80, '惡意爬蟲 / 掃描器\n(濫用 API / 刷 Token)', sub='未授權的外部連線', fill='#fee2e2', stroke=RED, label_fill=RED, fs=13)

    # 中間：Cloudflare Zero Trust 邊緣
    s += box(280, 105, 390, 370, '', fill='#ffffff', stroke=BORDER)
    s += note(475, 135, 'Cloudflare Anycast 邊緣 (Access 防線)', TEAL, 16, weight='700')

    # 注意：Access 的保護範圍由「Application（網域＋路徑）」界定，Policy 只負責「誰能過」；
    # 因此 Webhook 放行必須另建一個帶 path 的 Application，再對它掛 Bypass 政策（Policy 條件不支援 URL path）。
    s += box(300, 155, 350, 75, 'Application A：crm.yourdomain.com', sub='Allow 政策：檢查 Cookie ➔ 302 Google SSO / OTP\n驗證通過發放 JWT 並注入 Header', fill=BLUE_BG, stroke=BLUE, label_fill=BLUE, fs=13)
    s += box(300, 250, 350, 75, 'Application B：/api/webhook/*', sub='另建含 path 的應用並掛 Bypass 政策放行\n（不驗證身分，改由後端 HMAC 簽章守護）', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=13)
    s += box(300, 355, 350, 75, '未命中 Allow：邊緣阻擋', sub='未登入且非 Bypass 路徑之請求\n邊緣直接回傳 403 Forbidden ❌', fill='#fee2e2', stroke=RED, label_fill=RED, fs=13)

    # 右側：本地 Spring Boot
    s += box(720, 105, 240, 370, '', fill='#ffffff', stroke=BORDER)
    s += note(840, 135, '本地 Spring Boot (localhost:8080)', INK, 15, weight='700')
    s += box(740, 165, 200, 110, '乾淨的業務請求', sub='Header 帶入驗證資訊:\nCf-Access-Authenticated-User-Email\nCf-Access-Jwt-Assertion', fill=TEAL_BG, stroke=TEAL, label_fill=TEAL, fs=14)
    s += box(740, 295, 200, 110, 'Webhook 處理器', sub='接收 Raw Bytes 進行\nHMAC-SHA256 驗簽', fill=PANEL, stroke=BORDER, fs=14)
    s += note(840, 440, '🛡️ 連線碰不到惡意流量！', TEAL, 13, weight='700')

    # 箭頭連線
    s += arrow(210, 185, 300, 185, BLUE, None, width=2.0)
    s += arrow(210, 290, 300, 290, TEAL, None, width=2.0)
    s += arrow(210, 395, 300, 395, RED, None, width=2.0)

    s += arrow(650, 192, 740, 192, TEAL, 'QUIC 轉發', width=2.0)
    s += arrow(650, 287, 740, 340, TEAL, 'QUIC 轉發', width=2.0)

    # 底部說明
    s += note(500, 505, '✓ 未登入者與惡意爬蟲連本地電腦的 TCP 握手都碰不到，在邊緣直接被擋下，徹底守護 AI Token 帳單', INK, 14, weight='700')
    s += note(500, 535, '✓ 後端無須自建複雜 OAuth / JWT 登入系統，零維護成本享有企業級 Google 登入與 OTP 團隊白名單', MUTED, 13)

    write('020-a-zero-trust-edge-barrier.svg', s)


# ── 020-b TUNNEL 系列全景總結（TUNNEL 系列 T3・系列完結）──
def d020b():
    s = svg_open(1000, 562, 'TUNNEL 系列全景總結：從內網穿透到零信任防禦')
    s += note(500, 75, '四期連貫架構——打造現代工程師隨時隨地安全發布本地服務的完整能力', MUTED, 14)

    cards = [
        (40, 'T0 原理篇 (017)', '從 WebRTC 到反向通道', [
            '• 防火牆只擋 Inbound 入站',
            '• 主動向 Edge 建立出站連線',
            '• 免開 Port、免固定 IP',
            '• 告別老派危險 Port Forwarding',
        ], PANEL, BORDER, INK),
        (275, 'T1 實戰篇 (018)', '同站路由與 CORS 消除', [
            '• 一份 config.yml Ingress 規則',
            '• /api 導後端，其餘導前端',
            '• 完全同源，消滅 OPTIONS 預檢',
            '• 4 條 QUIC 平行多工傳輸',
        ], TEAL_BG, TEAL, TEAL),
        (510, 'T2 除錯篇 (019)', '本機攔截真實 Webhook', [
            '• 告別 Mock，IDE 設斷點攔截',
            '• 解決 InputStream 串流枯竭',
            '• HMAC-SHA256 常數時間驗簽',
            '• 50ms 秒回 200 + 異步解耦',
        ], BLUE_BG, BLUE, BLUE),
        (745, 'T3 防護篇 (020)', 'Zero Trust 零信任防衛', [
            '• 邊緣攔截，防禦 AI Token 帳單',
            '• Google SSO / Email OTP 白名單',
            '• Webhook Bypass 專用放行',
            '• RS256 離線驗證 JWT 信任鏈',
        ], AMBER_BG, AMBER, AMBER),
    ]

    for x, badge, title, items, bg, stroke, badge_col in cards:
        s += box(x, 110, 215, 360, '', fill=bg, stroke=stroke)
        s += note(x + 107, 145, badge, badge_col, 15, weight='700')
        s += note(x + 107, 175, title, INK, 14, weight='700')
        s += f'<line x1="{x+20}" y1="195" x2="{x+195}" y2="195" stroke="{stroke}" stroke-width="1.2"/>'
        
        iy = 225
        for item in items:
            s += note(x + 18, iy, item, INK, 12, anchor='start', weight='500')
            iy += 42

    s += note(500, 505, '從單機孤島、真實回調到企業級零信任閉環——用現代雲端邊緣網路武裝你的全端開發流！', INK, 14, weight='700')
    s += note(500, 535, '《AI 賦能全端開發：從零打造企業級智慧應用》課程加碼單元・完整實踐', TEAL, 13, weight='600')

    write('020-b-tunnel-series-summary.svg', s)


if __name__ == '__main__':
    d008a(); d008b(); d009a(); d009b(); d010a(); d010b(); d011a(); d011b()
    d012a(); d012b(); d013a(); d013b(); d013e(); d013f(); d014a(); d014b(); d015a(); d015b()
    d016a(); d016b(); d017a(); d017b(); d017c()
    d018a(); d018b(); d019a(); d019b(); d020a(); d020b()
    print('全部 29 張 SVG 產生完成')

