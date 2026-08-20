"""Mastering 美化鏈：修復完成後可選的第三階段，提供 podcast 質感的音色雕琢。

為何這裡允許壓縮器、而修復鏈（ar/chain.py）明令禁止 —— 兩者並不矛盾：

- 修復鏈的禁令是因為壓縮會把安靜段（含底噪）往上頂，破壞「afftdn 的降噪
  門檻有單一意義」的前提，也會抹掉樣本層拉平剛校正好的句間關係。
- mastering 作用在**已完成降噪與正規化的乾淨訊號**上：底噪已被壓到聽感
  地板以下、句間位準已拉齊，此時壓縮不再有「頂高底噪」的副作用，而是
  刻意的美學選擇 —— 提高能量密度，製造 podcast 的貼耳感。

各頻段／濾鏡的作用（三組 preset 共用同一套骨架，只差強度）：

- `equalizer f=110~120Hz +g`：胸腔共鳴感。講課人聲經 highpass 與降噪後
  低頻偏薄，小幅提升讓聲音有「份量」。
- `equalizer f=300Hz -g`：去渾濁。近距離麥克風與室內反射的能量堆積區，
  衰減後咬字更清楚。
- `equalizer f=4000~4500Hz +g`：presence（臨場感）。人聲子音與泛音的
  可懂度頻段，提升後聲音「往前站」。
- `treble f=9000~10000Hz +g`：空氣感。高頻 shelf，讓整體聽感明亮開闊。
- `acompressor`：能量密度。threshold 越低、ratio 越高，動態越緊實、
  越貼耳；attack 15ms 左右保住子音瞬態不被壓鈍。
- `aexciter`：高頻諧波光澤。以諧波激勵補回降噪削掉的高頻細節質感；
  conservative 檔刻意不掛，保守取向只做 EQ 與輕度壓縮。

三組 preset 於 2026-08-21 以真實素材（02.mp4）AB 試聽校準，
比較時所有樣本先響度校準到同一 LUFS 以消除響度偏誤。
"""

# 三組美化 preset。鍵名即 CLI --preset 的合法值；鏈字串為 ffmpeg -af 格式。
MASTER_PRESETS: dict[str, str] = {
    # 保守檔：僅 EQ 塑形 + 輕度壓縮，不掛 aexciter，音色改變最小
    "conservative": (
        "equalizer=f=120:t=q:w=1:g=1.5,"
        "equalizer=f=300:t=q:w=1.5:g=-1.5,"
        "equalizer=f=4000:t=q:w=1:g=1.5,"
        "treble=g=1.5:f=10000,"
        "acompressor=threshold=-16dB:ratio=2:attack=15:release=200"
    ),
    # 標準檔（預設，使用者 AB 試聽選定）：podcast 質感的平衡點
    "podcast": (
        "equalizer=f=120:t=q:w=1:g=2.5,"
        "equalizer=f=300:t=q:w=1.5:g=-2,"
        "equalizer=f=4000:t=q:w=1:g=2,"
        "treble=g=2:f=10000,"
        "acompressor=threshold=-18dB:ratio=2.5:attack=15:release=200,"
        "aexciter=amount=1.5:blend=0.7"
    ),
    # 濃郁檔：最重的 EQ／壓縮／激勵，廣播式厚實音色
    "rich": (
        "equalizer=f=110:t=q:w=1:g=3.5,"
        "equalizer=f=300:t=q:w=1.5:g=-2.5,"
        "equalizer=f=4500:t=q:w=1:g=2.5,"
        "treble=g=3:f=9000,"
        "acompressor=threshold=-20dB:ratio=3:attack=10:release=180,"
        "aexciter=amount=2.5:blend=1"
    ),
}


def build_master_chain(preset: str) -> str:
    """依 preset 名稱查表回傳美化濾鏡鏈。

    未知 preset 拋 ValueError 並列出全部可用選項 —— 使用者打錯字時
    要能直接從錯誤訊息照抄正確值，不必翻文件。
    """
    if preset not in MASTER_PRESETS:
        available = "、".join(sorted(MASTER_PRESETS))
        raise ValueError(f"未知的 preset：{preset}。可用選項：{available}")
    return MASTER_PRESETS[preset]
