"""
驗證 live-slides/exam/*.json 是否符合 Exam 系統（exam_system_new）匯入格式。

對照來源：exam-system-backend ExamExportDTO / Question / QuestionOption 的驗證註解與欄位長度
  - title 1–100 字、description ≤ 500 字
  - questionTimeLimit 10–300 秒
  - questionText ≤ 500 字、optionText ≤ 200 字（皆不可空白）
  - questionOrder / optionOrder 從 1 起連號
  - correctOptionOrder 必須指向存在的選項
  - singleStatChartType / cumulativeChartType 只能是 BAR 或 PIE

用法（專案根目錄）：
  python live-slides/scripts/verify_exam_import.py            # 驗證 exam/ 下全部 json
  python live-slides/scripts/verify_exam_import.py 檔案.json  # 驗證指定檔案
"""
import json
import sys
from pathlib import Path

# Exam 系統允許的圖表類型
CHART_TYPES = {"BAR", "PIE"}


def verify(path: Path) -> list[str]:
    """驗證單一 JSON 檔，回傳錯誤訊息清單（空清單代表通過）。"""
    errors: list[str] = []
    data = json.loads(path.read_text(encoding="utf-8"))

    title = data.get("title", "")
    if not (1 <= len(title) <= 100):
        errors.append(f"title 長度需在 1–100，目前 {len(title)}")
    if len(data.get("description") or "") > 500:
        errors.append("description 超過 500 字")
    limit = data.get("questionTimeLimit")
    if not isinstance(limit, int) or not (10 <= limit <= 300):
        errors.append(f"questionTimeLimit 需為 10–300 的整數，目前 {limit!r}")

    questions = data.get("questions") or []
    if not questions:
        errors.append("questions 不可為空")

    for idx, q in enumerate(questions, start=1):
        tag = f"Q#{idx}"
        if q.get("questionOrder") != idx:
            errors.append(f"{tag} questionOrder 應為 {idx}，目前 {q.get('questionOrder')!r}")
        text = q.get("questionText") or ""
        if not text.strip():
            errors.append(f"{tag} questionText 不可空白")
        if len(text) > 500:
            errors.append(f"{tag} questionText 超過 500 字（{len(text)}）")
        for key in ("singleStatChartType", "cumulativeChartType"):
            if q.get(key) not in CHART_TYPES:
                errors.append(f"{tag} {key} 需為 BAR/PIE，目前 {q.get(key)!r}")

        options = q.get("options") or []
        if not options:
            errors.append(f"{tag} options 不可為空")
        for oidx, opt in enumerate(options, start=1):
            if opt.get("optionOrder") != oidx:
                errors.append(f"{tag} 選項 {oidx} optionOrder 應為 {oidx}，目前 {opt.get('optionOrder')!r}")
            otext = opt.get("optionText") or ""
            if not otext.strip():
                errors.append(f"{tag} 選項 {oidx} optionText 不可空白")
            if len(otext) > 200:
                errors.append(f"{tag} 選項 {oidx} optionText 超過 200 字（{len(otext)}）")

        correct = q.get("correctOptionOrder")
        if not isinstance(correct, int) or not (1 <= correct <= len(options)):
            errors.append(f"{tag} correctOptionOrder 需在 1–{len(options)}，目前 {correct!r}")

    return errors


def main() -> int:
    """依參數或預設目錄逐檔驗證，任一檔失敗即回傳非零。"""
    exam_dir = Path(__file__).resolve().parent.parent / "exam"
    targets = [Path(p) for p in sys.argv[1:]] or sorted(exam_dir.glob("*.json"))
    if not targets:
        print(f"找不到任何 JSON：{exam_dir}")
        return 1

    failed = False
    for path in targets:
        errs = verify(path)
        count = len(json.loads(path.read_text(encoding="utf-8")).get("questions") or [])
        if errs:
            failed = True
            print(f"[FAIL] {path.name}（{count} 題）")
            for e in errs:
                print(f"   - {e}")
        else:
            print(f"[OK]   {path.name}（{count} 題）")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
