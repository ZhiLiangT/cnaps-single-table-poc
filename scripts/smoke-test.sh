#!/usr/bin/env sh
set -eu

BASE_URL=${BASE_URL:-http://127.0.0.1:8080/ruisui-bank-sim}
WORK_DATE=${WORK_DATE:-$(date +%F)}

echo "Running WebFE smoke tests against $BASE_URL"
curl -fsS "$BASE_URL/api/health"

create_response=$(curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d "{
    \"workDate\":\"$WORK_DATE\",
    \"businessType\":\"02102\",
    \"accountPart1\":\"404045\",
    \"accountPart2\":\"00772\",
    \"accountPart3\":\"000000000001\",
    \"payeeAccountNo\":\"622200000000000001\",
    \"payeeName\":\"收款人名称\",
    \"priority\":\"NORM\",
    \"systemType\":\"CNAPS\",
    \"amount\":\"5600.00\",
    \"remark\":\"无请求头CRUD验证\"
  }")
echo "$create_response"

bill_id=$(printf '%s' "$create_response" | sed -n 's/.*"billId":"\([^"]*\)".*/\1/p')
if [ -z "$bill_id" ]; then
  echo "Failed to extract billId from create response" >&2
  exit 1
fi

curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers/query" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"status":"10_PENDING_REVIEW"}'
curl -fsS "$BASE_URL/api/cnaps/vouchers/$bill_id"
curl -fsS -X PUT "$BASE_URL/api/cnaps/vouchers/$bill_id" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"payeeAccountNo":"622200000000000001","payeeName":"收款人名称-已修改","amount":"6600.00"}'
curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers/$bill_id/delete" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"deleteReason":"无请求头CRUD验证完成"}'
