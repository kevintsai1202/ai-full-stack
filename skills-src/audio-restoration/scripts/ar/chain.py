"""ffmpeg 濾鏡鏈組裝。

順序固定：afftdn（降噪）→ highpass → deesser。
拉平不在這條鏈裡 —— 它已在樣本層由 gain 模組完成（見 Task 8），因為 ffmpeg
的 volume 表達式在語句交界會產生增益尖峰，且長度會超過命令列上限。
不可調換：afftdn 是門檻式運算，訊號位準決定何者被判為噪音，故必須先拉平。
highpass 與 deesser 只在該區診斷確有需要時才掛，無差別套用會削掉男聲低頻
或讓咬字變鈍。
"""
TRUE_PEAK = -1.5  # 目標真峰值（dBTP）
LRA = 11          # 目標響度範圍


def build_zone_chain(zone_plan: dict) -> str:
    """組出單一 zone 的濾鏡鏈（不含拉平與最終響度處理）。

    拉平已由 gain.build_gain_envelope 在樣本層完成，送進這條鏈的音訊位準
    已經統一 —— 這正是 afftdn 的門檻能有單一意義的前提。此處再掛 volume
    會讓增益被套用兩次。
    """
    filters = [f"afftdn=nr={int(zone_plan['denoise_db'])}:nf=-40:tn=1"]
    if zone_plan.get("needs_highpass"):
        filters.append("highpass=f=80")
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
        f"alimiter=limit={10 ** (TRUE_PEAK / 20):.4f}"
    )
