package com.ruisui.cnaps.web.tuxedo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MockTuxedoClient implements TuxedoClient {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AtomicInteger serial = new AtomicInteger(1999);
    private final Map<String, Map<String, Object>> vouchers = new ConcurrentHashMap<>();

    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        return switch (serviceName) {
            case "SYSHEALTH" -> ok("健康检查成功", health());
            case "DICTQRY" -> ok("查询成功", data(dicts(text(request, "DICT_TYPE"))));
            case "BANKQRY" -> ok("查询成功", data(bankPage(request)));
            case "CNAPS5701E" -> create(request);
            case "CNAPS4609Q" -> ok("查询成功", data(voucherPage(request, null)));
            case "CNAPS5702Q" -> ok("查询成功", data(voucherPage(request, "10_PENDING_REVIEW")));
            case "CNAPS5702I" -> detail(request);
            case "CNAPS5701U" -> update(request);
            case "CNAPS5701D" -> delete(request);
            case "CNAPS5702A" -> reviewPass(request);
            case "CNAPS5702R" -> reviewReturn(request);
            default -> TuxedoResponse.fail("4003", "Tuxedo服务不可用：" + serviceName);
        };
    }

    private TuxedoResponse create(TuxedoRequest request) {
        String validationError = required(request, "PAYEE_ACCT");
        if (validationError != null) {
            return TuxedoResponse.fail("2001", validationError);
        }
        String serialNo = String.format("%07d", serial.incrementAndGet());
        String workDate = text(request, "WORK_DATE", LocalDate.now().toString());
        String branchNo = text(request, "BRANCH_NO", "772");
        String billId = "B" + workDate.replace("-", "") + branchNo + serialNo;

        Map<String, Object> voucher = new LinkedHashMap<>(request.fields());
        voucher.put("BILL_ID", billId);
        voucher.put("SERIAL_NO", serialNo);
        voucher.put("STATUS", "10_PENDING_REVIEW");
        voucher.put("LAST_ACTION", "CREATE");
        voucher.put("VERSION_NO", 1);
        voucher.put("DEBIT_MODE", text(request, "DEBIT_MODE", "1"));
        voucher.put("FEE_AMOUNT", text(request, "FEE_AMOUNT", "0.00"));
        voucher.put("FEE_CHARGE_MODE", text(request, "FEE_CHARGE_MODE", "1"));
        voucher.put("SEND_MODE", text(request, "SEND_MODE", "0"));
        voucher.put("FAX_FLAG", text(request, "FAX_FLAG", "0"));
        touch(voucher, request);
        String now = now();
        voucher.put("CREATED_AT", now);
        voucher.put("UPDATED_AT", now);
        vouchers.put(billId, voucher);
        return ok("录入成功，待复核", voucher);
    }

    private TuxedoResponse update(TuxedoRequest request) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        if (!editable(voucher)) {
            return TuxedoResponse.fail("3003", "当前状态不允许操作：" + voucher.get("STATUS"));
        }
        request.fields().forEach((key, value) -> {
            if (!key.endsWith("_ID") && !"REQ_ID".equals(key) && !"REQUEST_ID".equals(key)) {
                voucher.put(key, value);
            }
        });
        voucher.put("STATUS", "10_PENDING_REVIEW");
        voucher.put("LAST_ACTION", "UPDATE");
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        voucher.remove("REJECT_REASON");
        voucher.remove("CHECKER_NO");
        voucher.remove("CHECKER_TIME");
        touch(voucher, request);
        return ok("修改成功，待复核", voucher);
    }

    private TuxedoResponse delete(TuxedoRequest request) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        if (!editable(voucher)) {
            return TuxedoResponse.fail("3003", "当前状态不允许操作：" + voucher.get("STATUS"));
        }
        voucher.put("STATUS", "40_DELETED");
        voucher.put("LAST_ACTION", "DELETE");
        voucher.put("DELETE_REASON", text(request, "DELETE_REASON"));
        voucher.put("DELETE_OPERATOR_NO", text(request, "OPERATOR_NO"));
        voucher.put("DELETE_TIME", now());
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        touch(voucher, request);
        return ok("删除成功", voucher);
    }

    private TuxedoResponse reviewPass(TuxedoRequest request) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        if (text(request, "OPERATOR_NO").equals(voucher.get("OPERATOR_NO"))) {
            return TuxedoResponse.fail("3005", "不能复核本人录入单据");
        }
        if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
            return TuxedoResponse.fail("3004", "单据状态已变化");
        }
        voucher.put("STATUS", "20_REVIEW_APPROVED");
        voucher.put("LAST_ACTION", "REVIEW_PASS");
        voucher.put("CHECKER_NO", text(request, "OPERATOR_NO"));
        voucher.put("CHECKER_TIME", now());
        voucher.put("REVIEW_COMMENT", text(request, "REVIEW_COMMENT"));
        voucher.remove("REJECT_REASON");
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        touch(voucher, request);
        return ok("操作已成功", voucher);
    }

    private TuxedoResponse reviewReturn(TuxedoRequest request) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        String rejectReason = text(request, "REJECT_REASON");
        if (rejectReason == null || rejectReason.isBlank()) {
            return TuxedoResponse.fail("2001", "必输字段为空：rejectReason");
        }
        if (text(request, "OPERATOR_NO").equals(voucher.get("OPERATOR_NO"))) {
            return TuxedoResponse.fail("3005", "不能复核本人录入单据");
        }
        if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
            return TuxedoResponse.fail("3004", "单据状态已变化");
        }
        voucher.put("STATUS", "30_REVIEW_REJECTED");
        voucher.put("LAST_ACTION", "REVIEW_RETURN");
        voucher.put("CHECKER_NO", text(request, "OPERATOR_NO"));
        voucher.put("CHECKER_TIME", now());
        voucher.put("REJECT_REASON", rejectReason);
        voucher.put("REVIEW_COMMENT", text(request, "REVIEW_COMMENT"));
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        touch(voucher, request);
        return ok("复核退回成功", voucher);
    }

    private TuxedoResponse detail(TuxedoRequest request) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        return ok("查询成功", voucher);
    }

    private Map<String, Object> voucherPage(TuxedoRequest request, String forcedStatus) {
        String status = forcedStatus == null ? text(request, "STATUS") : forcedStatus;
        List<Map<String, Object>> records = vouchers.values().stream()
            .filter(voucher -> status == null || status.isBlank() || status.equals(voucher.get("STATUS")))
            .filter(voucher -> Boolean.parseBoolean(text(request, "INCLUDE_DELETED", "false")) || !"40_DELETED".equals(voucher.get("STATUS")))
            .filter(voucher -> contains(voucher, "PAYEE_NAME", text(request, "PAYEE_NAME")))
            .map(voucher -> (Map<String, Object>) new LinkedHashMap<>(voucher))
            .toList();
        return Map.of(
            "PAGE_NO", pageNo(request),
            "PAGE_SIZE", pageSize(request),
            "TOTAL", records.size(),
            "RECORDS", records
        );
    }

    private Map<String, Object> bankPage(TuxedoRequest request) {
        Map<String, Object> bank = Map.of(
            "BANK_NO", "102290000002",
            "BANK_NAME", "接收行名称",
            "CITY", "上海",
            "SYSTEM_TYPE", "CNAPS",
            "STATUS", "1"
        );
        return Map.of("PAGE_NO", pageNo(request), "PAGE_SIZE", pageSize(request), "TOTAL", 1, "RECORDS", List.of(bank));
    }

    private List<Map<String, Object>> dicts(String dictType) {
        String type = dictType == null || dictType.isBlank() ? "BUSINESS_TYPE" : dictType;
        return List.of(Map.of("DICT_TYPE", type, "DICT_CODE", "02102", "DICT_NAME", "普通汇兑", "STATUS", "1", "SORT_NO", 1));
    }

    private Map<String, Object> health() {
        return Map.of("WEBFE", "UP", "TUXEDO", "UP", "ORACLE", "UP", "SERVICE", "SYSHEALTH", "CHECK_TIME", now());
    }

    private Map<String, Object> data(Object value) {
        return Map.of("_DATA", value);
    }

    private TuxedoResponse ok(String message, Map<String, Object> fields) {
        return TuxedoResponse.ok(message, fields);
    }

    private Map<String, Object> load(TuxedoRequest request) {
        return vouchers.get(text(request, "BILL_ID"));
    }

    private boolean editable(Map<String, Object> voucher) {
        return "10_PENDING_REVIEW".equals(voucher.get("STATUS")) || "30_REVIEW_REJECTED".equals(voucher.get("STATUS"));
    }

    private void touch(Map<String, Object> voucher, TuxedoRequest request) {
        String now = now();
        voucher.put("UPDATED_AT", now);
        voucher.put("LAST_ACTION_TIME", now);
        voucher.put("LAST_OPERATOR_NO", text(request, "OPERATOR_NO"));
        voucher.put("LAST_REQUEST_ID", text(request, "REQ_ID"));
    }

    private String required(TuxedoRequest request, String key) {
        String value = text(request, key);
        return value == null || value.isBlank() ? "必输字段为空：payeeAccountNo" : null;
    }

    private boolean contains(Map<String, Object> voucher, String key, String value) {
        return value == null || value.isBlank() || String.valueOf(voucher.getOrDefault(key, "")).contains(value);
    }

    private String text(TuxedoRequest request, String key) {
        Object value = request.fields().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String text(TuxedoRequest request, String key, String defaultValue) {
        String value = text(request, key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int pageNo(TuxedoRequest request) {
        return Math.max(number(request.fields(), "PAGE_NO"), 1);
    }

    private int pageSize(TuxedoRequest request) {
        int value = number(request.fields(), "PAGE_SIZE");
        return value <= 0 ? 10 : value;
    }

    private int number(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String now() {
        return TIME_FORMAT.format(OffsetDateTime.now());
    }
}
