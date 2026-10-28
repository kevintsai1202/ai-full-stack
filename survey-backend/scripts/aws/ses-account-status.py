"""查詢 Amazon SES 帳號狀態（是否已離開沙箱、24 小時額度、寄送是否啟用）。

用途：不用登入 AWS 主控台、不用裝 AWS CLI 或 boto3，就能確認生產存取權申請的結果。
只用 Python 標準庫，自己做 SigV4 簽章呼叫 SES v2 GetAccount（唯讀；只需要 ses:GetAccount 權限）。
金鑰從專案根目錄的 .env 讀取（AWS_SES_ACCESS_KEY_ID、AWS_SES_SECRET_ACCESS_KEY），不會印出。

用法（PowerShell，於專案根目錄）：
    python survey-backend/scripts/aws/ses-account-status.py
"""
import datetime
import hashlib
import hmac
import json
import pathlib
import sys
import urllib.request

REGION = "ap-northeast-1"
HOST = f"email.{REGION}.amazonaws.com"
PATH = "/v2/email/account"


def load_env(path: pathlib.Path) -> dict:
    """讀取 .env 的 KEY=VALUE（忽略註解與空行，去掉引號）。"""
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def sign(key: bytes, msg: str) -> bytes:
    """HMAC-SHA256。"""
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def get_account(access_key: str, secret_key: str) -> dict:
    """以 SigV4 簽章呼叫 SES v2 GetAccount，回傳 JSON。"""
    now = datetime.datetime.now(datetime.timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(b"").hexdigest()
    canonical_headers = f"host:{HOST}\nx-amz-date:{amz_date}\n"
    signed_headers = "host;x-amz-date"
    canonical_request = "\n".join(["GET", PATH, "", canonical_headers, signed_headers, payload_hash])
    scope = f"{date_stamp}/{REGION}/ses/aws4_request"
    string_to_sign = "\n".join(["AWS4-HMAC-SHA256", amz_date, scope,
                                hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()])
    k_signing = sign(sign(sign(sign(("AWS4" + secret_key).encode("utf-8"), date_stamp), REGION), "ses"),
                     "aws4_request")
    signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    request = urllib.request.Request(f"https://{HOST}{PATH}", headers={
        "x-amz-date": amz_date,
        "Authorization": f"AWS4-HMAC-SHA256 Credential={access_key}/{scope}, "
                         f"SignedHeaders={signed_headers}, Signature={signature}",
    })
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read())


def main() -> int:
    """讀金鑰、查詢並印出重點欄位。"""
    root = pathlib.Path(__file__).resolve().parents[3]
    env = load_env(root / ".env")
    access_key = env.get("AWS_SES_ACCESS_KEY_ID")
    secret_key = env.get("AWS_SES_SECRET_ACCESS_KEY")
    if not access_key or not secret_key:
        print(".env 缺少 AWS_SES_ACCESS_KEY_ID 或 AWS_SES_SECRET_ACCESS_KEY", file=sys.stderr)
        return 1
    account = get_account(access_key, secret_key)
    quota = account.get("SendQuota", {})
    details = account.get("Details", {})
    print(f"生產存取權（已離開沙箱）：{account.get('ProductionAccessEnabled')}")
    print(f"寄送啟用：{account.get('SendingEnabled')}　帳號狀態：{account.get('EnforcementStatus')}")
    print(f"24 小時上限：{quota.get('Max24HourSend')}　已寄：{quota.get('SentLast24Hours')}"
          f"　每秒上限：{quota.get('MaxSendRate')}")
    review = details.get("ReviewDetails", {})
    if review:
        print(f"審核狀態：{review.get('Status')}　案件：{review.get('CaseId')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
