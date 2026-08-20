"""chain 模組測試：濾鏡順序與條件掛載。"""
from ar.chain import (build_loudnorm_apply_chain, build_loudnorm_measure_chain,
                      build_zone_chain)


def _zone_plan(**overrides) -> dict:
    """建立測試用 zone 計畫。"""
    base = {"gain_db": 3.0, "denoise_db": 18, "needs_highpass": True,
            "needs_deesser": True}
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
    """第二段必須帶入第一段量到的四個 measured 值並開啟 linear。"""
    measured = {"input_i": "-23.1", "input_tp": "-5.2",
                "input_lra": "8.3", "input_thresh": "-33.4",
                "target_offset": "0.4"}
    chain = build_loudnorm_apply_chain(-16.0, measured)
    for value in ("-23.1", "-5.2", "8.3", "-33.4", "0.4"):
        assert value in chain
    assert "linear=true" in chain
    assert "alimiter" in chain
