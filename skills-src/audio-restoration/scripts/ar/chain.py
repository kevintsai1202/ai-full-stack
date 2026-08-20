"""ffmpeg 濾鏡鏈組裝。

順序固定：afftdn（降噪）→ highpass → deesser。
拉平不在這條鏈裡 —— 它已在樣本層由 gain 模組完成（見 Task 8），因為 ffmpeg
的 volume 表達式在語句交界會產生增益尖峰，且長度會超過命令列上限。
不可調換：afftdn 是門檻式運算，訊號位準決定何者被判為噪音，故必須先拉平。
highpass 與 deesser 只在該區診斷確有需要時才掛，無差別套用會削掉男聲低頻
或讓咬字變鈍。
"""
TRUE_PEAK = -1.5          # 目標真峰值（dBTP）
LRA = 11                  # 目標響度範圍
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


def build_loudnorm_measure_chain(target_lufs: float) -> str:
    """兩段式 loudnorm 的第一段：量測，輸出 JSON。"""
    return (f"loudnorm=I={target_lufs}:TP={TRUE_PEAK}:LRA={LRA}"
            f":print_format=json")


def build_loudnorm_apply_chain(target_lufs: float, measured: dict) -> str:
    """兩段式 loudnorm 的第二段：帶入量測值套用，並以 alimiter 收尾。

    linear=true 讓 loudnorm 走線性增益而非動態壓縮，避免破壞已拉平的動態。
    """
    return (
        f"loudnorm=I={target_lufs}:TP={TRUE_PEAK}:LRA={LRA}"
        f":measured_I={measured['input_i']}"
        f":measured_TP={measured['input_tp']}"
        f":measured_LRA={measured['input_lra']}"
        f":measured_thresh={measured['input_thresh']}"
        f":offset={measured['target_offset']}"
        f":linear=true:print_format=summary,"
        # level=false 是必要的：alimiter 的 level 預設 true，會對限幅後的訊號
        # 做「自動電平補償」，把 loudnorm 剛做完的線性正規化結果重新推高。
        # 實測：不加時輸出偏離目標 1.5-2.0 LUFS，加了之後誤差降到 0.0-0.5。
        # 我們用 alimiter 只為了防止真峰值超標，不要它動響度。
        f"alimiter=limit={10 ** (TRUE_PEAK / 20):.4f}:level=false"
    )
