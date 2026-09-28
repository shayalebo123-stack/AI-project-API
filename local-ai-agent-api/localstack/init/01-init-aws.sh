#!/bin/bash
# LocalStack init hook: LocalStack runs every script mounted under
# /etc/localstack/init/ready.d once it is ready to accept requests.
# The script is idempotent - running it twice does no harm.
set -euo pipefail

export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
TABLE_NAME="ChatHistory"
QUEUE_NAME="agent-logs-queue"

echo "[init] Creating DynamoDB table '${TABLE_NAME}'..."
if awslocal dynamodb describe-table --table-name "${TABLE_NAME}" >/dev/null 2>&1; then
  echo "[init] Table '${TABLE_NAME}' already exists - skipping."
else
  awslocal dynamodb create-table \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions \
        AttributeName=sessionId,AttributeType=S \
        AttributeName=createdAt,AttributeType=S \
    --key-schema \
        AttributeName=sessionId,KeyType=HASH \
        AttributeName=createdAt,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST >/dev/null
  echo "[init] Table '${TABLE_NAME}' created."
fi

echo "[init] Creating SQS queue '${QUEUE_NAME}'..."
awslocal sqs create-queue --queue-name "${QUEUE_NAME}" >/dev/null   # idempotent
echo "[init] Queue '${QUEUE_NAME}' ready."

echo "[init] AWS resources are ready."
