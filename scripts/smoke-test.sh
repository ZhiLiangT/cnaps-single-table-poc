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

review_create_response=$(curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d "{
    \"workDate\":\"$WORK_DATE\",
    \"businessType\":\"02102\",
    \"accountPart1\":\"404045\",
    \"accountPart2\":\"00772\",
    \"accountPart3\":\"000000000001\",
    \"payeeAccountNo\":\"622200000000000002\",
    \"payeeName\":\"复核冒烟收款人\",
    \"priority\":\"NORM\",
    \"systemType\":\"CNAPS\",
    \"amount\":\"1200.00\",
    \"voucherNo\":\"REVIEW-SMOKE\",
    \"remark\":\"审核闭环冒烟验证\"
  }")
echo "$review_create_response"

review_bill_id=$(printf '%s' "$review_create_response" | sed -n 's/.*"billId":"\([^"]*\)".*/\1/p')
if [ -z "$review_bill_id" ]; then
  echo "Failed to extract billId from review smoke create response" >&2
  exit 1
fi

review_list_response=$(curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers/review-list" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"status":"20_REVIEW_APPROVED","pageSize":100}')
printf '%s' "$review_list_response" | grep "$review_bill_id" >/dev/null
printf '%s' "$review_list_response" | grep '"status":"10_PENDING_REVIEW"' >/dev/null

review_return_response=$(curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers/$review_bill_id/review-return")
printf '%s' "$review_return_response" | grep '"status":"30_REVIEW_REJECTED"' >/dev/null
printf '%s' "$review_return_response" | grep '"lastAction":"REVIEW_RETURN"' >/dev/null

curl -fsS -X PUT "$BASE_URL/api/cnaps/vouchers/$review_bill_id" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"payeeAccountNo":"622200000000000002","payeeName":"复核冒烟收款人-已修改","amount":"1300.00"}' \
  | grep '"status":"10_PENDING_REVIEW"' >/dev/null

review_pass_response=$(curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers/$review_bill_id/review-pass")
printf '%s' "$review_pass_response" | grep '"status":"20_REVIEW_APPROVED"' >/dev/null
printf '%s' "$review_pass_response" | grep '"lastAction":"REVIEW_PASS"' >/dev/null

repeat_status=$(curl -sS -o /tmp/cnaps-repeat-review-response.txt -w "%{http_code}" \
  -X POST "$BASE_URL/api/cnaps/vouchers/$review_bill_id/review-pass")
if [ "$repeat_status" != "409" ]; then
  cat /tmp/cnaps-repeat-review-response.txt >&2
  echo "Expected repeated review to return HTTP 409, got $repeat_status" >&2
  exit 1
fi
grep '"respCode":"3004"' /tmp/cnaps-repeat-review-response.txt >/dev/null
