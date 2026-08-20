"""實際執行 ffmpeg：分區渲染、串接、響度收斂、影片換揉、ASR WAV 匯出。"""
import json
import re
from pathlib import Path

from .chain import (LRA, build_loudnorm_apply_chain, build_loudnorm_measure_chain,
                    build_zone_chain)
from .ffmpeg_io import FFmpegError, run_ffmpeg
from .fingerprint import read_samples
from .gain import apply_gain_envelope, build_gain_envelope
from .probe import probe

# 用來從 ffmpeg loudnorm 量測階段的 stderr 中擷取 JSON 摘要區塊
_JSON_RE = re.compile(r"\{[^{}]*\"input_i\"[^{}]*\}", re.DOTALL)


def render_zones(input_path: Path, plan: dict, work_dir: Path) -> list[Path]:
    """逐 zone 拉平並套用濾鏡鏈，各自輸出成中繼 WAV。

    分兩步：先在樣本層用 numpy 包絡拉平（含交界斜坡與雜訊衰減），再交給
    ffmpeg 做降噪與 EQ。這個順序就是「先拉平、再優化」的實現。

    分區處理而非單一濾鏡鏈的原因：每個 zone 的降噪基準與濾鏡組合不同，
    ffmpeg 無法在單一濾鏡鏈中對不同時間段套用不同的 afftdn 參數。
    中繼一律用 32-bit float WAV，避免多次量化與拉平後的峰值被截頂。
    """
    work_dir.mkdir(parents=True, exist_ok=True)
    # 取樣率取自輸入媒體規格，確保整條鏈不做不必要的降規格
    sample_rate = probe(input_path).sample_rate
    parts: list[Path] = []
    for zone in plan["zones"]:
        # 只保留與此 zone 重疊的句級增益，並把時間平移到該段的相對時間軸
        local_gains = [
            (max(0.0, u["start"] - zone["start"]),
             min(zone["end"], u["end"]) - zone["start"],
             u["gain_db"])
            for u in plan["utterances"]
            if u["start"] < zone["end"] and u["end"] > zone["start"]
        ]
        # 落在此 zone 內的非語音雜訊，同樣平移到相對時間軸後額外壓低
        local_events = [
            (max(0.0, e["start"] - zone["start"]),
             min(zone["end"], e["end"]) - zone["start"])
            for e in plan.get("nonspeech_events", [])
            if e["start"] < zone["end"] and e["end"] > zone["start"]
        ]

        # 第一步：在樣本層拉平。用原始取樣率解碼，避免為了修音而降規格。
        samples = read_samples(input_path, zone["start"], zone["end"], sample_rate)
        envelope = build_gain_envelope(
            samples.size, sample_rate, local_gains, local_events
        )
        # 區級增益是整段常數，直接加到包絡上；句級增益已在包絡內
        leveled = apply_gain_envelope(samples, envelope + float(zone["gain_db"]))

        # 以 32-bit float raw 交棒給 ffmpeg：拉平後的峰值可能超過 1.0，
        # 若此時就寫 16-bit PCM 會被截頂，後面的 loudnorm 也救不回來。
        raw_path = work_dir / f"zone-{zone['index']:03d}.f32"
        # 明確指定 little-endian（"<f4"）而非依賴主機位元組序：讀取端寫死
        # 了 -f f32le，在 x86 上剛好相符是巧合，不該是隱含假設
        leveled.astype("<f4").tofile(raw_path)

        # 第二步：交給 ffmpeg 做降噪與 EQ
        out_path = work_dir / f"zone-{zone['index']:03d}.wav"
        run_ffmpeg([
            "-y", "-f", "f32le", "-ar", str(sample_rate), "-ac", "1",
            "-i", str(raw_path),
            "-af", build_zone_chain(zone),
            "-c:a", "pcm_f32le", str(out_path),
        ])
        parts.append(out_path)
    return parts


def concat_zones(parts: list[Path], out_path: Path) -> Path:
    """把各 zone 的中繼檔按序串接。"""
    # concat demuxer 需要的清單檔，逐行列出各段檔案路徑
    list_file = out_path.parent / "concat-list.txt"
    list_file.write_text(
        "\n".join(f"file '{part.as_posix()}'" for part in parts), encoding="utf-8"
    )
    run_ffmpeg([
        "-y", "-f", "concat", "-safe", "0", "-i", str(list_file),
        "-c", "copy", str(out_path),
    ])
    return out_path


def apply_loudnorm(input_path: Path, out_path: Path, target_lufs: float) -> Path:
    """兩段式 loudnorm：先量測再套用，最後以 alimiter 收尾。

    單段式 loudnorm 走的是動態壓縮路徑，會破壞前面辛苦拉平的動態關係；
    兩段式帶入 measured 值後可走 linear 模式，只做線性增益搬移。

    輸出必須明確指定取樣率：loudnorm 為了偵測 true peak 會內部過採樣到
    192kHz，且輸出會維持在 192kHz —— 若不鎖定，換揉回影片或交給下游時
    取樣率已經悄悄變了。
    """
    # 拉平/降噪階段用的取樣率，loudnorm 過採樣後要鎖回這個值
    source_rate = probe(input_path).sample_rate
    # 第一段：量測整段素材的響度統計，結果印在 stderr 的 JSON 摘要中
    measure_stderr = run_ffmpeg([
        "-i", str(input_path), "-af", build_loudnorm_measure_chain(target_lufs),
        "-f", "null", "-",
    ])
    match = _JSON_RE.search(measure_stderr)
    if not match:
        raise FFmpegError("loudnorm 量測失敗：找不到 JSON 輸出")
    measured = json.loads(match.group(0))
    # 第二段：帶入量測值套用線性增益，收斂到目標響度
    apply_stderr = run_ffmpeg([
        "-y", "-i", str(input_path),
        "-af", build_loudnorm_apply_chain(target_lufs, measured),
        "-ar", str(source_rate),  # 鎖回原取樣率，抵銷 loudnorm 的內部過採樣
        "-c:a", "pcm_s16le", str(out_path),
    ])
    _warn_if_dynamic_fallback(apply_stderr)
    return out_path


def _warn_if_dynamic_fallback(stderr: str) -> None:
    """檢查 loudnorm 是否從 linear 退回動態模式並提出警告。

    ffmpeg 的 loudnorm 指定 linear=true 後，若目標無法以單一線性增益達成
    （例如原始素材的 LRA 已超過目標），會**自動退回動態模式**且不報錯。
    動態模式會壓縮動態範圍，破壞前面辛苦拉平的句間關係 —— 這正是本技能
    最不想要的結果，卻是預設會靜默發生的行為。
    """
    # ffmpeg 實際輸出是 "Normalization Type:   Dynamic"（首字大寫、多個空白），
    # 用字面小寫比對會永遠不匹配 —— 這個專門偵測靜默失敗的警告若自己寫死了
    # 大小寫，它自己就會靜默失效，且沒有任何測試會發現。
    if re.search(r"Normalization\s+Type:\s*Dynamic", stderr, re.IGNORECASE):
        # 從 stderr 中順手撈出實測輸入 LRA，讓警告訊息能直接告訴使用者差多少
        measured_lra = re.search(r"Input LRA:\s*([\d.]+)", stderr)
        lra_note = f"（實測輸入 LRA {measured_lra.group(1)}，目標 {LRA}）" if measured_lra else ""
        print(
            f"警告：loudnorm 無法以線性增益達成目標，已自動退回動態模式{lra_note}。\n"
            "  動態模式會壓縮動態範圍，抵銷前面拉平的效果。\n"
            "  常見原因：素材原始動態範圍超過目標 LRA。\n"
            "  處置：確認拉平階段是否生效（檢查 verify.json 的句間標準差），"
            "或把 --target 調得更接近素材原始響度。"
        )


# 註：loudnorm 之後才降回 16-bit。此前一律保持 32-bit float，
# 因為拉平後、限幅前的訊號峰值可能超過 0 dBFS，提早量化會截頂。


MAX_DURATION_DRIFT = 0.1  # 音訊與影像時長容許誤差（秒）


def mux_video(video_path: Path, audio_path: Path, out_path: Path) -> Path:
    """把修復後的音軌換揉回原影片，影像軌直接複製不重編。

    換揉前先比對音訊與影像時長：`-shortest` 會在音軌較短時靜默截掉影像
    尾巴，不報錯也不警告。逐 zone 解碼與串接會累積次樣本級的裁切誤差，
    zone 多時（長課程可能數十個）可能累積到可察覺的程度，因此這裡明確
    擋下而非讓它悄悄發生。
    """
    video_duration = probe(video_path).duration
    audio_duration = probe(audio_path).duration
    drift = abs(video_duration - audio_duration)
    if drift > MAX_DURATION_DRIFT:
        raise FFmpegError(
            f"修復後音訊時長 {audio_duration:.3f}s 與影像 {video_duration:.3f}s "
            f"相差 {drift:.3f}s，超過容許的 {MAX_DURATION_DRIFT}s。\n"
            "換揉會靜默截掉較長的一軌，故在此停下。\n"
            "可能原因：分區串接累積裁切誤差，或 plan.json 的 zone 邊界未涵蓋整支檔案。"
        )
    run_ffmpeg([
        "-y", "-i", str(video_path), "-i", str(audio_path),
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
        "-shortest", str(out_path),
    ])
    return out_path


def export_asr_wav(input_path: Path, out_path: Path) -> Path:
    """匯出 16kHz 單聲道 WAV，供 faster-whisper 等 ASR 使用。"""
    run_ffmpeg([
        "-y", "-i", str(input_path), "-ac", "1", "-ar", "16000",
        "-c:a", "pcm_s16le", str(out_path),
    ])
    return out_path
