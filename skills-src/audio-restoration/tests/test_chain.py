"""chain 模組測試：濾鏡順序與條件掛載。"""
from ar.chain import (build_loudnorm_apply_chain, build_loudnorm_measure_chain,
                      build_zone_chain)


def _zone_plan(**overrides) -> dict:
    """建立測試用 zone 計畫。"""
    base = {"gain_db": 3.0, "denoise_db": 18, "noise_floor_db": -45.0,
            "needs_highpass": True, "needs_deesser": True}
    base.update(overrides)
    return base


def test_zone_chain_orders_filters_correctly():
    """順序必須是 afftdn → highpass → deesser。"""
    chain = build_zone_chain(_zone_plan())
    positions = [chain.index(name) for name in ("afftdn", "highpass", "deesser")]
    assert positions == sorted(positions)


def test_zone_chain_has_no_volume_filter():
    """拉平已在樣本層完成，濾鏡鏈不得再動音量。

    若這裡出現 volume，代表增益被套用了兩次。
    """
    assert "volume" not in build_zone_chain(_zone_plan())


def test_zone_chain_omits_highpass_when_not_needed():
    """不需要時不得掛 highpass —— 無差別套用會削掉男聲低頻。"""
    chain = build_zone_chain(_zone_plan(needs_highpass=False))
    assert "highpass" not in chain


def test_zone_chain_omits_deesser_when_not_needed():
    """不需要時不得掛 deesser。"""
    chain = build_zone_chain(_zone_plan(needs_deesser=False))
    assert "deesser" not in chain


def test_zone_chain_uses_planned_denoise_strength():
    """降噪強度必須取自計畫值，不得寫死。"""
    assert "nr=24" in build_zone_chain(_zone_plan(denoise_db=24))


def test_denoise_strength_rounds_not_truncates():
    """使用者手改成 18.9 時應四捨五入為 19，不得截斷成 18。

    截斷的偏差方向永遠偏弱，且不會被任何驗證抓到。
    """
    assert "nr=19" in build_zone_chain(_zone_plan(denoise_db=18.9))


def test_zone_chain_uses_measured_noise_floor():
    """底噪基準須取自該區實測值，不得寫死。"""
    assert "nf=-52" in build_zone_chain(_zone_plan(noise_floor_db=-52.0))


def test_noise_floor_clamped_to_valid_range():
    """實測底噪超出 afftdn 合法範圍時須夾住，否則 ffmpeg 會直接報參數錯誤。"""
    assert "nf=-80" in build_zone_chain(_zone_plan(noise_floor_db=-120.0))
    assert "nf=-20" in build_zone_chain(_zone_plan(noise_floor_db=-5.0))


def test_highpass_preserves_male_fundamental():
    """高通截止不得高到削掉男聲基頻（約 85Hz 起）。"""
    chain = build_zone_chain(_zone_plan(needs_highpass=True))
    assert "highpass=f=60" in chain


def test_no_compressor_in_chain():
    """禁止出現壓縮器 —— 壓縮會頂高底噪，破壞降噪前提。"""
    chain = build_zone_chain(_zone_plan())
    assert "acompressor" not in chain
    assert "dynaudnorm" not in chain
    assert "speechnorm" not in chain


def test_loudnorm_measure_chain_requests_json():
    """第一段量測必須輸出 JSON 才能餵給第二段。"""
    chain = build_loudnorm_measure_chain(-16.0)
    assert "print_format=json" in chain
    assert "I=-16.0" in chain
    assert "TP=-1.5" in chain


def test_loudnorm_apply_chain_uses_measured_values():
    """第二段必須帶入第一段量到的值，且鍵名要對得上。

    斷言完整的鍵值對而非只檢查數值出現：若 measured_I 與 measured_TP
    的值被寫反，只檢查數值的斷言照樣會通過。
    """
    measured = {"input_i": "-23.1", "input_tp": "-5.2",
                "input_lra": "8.3", "input_thresh": "-33.4",
                "target_offset": "0.4"}
    chain = build_loudnorm_apply_chain(-16.0, measured)
    assert "measured_I=-23.1" in chain
    assert "measured_TP=-5.2" in chain
    assert "measured_LRA=8.3" in chain
    assert "measured_thresh=-33.4" in chain
    assert "offset=0.4" in chain
    assert "linear=true" in chain


def test_alimiter_limit_is_linear_not_db():
    """alimiter 的 limit 吃線性值，須由 dBTP 換算。

    -1.5 dBTP → 10^(-1.5/20) ≈ 0.8414。若誤把 -1.5 直接填進去，
    ffmpeg 不會報錯，但限幅門檻會完全失效。
    """
    chain = build_loudnorm_apply_chain(-16.0, {
        "input_i": "-23.1", "input_tp": "-5.2", "input_lra": "8.3",
        "input_thresh": "-33.4", "target_offset": "0.4"})
    assert "alimiter=limit=0.8414" in chain
