"""chain 模組測試：濾鏡順序與條件掛載。"""
from ar.chain import build_linear_gain_chain, build_zone_chain


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


def test_linear_gain_chain_applies_exact_gain():
    """正規化輪是純 volume 增益，數值直接可讀、可驗證。

    響度量測已統一走 bulk.measure_overall（與驗證同一條路徑），
    loudnorm 量測鏈（build_loudnorm_measure_chain）已隨之移除。
    """
    chain = build_linear_gain_chain(4.2)
    assert "volume=4.20dB" in chain
    chain_negative = build_linear_gain_chain(-3.55)
    assert "volume=-3.55dB" in chain_negative


def test_linear_gain_chain_never_uses_loudnorm():
    """最終套用不得出現 loudnorm。

    loudnorm 在「線性增益會讓 TP 暫時超標」時會靜默退回動態模式，
    為了湊 LRA 目標把底噪抬高 —— 真實素材實測底噪因此不降反升 11.6dB。
    這個測試防止有人把 loudnorm 套用加回來。
    """
    chain = build_linear_gain_chain(4.2)
    assert "loudnorm" not in chain


def test_alimiter_limit_is_linear_not_db():
    """alimiter 的 limit 吃線性值，須由 dBTP 換算。

    處理目標 -2.0 dBTP → 10^(-2.0/20) ≈ 0.7943。若誤把 -2.0 直接填進去，
    ffmpeg 不會報錯，但限幅門檻會完全失效。

    注意處理目標（-2.0）比驗收標準（-1.5）低 0.5 dB，那是留給有損編碼的
    餘裕 —— 實測 AAC 192k 重編會讓真峰值上升約 0.1 dB。
    """
    chain = build_linear_gain_chain(4.2)
    assert "alimiter=limit=0.7943" in chain


def test_alimiter_disables_auto_level():
    """alimiter 必須關閉自動電平補償。

    ffmpeg 的 alimiter 預設 level=true，會在限幅後自動調整輸出電平，
    把 loudnorm 剛做完的正規化結果推高。實測不加 level=false 時輸出
    偏離目標 1.5-2.0 LUFS。我們用 alimiter 只為防止真峰值超標，
    不要它動響度。
    """
    chain = build_linear_gain_chain(4.2)
    assert "level=false" in chain
