"""增益計算：區級補償、句級補償、限幅、平滑、逐樣本包絡生成與套用。

只使用純增益，不使用壓縮器。壓縮會連帶把底噪頂高，破壞後續降噪賴以判斷的
訊噪比前提；純增益只搬動位準，噪音與人聲的比例不變。
"""
import numpy as np

from .diagnose import ZoneDiagnosis
from .fingerprint import Zone
from .segments import Utterance

MAX_GAIN_DB = 6.0   # 單句增益上限，避免把咳嗽、翻頁聲拉到人聲音量
MAX_STEP_DB = 3.0   # 相鄰句增益差上限，避免可聽的音量跳動
RAMP_MS = 200       # 語句增益交界的線性斜坡長度（毫秒）
EVENT_RAMP_MS = 50  # 雜訊衰減進出的斜坡長度（毫秒），比語句短以免衰減被稀釋
NONSPEECH_ATTENUATION_DB = -6.0  # 非語音雜訊區間的額外衰減


def compute_zone_gains(diagnoses: list[ZoneDiagnosis],
                       working_lufs: float = -20.0) -> list[float]:
    """算出每個 zone 補到共同工作位準所需的純增益。"""
    return [working_lufs - d.speech_rms_db for d in diagnoses]


def _zone_index_for(time: float, zones: list[Zone]) -> int:
    """判斷某時間點落在哪個 zone。"""
    for zone in zones:
        if zone.start <= time < zone.end:
            return zone.index
    return zones[-1].index if zones else 0


def compute_utterance_gains(utterances: list[Utterance], utterance_rms: list[float],
                            zones: list[Zone], zone_gains: list[float],
                            working_lufs: float = -20.0,
                            max_gain_db: float = MAX_GAIN_DB,
                            max_step_db: float = MAX_STEP_DB
                            ) -> list[tuple[float, float, float]]:
    """算出逐句增益，並套用上限與相鄰差限幅。

    utterance_rms 為每句的 RMS 位準（dBFS）。句級用 RMS 而非 LUFS：
    句級增益是相對補償，只在乎句與句之間的相對位準，RMS 與 LUFS 在此
    等價；且區級（compute_zone_gains）本來就以 RMS 為基準，句級同用 RMS
    才讓整個位準體系一致——真正的感知響度只在最終 loudnorm 階段處理。

    回傳每項為 (start, end, gain_db)，此 gain 為**句級增益**，
    實際套用時會與該句所屬 zone 的區級增益相加。
    """
    raw: list[float] = []
    for utterance, rms in zip(utterances, utterance_rms):
        zone_index = _zone_index_for((utterance.start + utterance.end) / 2.0, zones)
        applied = zone_gains[zone_index] if zone_index < len(zone_gains) else 0.0
        # 區級增益已補償一部分，句級只需補剩下的差額
        residual = working_lufs - (rms + applied)
        raw.append(max(-max_gain_db, min(max_gain_db, residual)))

    smoothed = _limit_steps(raw, max_step_db)
    return [(u.start, u.end, g) for u, g in zip(utterances, smoothed)]


def _limit_steps(gains: list[float], max_step_db: float) -> list[float]:
    """限制相鄰句的增益差。

    正反各掃一次，確保任一方向的跳變都被壓平（單向掃描會在下坡段失效）。
    """
    if not gains:
        return []
    forward = list(gains)
    for index in range(1, len(forward)):
        delta = forward[index] - forward[index - 1]
        if abs(delta) > max_step_db:
            forward[index] = forward[index - 1] + max_step_db * (1 if delta > 0 else -1)
    for index in range(len(forward) - 2, -1, -1):
        delta = forward[index] - forward[index + 1]
        if abs(delta) > max_step_db:
            forward[index] = forward[index + 1] + max_step_db * (1 if delta > 0 else -1)
    return forward


def _box_smooth(envelope: np.ndarray, window_samples: int) -> np.ndarray:
    """對包絡做移動平均，把階梯轉成線性斜坡。

    box filter 作用在階梯上的結果恰好是長度等於窗長、以原邊界為中心的線性
    斜坡，正是我們要的交叉淡化。兩端以邊界值填補，避免頭尾被拉向 0。
    """
    if window_samples < 2:
        return envelope
    kernel = np.ones(window_samples) / window_samples
    left_pad = window_samples // 2
    right_pad = window_samples - 1 - left_pad
    padded = np.concatenate([
        np.full(left_pad, envelope[0]),
        envelope,
        np.full(right_pad, envelope[-1]),
    ])
    return np.convolve(padded, kernel, mode="valid")


def build_gain_envelope(total_samples: int, sample_rate: int,
                        utterance_gains: list[tuple[float, float, float]],
                        nonspeech_events: list[tuple[float, float]] | None = None,
                        attenuation_db: float = NONSPEECH_ATTENUATION_DB,
                        ramp_ms: int = RAMP_MS,
                        event_ramp_ms: int = EVENT_RAMP_MS) -> np.ndarray:
    """產生逐樣本的 dB 增益包絡。

    先鋪成階梯（每句一段常數增益），再以 box filter 把每個交界轉成長度
    ramp_ms 的線性斜坡。非語音雜訊另建一條衰減階梯、以較短的 event_ramp_ms
    平滑後相加 —— 雜訊事件常只有一兩百毫秒，用語句的 200ms 斜坡會把衰減
    稀釋掉。

    為何不用 ffmpeg 的 volume 表達式：`between(t,a,b)` 是閉區間，而語句邊界
    外擴到靜音中點後必然相接，交界那一點兩個條件同時成立會讓增益相加，每個
    交界都產生尖峰；且表達式長度隨語句數線性成長，50 分鐘課程就會逼近
    Windows 命令列 32KB 上限。改用逐樣本包絡兩者皆解，且斜坡精確可控。
    """
    envelope = np.zeros(total_samples, dtype=np.float64)
    for start, end, gain in utterance_gains:
        begin = max(0, int(start * sample_rate))
        finish = min(total_samples, int(end * sample_rate))
        if finish > begin:
            envelope[begin:finish] = gain
    envelope = _box_smooth(envelope, int(ramp_ms / 1000.0 * sample_rate))

    events = nonspeech_events or []
    if events:
        attenuation = np.zeros(total_samples, dtype=np.float64)
        for start, end in events:
            begin = max(0, int(start * sample_rate))
            finish = min(total_samples, int(end * sample_rate))
            if finish > begin:
                attenuation[begin:finish] = attenuation_db
        envelope = envelope + _box_smooth(
            attenuation, int(event_ramp_ms / 1000.0 * sample_rate)
        )
    return envelope


def apply_gain_envelope(samples: np.ndarray, envelope_db: np.ndarray) -> np.ndarray:
    """把 dB 包絡套用到樣本上，回傳 float32 陣列。

    長度不符時報錯而非截斷：截斷會讓增益與音訊錯位，而錯位的結果聽起來
    仍然「像是一段正常的音訊」，是最難被發現的失敗模式。
    """
    if samples.size != envelope_db.size:
        raise ValueError(
            f"樣本數 {samples.size} 與包絡長度 {envelope_db.size} 不符，"
            "無法套用增益（長度不符代表上游切窗有誤，不可截斷處理）"
        )
    return (samples.astype(np.float64) * (10.0 ** (envelope_db / 20.0))).astype(np.float32)
