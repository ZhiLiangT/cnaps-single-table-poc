package com.ruisui.cnaps.web.tuxedo;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class TuxedoRequestMapper {
    private static final Map<String, String> BODY_FIELD_NAMES = Map.ofEntries(
        Map.entry("requestId", "REQUEST_ID"),
        Map.entry("billId", "BILL_ID"),
        Map.entry("serialNo", "SERIAL_NO"),
        Map.entry("businessType", "BUSINESS_TYPE"),
        Map.entry("accountPart1", "ACCOUNT_PART1"),
        Map.entry("accountPart2", "ACCOUNT_PART2"),
        Map.entry("accountPart3", "ACCOUNT_PART3"),
        Map.entry("accountName", "ACCOUNT_NAME"),
        Map.entry("payerName", "PAYER_NAME"),
        Map.entry("payeeAccountNo", "PAYEE_ACCT"),
        Map.entry("payeeName", "PAYEE_NAME"),
        Map.entry("priority", "PRIORITY"),
        Map.entry("receiveBankNo", "RECEIVE_BANK_NO"),
        Map.entry("receiveBankName", "RECEIVE_BANK_NAME"),
        Map.entry("systemType", "SYSTEM_TYPE"),
        Map.entry("amount", "AMOUNT"),
        Map.entry("debitMode", "DEBIT_MODE"),
        Map.entry("feeAmount", "FEE_AMOUNT"),
        Map.entry("feeChargeMode", "FEE_CHARGE_MODE"),
        Map.entry("sendMode", "SEND_MODE"),
        Map.entry("faxFlag", "FAX_FLAG"),
        Map.entry("voucherNo", "VOUCHER_NO"),
        Map.entry("remark", "REMARK"),
        Map.entry("status", "STATUS"),
        Map.entry("rejectReason", "REJECT_REASON"),
        Map.entry("reviewComment", "REVIEW_COMMENT"),
        Map.entry("deleteReason", "DELETE_REASON"),
        Map.entry("pageNo", "PAGE_NO"),
        Map.entry("pageSize", "PAGE_SIZE"),
        Map.entry("includeDeleted", "INCLUDE_DELETED")
    );

    public String serviceName(String method, String path) {
        String verb = method.toUpperCase(Locale.ROOT);
        String cleanPath = path == null ? "" : path.split("\\?", 2)[0];

        if ("GET".equals(verb) && "/api/health".equals(cleanPath)) {
            return "SYSHEALTH";
        }
        if ("GET".equals(verb) && cleanPath.startsWith("/api/dicts/")) {
            return "DICTQRY";
        }
        if ("GET".equals(verb) && "/api/banks".equals(cleanPath)) {
            return "BANKQRY";
        }
        if ("POST".equals(verb) && "/api/cnaps/vouchers".equals(cleanPath)) {
            return "CNAPS5701E";
        }
        if ("POST".equals(verb) && "/api/cnaps/vouchers/query".equals(cleanPath)) {
            return "CNAPS4609Q";
        }
        if ("POST".equals(verb) && "/api/cnaps/vouchers/review-list".equals(cleanPath)) {
            return "CNAPS5702Q";
        }
        if (cleanPath.startsWith("/api/cnaps/vouchers/")) {
            if ("PUT".equals(verb)) {
                return "CNAPS5701U";
            }
            if ("GET".equals(verb)
                && !"/api/cnaps/vouchers/query".equals(cleanPath)
                && !"/api/cnaps/vouchers/review-list".equals(cleanPath)) {
                return "CNAPS5702I";
            }
            if ("POST".equals(verb) && cleanPath.endsWith("/delete")) {
                return "CNAPS5701D";
            }
            if ("POST".equals(verb) && cleanPath.endsWith("/review-pass")) {
                return "CNAPS5702A";
            }
            if ("POST".equals(verb) && cleanPath.endsWith("/review-return")) {
                return "CNAPS5702R";
            }
        }
        throw new IllegalArgumentException("Unsupported WebFE operation: " + method + " " + path);
    }

    public TuxedoRequest from(
        String requestId,
        String operatorNo,
        String branchNo,
        Map<String, ?> body
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        body.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            fields.put(BODY_FIELD_NAMES.getOrDefault(key, camelToFieldName(key)), value);
        });

        putIfPresent(fields, "REQUEST_ID", requestId);
        putIfPresent(fields, "REQ_ID", requestId);
        putIfPresent(fields, "OPERATOR_NO", operatorNo);
        putIfPresent(fields, "BRANCH_NO", branchNo);
        return new TuxedoRequest(fields);
    }

    private static void putIfPresent(Map<String, Object> fields, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(fieldName, value);
        }
    }

    private static String camelToFieldName(String key) {
        StringBuilder result = new StringBuilder();
        for (char ch : key.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                result.append('_');
            }
            result.append(Character.toUpperCase(ch));
        }
        return result.toString();
    }
}
