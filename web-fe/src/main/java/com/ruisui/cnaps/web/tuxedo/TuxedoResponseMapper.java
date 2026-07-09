package com.ruisui.cnaps.web.tuxedo;

import com.ruisui.cnaps.web.dto.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TuxedoResponseMapper {
    private static final String DATA_FIELD = "_DATA";
    private static final Map<String, String> FIELD_NAMES = Map.ofEntries(
        Map.entry("WEBFE", "webfe"),
        Map.entry("TUXEDO", "tuxedo"),
        Map.entry("ORACLE", "oracle"),
        Map.entry("SERVICE", "service"),
        Map.entry("CHECK_TIME", "checkTime"),
        Map.entry("PAGE_NO", "pageNo"),
        Map.entry("PAGE_SIZE", "pageSize"),
        Map.entry("TOTAL", "total"),
        Map.entry("RECORDS", "records"),
        Map.entry("DICT_TYPE", "dictType"),
        Map.entry("DICT_CODE", "dictCode"),
        Map.entry("DICT_NAME", "dictName"),
        Map.entry("SORT_NO", "sortNo"),
        Map.entry("BANK_NO", "bankNo"),
        Map.entry("BANK_NAME", "bankName"),
        Map.entry("SYSTEM_TYPE", "systemType"),
        Map.entry("BILL_ID", "billId"),
        Map.entry("SERIAL_NO", "serialNo"),
        Map.entry("BUSINESS_TYPE", "businessType"),
        Map.entry("ACCOUNT_PART1", "accountPart1"),
        Map.entry("ACCOUNT_PART2", "accountPart2"),
        Map.entry("ACCOUNT_PART3", "accountPart3"),
        Map.entry("ACCOUNT_NAME", "accountName"),
        Map.entry("PAYER_NAME", "payerName"),
        Map.entry("PAYEE_ACCT", "payeeAccountNo"),
        Map.entry("PAYEE_ACCOUNT_NO", "payeeAccountNo"),
        Map.entry("PAYEE_NAME", "payeeName"),
        Map.entry("RECEIVE_BANK_NO", "receiveBankNo"),
        Map.entry("RECEIVE_BANK_NAME", "receiveBankName"),
        Map.entry("DEBIT_MODE", "debitMode"),
        Map.entry("FEE_AMOUNT", "feeAmount"),
        Map.entry("FEE_CHARGE_MODE", "feeChargeMode"),
        Map.entry("SEND_MODE", "sendMode"),
        Map.entry("FAX_FLAG", "faxFlag"),
        Map.entry("VOUCHER_NO", "voucherNo"),
        Map.entry("OPERATOR_NO", "operatorNo"),
        Map.entry("BRANCH_NO", "branchNo"),
        Map.entry("WORK_DATE", "workDate"),
        Map.entry("CHECKER_NO", "checkerNo"),
        Map.entry("CHECKER_TIME", "checkerTime"),
        Map.entry("REVIEW_COMMENT", "reviewComment"),
        Map.entry("REJECT_REASON", "rejectReason"),
        Map.entry("DELETE_REASON", "deleteReason"),
        Map.entry("DELETE_OPERATOR_NO", "deleteOperatorNo"),
        Map.entry("DELETE_TIME", "deleteTime"),
        Map.entry("LAST_ACTION", "lastAction"),
        Map.entry("LAST_OPERATOR_NO", "lastOperatorNo"),
        Map.entry("LAST_REQUEST_ID", "lastRequestId"),
        Map.entry("LAST_ACTION_TIME", "lastActionTime"),
        Map.entry("CREATED_AT", "createdAt"),
        Map.entry("UPDATED_AT", "updatedAt"),
        Map.entry("VERSION_NO", "versionNo")
    );

    public ApiResponse<Object> toApiResponse(String requestId, TuxedoResponse response) {
        if (response.success()) {
            Object data = response.fields().containsKey(DATA_FIELD)
                ? mapValue(response.fields().get(DATA_FIELD))
                : mapFields(response.fields());
            return ApiResponse.ok(requestId, response.respMsg(), data);
        }
        return ApiResponse.fail(requestId, response.respCode(), response.respMsg());
    }

    @SuppressWarnings("unchecked")
    private Object mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return mapFields((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::mapValue).toList();
        }
        return value;
    }

    private Map<String, Object> mapFields(Map<String, Object> fields) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            if ("RESP_CODE".equals(key) || "RESP_MSG".equals(key) || "_DATA".equals(key)) {
                return;
            }
            mapped.put(FIELD_NAMES.getOrDefault(key, lowerCamel(key)), mapValue(value));
        });
        return mapped;
    }

    private String lowerCamel(String key) {
        String lower = key.toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : lower.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }
}
