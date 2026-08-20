"""增益計算：區級補償、句級補償、限幅、平滑、ffmpeg 表達式生成。

只使用純增益，不使用壓縮器。壓縮會連帶把底噪頂高，破壞後續降噪賴以判斷的
訊噪比前提；純增益只搬動位準，噪音與人聲的比例不變。
"""
from .diagnose import ZoneDiagnosis
from .fingerprint import Zone
from .measure import LoudnessStats
from .segments import Utterance

MAX_GAIN_DB = 6.0   # 單句增益上限，避免把咳嗽、翻頁聲拉到人聲音量
MAX_STEP_DB = 3.0   # 相鄰句增益差上限，避免可聽的音量跳動
RAMP_MS = 200       # 增益交界的線性斜坡長度（毫秒）
NONSPEECH_ATTENUATION_DB = -6.0  # 非語音雜訊區間的額外衰減


def compute_zone_gains(diagnoses: list[ZoneDiagnosis],
                       working_lufs: float = -20.0) -> list[float]:
    """算出每個 zone 補到共同工作位準所需的純增益。"""
    return [working_lufs - d.speech_lufs for d in diagnoses]


def _zone_index_for(time: float, zones: list[Zone]) -> int:
    """判斷某時間點落在哪個 zone。"""
    for zone in zones:
        if zone.start <= time < zone.end:
            return zone.index
    return zones[-1].index if zones else 0


def compute_utterance_gains(utterances: list[Utterance], stats: list[LoudnessStats],
                            zones: list[Zone], zone_gains: list[float],
                            working_lufs: float = -20.0,
                            max_gain_db: float = MAX_GAIN_DB,
                            max_step_db: float = MAX_STEP_DB
                            ) -> list[tuple[float, float, float]]:
    """算出逐句增益，並套用上限與相鄰差限幅。

    回傳每項為 (start, end, gain_db)，此 gain 為**句級增益**，
    實際套用時會與該句所屬 zone 的區級增益相加。
    """
    raw: list[float] = []
    for utterance, stat in zip(utterances, stats):
        zone_index = _zone_index_for((utterance.start + utterance.end) / 2.0, zones)
        applied = zone_gains[zone_index] if zone_index < len(zone_gains) else 0.0
        # 區級增益已補償一部分，句級只需補剩下的差額
        residual = working_lufs - (stat.lufs + applied)
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


def build_volume_expression(utterance_gains: list[tuple[float, float, float]],
                            nonspeech_events: list[tuple[float, float]] | None = None,
                            attenuation_db: float = NONSPEECH_ATTENUATION_DB,
                            ramp_ms: int = RAMP_MS) -> str:
    """把逐句增益組成 ffmpeg volume 濾鏡的時間條件表達式。

    ffmpeg 的 volume 濾鏡以 dB 為單位需搭配 eval=frame。每句一個 between 條件，
    未涵蓋的時間點增益為 0dB（原樣通過）。ramp_ms 目前用於文件記錄；實際
    平滑已由 _limit_steps 在增益值層面完成，且切換點落在靜音中點，不需要
    額外的時間域斜坡。

    非語音雜訊區間額外疊加負增益：between 條件項相加，故該區間的實際增益
    等於「所在語句的增益 + attenuation_db」，達成相對於人聲被壓低的效果。
    """
    if not utterance_gains:
        return "0"
    terms = [
        f"between(t,{start:.3f},{end:.3f})*{gain:.3f}"
        for start, end, gain in utterance_gains
    ]
    for start, end in (nonspeech_events or []):
        terms.append(f"between(t,{start:.3f},{end:.3f})*{attenuation_db:.3f}")
    return "+".join(terms)
