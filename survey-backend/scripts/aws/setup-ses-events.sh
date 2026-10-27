#!/usr/bin/env bash
# 建立 SES 退信／檢舉／送達事件的 AWS 資源（東京區），供 ses-mail-spring-boot-starter 0.3.0 的 SQS 拉取使用。
#
# 用途：SES configuration set → SNS topic（ses-events）→ SQS 佇列（ses-events，含死信佇列）→ survey-backend 拉取。
# 執行環境：AWS CloudShell（已登入主控台的權限；本機 survey-backend-ses 金鑰沒有建立資源的權限）。
# 可重複執行：已存在的資源會沿用，政策與屬性會覆寫成本腳本的設定。
# 前提：SNS topic ses-events 已建立且存取政策允許本帳號的 SES 發佈（切換手冊第 10 節）。
#
# 用法：在 CloudShell 執行 `bash setup-ses-events.sh`
set -euo pipefail

REGION=ap-northeast-1
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
TOPIC_ARN="arn:aws:sns:${REGION}:${ACCOUNT}:ses-events"
QUEUE_NAME=ses-events
DLQ_NAME=ses-events-dlq
CONFIG_SET=newsletter
IAM_USER=survey-backend-ses
IAM_POLICY=ses-send-only

echo "== 1. 死信佇列（保留 14 天）"
DLQ_URL=$(aws sqs create-queue --region "$REGION" --queue-name "$DLQ_NAME" \
  --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
DLQ_ARN=$(aws sqs get-queue-attributes --region "$REGION" --queue-url "$DLQ_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
echo "$DLQ_ARN"

echo "== 2. 事件佇列（可見逾時 60 秒，重試 5 次後轉死信佇列）"
QUEUE_URL=$(aws sqs create-queue --region "$REGION" --queue-name "$QUEUE_NAME" --query QueueUrl --output text)
QUEUE_ARN=$(aws sqs get-queue-attributes --region "$REGION" --queue-url "$QUEUE_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
# 佇列政策：只允許 ses-events topic 發送進來
POLICY=$(cat <<JSON
{"Version":"2012-10-17","Statement":[{"Sid":"AllowSesEventsTopic","Effect":"Allow",
"Principal":{"Service":"sns.amazonaws.com"},"Action":"SQS:SendMessage","Resource":"${QUEUE_ARN}",
"Condition":{"ArnEquals":{"aws:SourceArn":"${TOPIC_ARN}"}}}]}
JSON
)
REDRIVE="{\"deadLetterTargetArn\":\"${DLQ_ARN}\",\"maxReceiveCount\":\"5\"}"
ATTRS=$(python3 -c 'import json,sys; print(json.dumps({"VisibilityTimeout":"60","Policy":sys.argv[1],"RedrivePolicy":sys.argv[2]}))' "$POLICY" "$REDRIVE")
aws sqs set-queue-attributes --region "$REGION" --queue-url "$QUEUE_URL" --attributes "$ATTRS"
echo "$QUEUE_URL"

echo "== 3. 佇列訂閱 topic（不開 raw message delivery，保留 SNS 簽章）"
aws sns subscribe --region "$REGION" --topic-arn "$TOPIC_ARN" --protocol sqs \
  --notification-endpoint "$QUEUE_ARN" --query SubscriptionArn --output text

echo "== 4. SES configuration set 與事件目的地（Bounce、Complaint、Delivery）"
aws sesv2 create-configuration-set --region "$REGION" --configuration-set-name "$CONFIG_SET" 2>/dev/null \
  || echo "configuration set ${CONFIG_SET} 已存在，沿用"
DEST='{"Enabled":true,"MatchingEventTypes":["BOUNCE","COMPLAINT","DELIVERY"],"SnsDestination":{"TopicArn":"'"${TOPIC_ARN}"'"}}'
aws sesv2 create-configuration-set-event-destination --region "$REGION" --configuration-set-name "$CONFIG_SET" \
  --event-destination-name ses-events --event-destination "$DEST" 2>/dev/null \
  || aws sesv2 update-configuration-set-event-destination --region "$REGION" --configuration-set-name "$CONFIG_SET" \
       --event-destination-name ses-events --event-destination "$DEST"

echo "== 5. IAM：寄信金鑰加上讀取此佇列的權限"
IAM_DOC=$(cat <<JSON
{"Version":"2012-10-17","Statement":[
{"Effect":"Allow","Action":["ses:SendEmail","ses:GetAccount"],"Resource":"*"},
{"Effect":"Allow","Action":["sqs:ReceiveMessage","sqs:DeleteMessage","sqs:ChangeMessageVisibility"],"Resource":"${QUEUE_ARN}"}]}
JSON
)
aws iam put-user-policy --user-name "$IAM_USER" --policy-name "$IAM_POLICY" --policy-document "$IAM_DOC"

echo "== 完成。Zeabur 環境變數："
echo "SESMAIL_SQS_ENABLED=true"
echo "SESMAIL_SQS_QUEUE_URL=${QUEUE_URL}"
echo "SESMAIL_SNS_TOPIC_ARNS=${TOPIC_ARN}"
echo "AWS_SES_CONFIGURATION_SET=${CONFIG_SET}"
