#!/usr/bin/env sh
set -eu

BASE_URL=${BASE_URL:-http://127.0.0.1:8080/cnaps-web}

echo "Running WebFE smoke tests against $BASE_URL"
curl -fsS "$BASE_URL/api/health"
curl -fsS "$BASE_URL/api/cnaps/vouchers?status=10_PENDING_REVIEW" \
  -H "requestId: REQ-SMOKE-QRY" \
  -H "operatorNo: 77210021" \
  -H "branchNo: 772" \
  -H "workDate: 2026-07-08"
