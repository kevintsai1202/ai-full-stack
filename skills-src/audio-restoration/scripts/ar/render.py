"""實際執行 ffmpeg：分區渲染、串接、響度收斂、影片換揉、ASR WAV 匯出。"""
from pathlib import Path

from .bulk import measure_overall
from .chain import build_linear_gain_chain, build_zone_chain
from .ffmpeg_io import FFmpegError, run_ffmpeg
from .fingerprint import read_samples
from .gain import apply_gain_envelope, build_gain_envelope
from .probe import probe


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
        # raw_path 只是傳給 ffmpeg 的中繼原始資料，wav 產出後即可丟棄；
        # 長課程逐 zone 累積下來每次都留著會佔用可觀磁碟空間（實測單一
        # zone 可達數十 MB，整支課程累積上百 MB）
        raw_path.unlink()
        parts.append(out_path)
    return parts


def concat_zones(parts: list[Path], out_path: Path) -> Path:
    """把各 zone 的中繼檔按序串接。"""
    # concat demuxer 需要的清單檔，逐行列出各段檔案路徑
    list_file = out_path.parent / "concat-list.txt"
    # 必須寫絕對路徑：concat demuxer 把相對路徑解析成「相對於清單檔所在
    # 目錄」，呼叫端若以相對路徑指定 work_dir，路徑會被重複拼接而開檔失敗
    list_file.write_text(
        "\n".join(f"file '{part.resolve().as_posix()}'" for part in parts), encoding="utf-8"
    )
    run_ffmpeg([
        "-y", "-f", "concat", "-safe", "0", "-i", str(list_file),
        "-c", "copy", str(out_path),
    ])
    return out_path


# 補償輪的觸發容差（dB）。取驗收容差 ±0.5 的一半，留餘裕給 AAC 重編偏移。
LOUDNESS_CONVERGE_TOLERANCE = 0.25
# 首輪 + 至多兩輪殘差補償。每輪限幅損耗遞減，實務上兩輪內必然收斂；
# 上限存在是為了防呆（量測異常時不無限迭代），不是預期會用滿。
MAX_NORMALIZE_PASSES = 3


def apply_loudnorm(input_path: Path, out_path: Path, target_lufs: float,
                   limit_db: float | None = None) -> Path:
    """最終響度正規化：量測後套純線性增益 + 限幅，殘差超容差時迭代補償。

    為什麼不用 loudnorm 套用：拉平後素材的 LRA 只剩約 2，而線性增益可能
    讓真峰值暫時超過 TP 目標，loudnorm 遇到這種情況會**靜默退回動態模式**，
    為了湊 LRA 目標把安靜段（含底噪）往上推 —— 真實素材實測底噪因此
    不降反升 11.6dB，抵銷了降噪成果。volume 純線性永無 fallback，
    峰值保護交給 alimiter（level=false）。

    為什麼要迭代：純線性增益在數學上精確平移 LUFS，但 alimiter 會把超過
    峰值目標的樣本壓掉，吃掉一部分增益能量 —— 高峰值素材（真峰值 -0.8、
    需 +6.3dB）實測單遍只收斂到 -16.6，誤差 0.6 超出驗收容差 ±0.5。
    每輪套完後重新量測，殘差超過容差就再補一輪；補償輪的增量小，
    限幅損耗逐輪遞減，漸近收斂。

    量測用 bulk.measure_overall（全檔單次 ebur128），與驗證階段同一條
    量測路徑；中繼輪保持 32-bit float 避免多次量化，最後才降 16-bit。

    limit_db（None＝修復路徑預設 -2.0）：alimiter 壓樣本峰值不管
    inter-sample peak，mastering 的高頻增強會放大 ISP，該路徑須傳
    更深的天花板（理由見 chain.build_linear_gain_chain）。
    """
    source_rate = probe(input_path).sample_rate
    # None 時沿用 chain 的預設天花板；顯式傳遞讓兩處預設值只定義在一處
    limit_kwargs = {} if limit_db is None else {"limit_db": limit_db}
    current = input_path
    intermediates: list[Path] = []  # 各輪的 f32 中繼檔，成功後一併清除
    for pass_index in range(MAX_NORMALIZE_PASSES):
        measured_lufs, _ = measure_overall(current)
        gain_db = target_lufs - measured_lufs
        # 首輪無條件執行（既要搬位準也要過 limiter 保證峰值）；
        # 之後只有殘差超容差才補償
        if pass_index > 0 and abs(gain_db) <= LOUDNESS_CONVERGE_TOLERANCE:
            break
        stage_path = out_path.parent / f"{out_path.stem}-pass{pass_index}.wav"
        run_ffmpeg([
            "-y", "-i", str(current),
            "-af", build_linear_gain_chain(gain_db, **limit_kwargs),
            "-ar", str(source_rate),
            "-c:a", "pcm_f32le", str(stage_path),
        ])
        intermediates.append(stage_path)
        current = stage_path
    # 最後一步才降 16-bit：純轉碼，不改響度與峰值
    run_ffmpeg([
        "-y", "-i", str(current),
        "-ar", str(source_rate),
        "-c:a", "pcm_s16le", str(out_path),
    ])
    for stage in intermediates:
        stage.unlink()
    return out_path


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
