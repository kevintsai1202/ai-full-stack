"""ffmpeg 濾鏡鏈組裝。

順序固定：afftdn（降噪）→ highpass → deesser。
拉平不在這條鏈裡 —— 它已在樣本層由 gain 模組完成（見 Task 8），因為 ffmpeg
的 volume 表達式在語句交界會產生增益尖峰，且長度會超過命令列上限。
不可調換：afftdn 是門檻式運算，訊號位準決定何者被判為噪音，故必須先拉平。
highpass 與 deesser 只在該區診斷確有需要時才掛，無差別套用會削掉男聲低頻
或讓咬字變鈍。
"""
# 處理階段的真峰值目標，比驗收標準（-1.5 dBTP）低 0.5 dB。
# 這 0.5 dB 是留給有損編碼的餘裕：真實素材實測顯示 pre-mux 的 WAV 真峰值
# 精確落在 -1.5，但 AAC 192k 重編後上升到 -1.4，直接超標。限幅器把訊號
# 壓到剛好卡在驗收線上，等於沒有任何容錯空間。
TRUE_PEAK_TARGET = -2.0
DEFAULT_NOISE_FLOOR = -40.0  # 沒有實測底噪時的退路值
NOISE_FLOOR_MIN = -80.0   # afftdn 的 nf 合法下限
NOISE_FLOOR_MAX = -20.0   # afftdn 的 nf 合法上限
# 高通截止頻率。低頻隆隆（冷氣、桌面震動、風切）主要落在 20-60Hz，
# 60Hz 已能濾掉大部分；男聲基頻約 85Hz 起，用 ffmpeg 預設的 2 階
# （-3dB 點就在截止頻率）時，80Hz 截止會讓 85Hz 衰減約 2.5dB，
# 對低音域男聲與 vocal fry 削得太多。取 60 保留安全邊際。
HIGHPASS_HZ = 60


def _clamp_noise_floor(value: float) -> float:
    """把底噪估計值夾到 afftdn 的合法範圍。

    量測值可能落在合法範圍外（例如極安靜的錄音低於 -80dB），
    超出範圍會讓 ffmpeg 直接報參數錯誤。
    """
    return max(NOISE_FLOOR_MIN, min(NOISE_FLOOR_MAX, float(value)))


def build_zone_chain(zone_plan: dict) -> str:
    """組出單一 zone 的濾鏡鏈（不含拉平與最終響度處理）。

    拉平已由 gain.build_gain_envelope 在樣本層完成，送進這條鏈的音訊位準
    已經統一 —— 這正是 afftdn 的門檻能有單一意義的前提。此處再掛 volume
    會讓增益被套用兩次。
    """
    # 用 round 而非 int：plan.json 是使用者可手動編輯的，寫成 18.9 時
    # 截斷會變 18，且偏差方向永遠偏弱，不會被任何驗證抓到
    denoise = round(float(zone_plan["denoise_db"]))
    # 底噪基準用該區實際量到的值。afftdn 的 nf 是噪音位準的起始估計，
    # 雖然 tn=1 會動態追蹤，但起始值仍影響收斂速度與前幾幀的判斷。
    # 前面已逐區量出底噪，這裡寫死一個固定值等於把那份資訊丟掉。
    noise_floor = _clamp_noise_floor(zone_plan.get("noise_floor_db", DEFAULT_NOISE_FLOOR))
    filters = [f"afftdn=nr={denoise}:nf={noise_floor:.0f}:tn=1"]
    if zone_plan.get("needs_highpass"):
        filters.append(f"highpass=f={HIGHPASS_HZ}")
    if zone_plan.get("needs_deesser"):
        filters.append("deesser=i=0.4:m=0.5:f=0.5")
    return ",".join(filters)


def build_linear_gain_chain(gain_db: float) -> str:
    """最終正規化的第二段：純線性增益 + 限幅。**刻意不用 loudnorm 套用。**

    真實素材實測揭露：拉平後 LRA 只剩約 2，但線性增益（+4.2dB）會讓真峰值
    從 -1.63 推到 +2.57 超過 TP 目標，loudnorm 判定線性模式無法達成，
    **靜默退回動態模式** —— 動態模式為了湊 LRA=11 會把安靜段（含底噪）
    往上推，實測底噪因此不降反升 11.6dB，正好抵銷降噪的成果。

    拉平已在樣本層完成，最後一步需要的只有「搬到目標響度 + 保護峰值」，
    loudnorm 的動態機制在這條流程裡沒有任何正當用途。volume 是純線性，
    永遠不會有 fallback；峰值保護交給 alimiter。

    level=false 是必要的：alimiter 的 level 預設 true 會做自動電平補償，
    把剛做完的正規化推歪（實測偏差 1.5-2.0 LUFS）。
    """
    return (
        f"volume={gain_db:.2f}dB,"
        f"alimiter=limit={10 ** (TRUE_PEAK_TARGET / 20):.4f}:level=false"
    )
