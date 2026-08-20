"""master 模組測試：preset 查表與濾鏡鏈內容。"""
import pytest

from ar.master import MASTER_PRESETS, build_master_chain


def test_presets_contain_three_calibrated_options():
    """三組 preset 必須齊備：conservative／podcast／rich。

    這三組是 2026-08-21 以真實素材（02.mp4）AB 試聽校準的結果，
    鍵名是 CLI --preset 的合法值來源，不可隨意增刪。
    """
    assert set(MASTER_PRESETS.keys()) == {"conservative", "podcast", "rich"}


def test_build_master_chain_returns_preset_chain():
    """查表回傳的鏈必須與 preset 定義完全一致，不得中途加工。"""
    for name, chain in MASTER_PRESETS.items():
        assert build_master_chain(name) == chain


def test_unknown_preset_raises_with_available_options():
    """未知 preset 必須拋 ValueError，且訊息列出全部可用選項。

    使用者打錯字時要能直接從錯誤訊息照抄正確值，不必翻文件。
    """
    with pytest.raises(ValueError) as excinfo:
        build_master_chain("loud")
    message = str(excinfo.value)
    for name in ("conservative", "podcast", "rich"):
        assert name in message


def test_podcast_chain_has_compressor_and_exciter():
    """podcast（預設）鏈必須含 acompressor 與 aexciter。

    壓縮提供能量密度（貼耳感）、aexciter 提供高頻諧波光澤，
    兩者正是 podcast 質感與 conservative 的差異所在。
    """
    chain = build_master_chain("podcast")
    assert "acompressor" in chain
    assert "aexciter" in chain


def test_conservative_chain_has_no_exciter():
    """conservative 鏈刻意不掛 aexciter —— 保守檔只做 EQ 與輕度壓縮。"""
    assert "aexciter" not in build_master_chain("conservative")
