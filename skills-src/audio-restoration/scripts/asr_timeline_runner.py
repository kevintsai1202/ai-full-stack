"""在具備 faster-whisper 的 Python 環境中產生詞級時間軸。

輸出格式與專案既有 .tmp/asr_transcribe.py 一致，可互相沿用。
"""
import argparse
import json
from pathlib import Path

from faster_whisper import WhisperModel


def transcribe(audio_path: Path, out_dir: Path, model_size: str) -> None:
    """轉錄音訊並輸出 timeline.json（僅時間軸，不產字幕檔）。"""
    out_dir.mkdir(parents=True, exist_ok=True)
    model = WhisperModel(model_size, device="cpu", compute_type="int8")
    raw_segments, info = model.transcribe(
        str(audio_path), language="zh", beam_size=5,
        vad_filter=True, word_timestamps=True,
    )
    segments = [
        {
            "id": segment.id,
            "start": segment.start,
            "end": segment.end,
            "text": segment.text.strip(),
            "no_speech_prob": segment.no_speech_prob,
            "words": [
                {"start": w.start, "end": w.end, "word": w.word, "probability": w.probability}
                for w in (segment.words or [])
            ],
        }
        for segment in raw_segments
    ]
    payload = {
        "info": {
            "language": info.language,
            "language_probability": info.language_probability,
            "duration": info.duration,
            "duration_after_vad": info.duration_after_vad,
            "model": model_size,
        },
        "segments": segments,
    }
    (out_dir / "timeline.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def main() -> None:
    """解析參數並啟動轉錄。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--audio", required=True, type=Path)
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--model", default="small")
    args = parser.parse_args()
    transcribe(args.audio, args.out_dir, args.model)


if __name__ == "__main__":
    main()
