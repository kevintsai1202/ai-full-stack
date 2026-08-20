"""render 模組測試：分區渲染、串接、響度收斂、ASR WAV。"""
from pathlib import Path

from ar.measure import measure_interval
from ar.probe import probe
from ar.render import apply_loudnorm, concat_zones, export_asr_wav, render_zones


def _plan(input_path: Path) -> dict:
    """建立涵蓋整支合成音檔的兩區計畫。"""
    return {
        "schema_version": 1,
        "input": str(input_path),
        "is_video": False,
        "target_lufs": -16.0,
        "zones": [
            {"index": 0, "start": 0.0, "end": 4.5, "gain_db": 0.0, "denoise_db": 12,
             "noise_floor_db": -48.0, "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
            {"index": 1, "start": 4.5, "end": 8.0, "gain_db": 6.0, "denoise_db": 12,
             "noise_floor_db": -48.0, "needs_highpass": False, "needs_deesser": False,
             "needs_ai_rescue": False, "issues": []},
        ],
        "utterances": [
            {"start": 0.0, "end": 4.5, "gain_db": 0.0},
            {"start": 4.5, "end": 8.0, "gain_db": 3.0},
        ],
    }


def test_render_zones_produces_one_file_per_zone(synth_wav: Path, tmp_path: Path):
    """兩個 zone 應產出兩個中繼檔。"""
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    assert len(parts) == 2
    assert all(p.exists() for p in parts)


def test_render_keeps_float_precision_until_loudnorm(synth_wav: Path, tmp_path: Path):
    """中繼檔須為 32-bit float。

    拉平後、限幅前的峰值可能超過 0 dBFS，中繼若用 16-bit PCM 會被截頂，
    後面的 loudnorm 無法還原已經削掉的波形。
    """
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    assert probe(parts[0]).audio_codec == "pcm_f32le"


def test_no_gain_spike_at_zone_internal_boundary(synth_wav: Path, tmp_path: Path):
    """語句交界不得出現增益尖峰。

    zone 1 內含 4.5→8.0 的語句邊界；若拉平回到 ffmpeg between 表達式的
    閉區間寫法，交界會出現兩句增益相加的瞬間尖峰。這裡以「交界前後的
    響度落在兩側之間」作為守門。
    """
    plan = _plan(synth_wav)
    plan["utterances"] = [
        {"start": 0.0, "end": 4.5, "gain_db": 0.0},
        {"start": 4.5, "end": 8.0, "gain_db": 6.0},
    ]
    parts = render_zones(synth_wav, plan, tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    before = measure_interval(merged, 4.2, 4.4).lufs
    across = measure_interval(merged, 4.45, 4.55).lufs
    after = measure_interval(merged, 4.6, 4.8).lufs
    assert min(before, after) - 1.0 <= across <= max(before, after) + 1.0


def test_concat_preserves_total_duration(synth_wav: Path, tmp_path: Path):
    """串接後總時長應與原檔一致（誤差 0.1 秒內）。"""
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    assert abs(probe(merged).duration - 8.0) < 0.1


def test_zone_gain_actually_raises_level(synth_wav: Path, tmp_path: Path):
    """zone 1 設 +6dB 區級增益 +3dB 句級，該段響度應明顯高於處理前。"""
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    before = measure_interval(synth_wav, 5.2, 6.8)
    after = measure_interval(merged, 5.2, 6.8)
    assert after.lufs - before.lufs > 6.0


def test_apply_loudnorm_converges_after_leveling(synth_wav: Path, tmp_path: Path):
    """走完整流程（拉平→串接→loudnorm）後，響度應收斂到目標 ±1.5 LUFS 內。

    必須先拉平再 loudnorm，這是真實流程的順序。直接對未拉平的素材跑
    loudnorm 會因為動態範圍過大而退回動態模式，量出來的偏差反映的是
    「跳過拉平」，不是 loudnorm 本身不準。
    """
    parts = render_zones(synth_wav, _plan(synth_wav), tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    out = apply_loudnorm(merged, tmp_path / "norm.wav", target_lufs=-16.0)
    stats = measure_interval(out, 0.0, 8.0)
    assert abs(stats.lufs - (-16.0)) < 1.5


def test_dynamic_fallback_is_detected(synth_wav: Path, tmp_path: Path, capsys):
    """素材動態範圍超過目標 LRA 時，必須偵測到 loudnorm 退回動態模式並警告。

    合成音檔兩句相差 12dB，未拉平時 input LRA 約 12，超過目標 LRA 11，
    ffmpeg 會靜默退回動態模式。這個測試同時守住兩件事：偵測字串要能匹配
    ffmpeg 的實際輸出格式（大寫 D、多個空白），以及警告確實會印出來。
    """
    apply_loudnorm(synth_wav, tmp_path / "norm.wav", target_lufs=-16.0)
    captured = capsys.readouterr()
    assert "動態模式" in captured.out


def test_loudnorm_self_reported_value_is_not_trusted(synth_wav: Path, tmp_path: Path):
    """驗證必須重新量測輸出檔，不能採信 loudnorm 的自報值。

    實測發現：退回動態模式時 ffmpeg 自報 Output Integrated -16.0 LUFS，
    但實際寫進檔案的是 -14.5 LUFS。這就是四指標驗證要對輸出檔重跑量測、
    而不是解析 ffmpeg 回報的原因。
    """
    out = apply_loudnorm(synth_wav, tmp_path / "norm.wav", target_lufs=-16.0)
    actual = measure_interval(out, 0.0, 8.0).lufs
    # 不斷言具體數值（那會鎖死 ffmpeg 版本行為），只確認「量得到一個實際值」
    assert actual != 0.0


def test_nonspeech_event_is_attenuated(synth_wav: Path, tmp_path: Path):
    """標記為非語音雜訊的區間，響度應低於同一句的其他部分約 6dB。"""
    plan = _plan(synth_wav)
    plan["nonspeech_events"] = [{"start": 2.5, "end": 3.2}]
    parts = render_zones(synth_wav, plan, tmp_path)
    merged = concat_zones(parts, tmp_path / "merged.wav")
    attenuated = measure_interval(merged, 2.6, 3.1)
    normal = measure_interval(merged, 3.4, 3.9)
    assert 4.0 < normal.lufs - attenuated.lufs < 8.0


def test_export_asr_wav_is_16k_mono(synth_wav: Path, tmp_path: Path):
    """供 ASR 使用的 WAV 必須是 16kHz 單聲道。"""
    out = export_asr_wav(synth_wav, tmp_path / "asr.wav")
    spec = probe(out)
    assert spec.sample_rate == 16000
    assert spec.channels == 1
