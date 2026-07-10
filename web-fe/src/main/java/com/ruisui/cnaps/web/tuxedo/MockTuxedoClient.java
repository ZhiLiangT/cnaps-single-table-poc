package com.ruisui.cnaps.web.tuxedo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MockTuxedoClient implements TuxedoClient {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> REQUIRED_CREATE_FIELDS = Map.ofEntries(
        Map.entry("WORK_DATE", "workDate"),
        Map.entry("BUSINESS_TYPE", "businessType"),
        Map.entry("ACCOUNT_PART1", "accountPart1"),
        Map.entry("ACCOUNT_PART2", "accountPart2"),
        Map.entry("ACCOUNT_PART3", "accountPart3"),
        Map.entry("PAYEE_ACCT", "payeeAccountNo"),
        Map.entry("PAYEE_NAME", "payeeName"),
        Map.entry("PRIORITY", "priority"),
        Map.entry("SYSTEM_TYPE", "systemType"),
        Map.entry("AMOUNT", "amount")
    );
    private static final List<String> BUSINESS_FIELDS = List.of(
        "WORK_DATE",
        "BUSINESS_TYPE",
        "ACCOUNT_PART1",
        "ACCOUNT_PART2",
        "ACCOUNT_PART3",
        "ACCOUNT_NAME",
        "PAYER_NAME",
        "PAYEE_ACCT",
        "PAYEE_NAME",
        "PRIORITY",
        "RECEIVE_BANK_NO",
        "RECEIVE_BANK_NAME",
        "SYSTEM_TYPE",
        "AMOUNT",
        "DEBIT_MODE",
        "FEE_AMOUNT",
        "FEE_CHARGE_MODE",
        "SEND_MODE",
        "FAX_FLAG",
        "VOUCHER_NO",
        "REMARK"
    );
    private static final Map<String, List<Map<String, Object>>> DICTIONARIES = Map.of(
        "BUSINESS_TYPE", List.of(dictItem("BUSINESS_TYPE", "02102", "普通汇兑", 1)),
        "PRIORITY", List.of(dictItem("PRIORITY", "NORM", "普通", 1)),
        "FEE_CHARGE_MODE", List.of(dictItem("FEE_CHARGE_MODE", "1", "同城收费", 1)),
        "SEND_MODE", List.of(dictItem("SEND_MODE", "0", "柜面", 1)),
        "DEBIT_MODE", List.of(dictItem("DEBIT_MODE", "1", "扣收", 1)),
        "FAX_FLAG", List.of(
            dictItem("FAX_FLAG", "0", "否", 1),
            dictItem("FAX_FLAG", "1", "是", 2)
        ),
        "SYSTEM_TYPE", List.of(dictItem("SYSTEM_TYPE", "CNAPS", "CNAPS", 1))
    );
    private static final List<Map<String, Object>> BANKS = List.of(Map.of(
        "BANK_NO", "102290000002",
        "BANK_NAME", "接收行名称",
        "CITY", "上海",
        "SYSTEM_TYPE", "CNAPS",
        "STATUS", "1"
    ));
    private final AtomicInteger serial = new AtomicInteger(1999);
    private final Map<String, Map<String, Object>> vouchers = new ConcurrentHashMap<>();

    @Override
    public TuxedoResponse call(String serviceName, TuxedoRequest request) {
        return switch (serviceName) {
            case "SYSHEALTH" -> ok("健康检查成功", health());
            case "DICTQRY" -> dictQuery(request);
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
        TuxedoResponse validation = validateCreate(request);
        if (validation != null) {
            return validation;
        }
        String workDate = text(request, "WORK_DATE");
        if (!validWorkDate(workDate)) {
            return TuxedoResponse.fail("2002", "工作日期格式错误");
        }
        String serialNo = String.format("%07d", serial.incrementAndGet());
        String branchNo = text(request, "BRANCH_NO", "772");
        String billId = "B" + workDate.replace("-", "") + branchNo + serialNo;

        Map<String, Object> voucher = new LinkedHashMap<>();
        for (String field : BUSINESS_FIELDS) {
            if (request.fields().containsKey(field)) {
                voucher.put(field, request.fields().get(field));
            }
        }
        voucher.put("OPERATOR_NO", text(request, "OPERATOR_NO"));
        voucher.put("BRANCH_NO", branchNo);
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
        TuxedoResponse validation = validateUpdate(request);
        if (validation != null) {
            return validation;
        }
        for (String field : BUSINESS_FIELDS) {
            if (request.fields().containsKey(field)) {
                voucher.put(field, request.fields().get(field));
            }
        }
        voucher.put("STATUS", "10_PENDING_REVIEW");
        voucher.put("LAST_ACTION", "UPDATE");
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        voucher.remove("REJECT_REASON");
        voucher.remove("CHECKER_NO");
        voucher.remove("CHECKER_TIME");
        voucher.remove("REVIEW_COMMENT");
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
        setOptional(voucher, "DELETE_REASON", text(request, "DELETE_REASON"));
        setOptional(voucher, "DELETE_OPERATOR_NO", text(request, "OPERATOR_NO"));
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
        if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
            return TuxedoResponse.fail("3004", "单据状态已变化");
        }
        voucher.put("STATUS", "20_REVIEW_APPROVED");
        voucher.put("LAST_ACTION", "REVIEW_PASS");
        setOptional(voucher, "CHECKER_NO", text(request, "OPERATOR_NO"));
        voucher.put("CHECKER_TIME", now());
        setOptional(voucher, "REVIEW_COMMENT", text(request, "REVIEW_COMMENT"));
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
        if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
            return TuxedoResponse.fail("3004", "单据状态已变化");
        }
        voucher.put("STATUS", "30_REVIEW_REJECTED");
        voucher.put("LAST_ACTION", "REVIEW_RETURN");
        setOptional(voucher, "CHECKER_NO", text(request, "OPERATOR_NO"));
        voucher.put("CHECKER_TIME", now());
        voucher.put("REJECT_REASON", rejectReason);
        voucher.remove("REVIEW_COMMENT");
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
        int pageNo = pageNo(request);
        int pageSize = pageSize(request);
        List<Map<String, Object>> matching = vouchers.values().stream()
            .filter(voucher -> matches(voucher, "STATUS", status, false))
            .filter(voucher -> matches(voucher, "WORK_DATE", text(request, "WORK_DATE"), false))
            .filter(voucher -> matches(voucher, "BRANCH_NO", text(request, "BRANCH_NO"), false))
            .filter(voucher -> matches(voucher, "SERIAL_NO", text(request, "SERIAL_NO"), false))
            .filter(voucher -> matches(voucher, "VOUCHER_NO", text(request, "VOUCHER_NO"), false))
            .filter(voucher -> matches(voucher, "PAYEE_ACCT", text(request, "PAYEE_ACCT"), false))
            .filter(voucher -> matches(voucher, "PAYEE_NAME", text(request, "PAYEE_NAME"), true))
            .filter(voucher -> Boolean.parseBoolean(text(request, "INCLUDE_DELETED", "false"))
                || !"40_DELETED".equals(voucher.get("STATUS")))
            .sorted(Comparator.comparing(voucher -> String.valueOf(voucher.get("BILL_ID"))))
            .map(voucher -> (Map<String, Object>) new LinkedHashMap<>(voucher))
            .toList();
        return Map.of(
            "PAGE_NO", pageNo,
            "PAGE_SIZE", pageSize,
            "TOTAL", matching.size(),
            "RECORDS", pageSlice(matching, pageNo, pageSize)
        );
    }

    private Map<String, Object> bankPage(TuxedoRequest request) {
        int pageNo = pageNo(request);
        int pageSize = pageSize(request);
        List<Map<String, Object>> matching = BANKS.stream()
            .filter(bank -> matches(bank, "BANK_NO", text(request, "BANK_NO"), false))
            .filter(bank -> matches(bank, "BANK_NAME", text(request, "KEYWORD"), true))
            .filter(bank -> matches(bank, "CITY", text(request, "CITY"), false))
            .filter(bank -> matches(bank, "SYSTEM_TYPE", text(request, "SYSTEM_TYPE"), false))
            .map(bank -> (Map<String, Object>) new LinkedHashMap<>(bank))
            .toList();
        return Map.of(
            "PAGE_NO", pageNo,
            "PAGE_SIZE", pageSize,
            "TOTAL", matching.size(),
            "RECORDS", pageSlice(matching, pageNo, pageSize)
        );
    }

    private TuxedoResponse dictQuery(TuxedoRequest request) {
        String dictType = text(request, "DICT_TYPE");
        List<Map<String, Object>> items = DICTIONARIES.get(dictType);
        if (items == null) {
            return TuxedoResponse.fail("2003", "字典类型不存在：" + dictType);
        }
        return ok("查询成功", data(items));
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

    private TuxedoResponse validateCreate(TuxedoRequest request) {
        for (Map.Entry<String, String> required : REQUIRED_CREATE_FIELDS.entrySet()) {
            String value = text(request, required.getKey());
            if (value == null || value.isBlank()) {
                return TuxedoResponse.fail("2001", "必输字段为空：" + required.getValue());
            }
        }
        if (!validMoney(text(request, "AMOUNT"), false) || !validMoney(text(request, "FEE_AMOUNT", "0.00"), true)) {
            return TuxedoResponse.fail("2002", "金额格式错误");
        }
        return null;
    }

    private TuxedoResponse validateUpdate(TuxedoRequest request) {
        if (request.fields().containsKey("AMOUNT") && !validMoney(text(request, "AMOUNT"), false)) {
            return TuxedoResponse.fail("2002", "金额格式错误");
        }
        if (request.fields().containsKey("FEE_AMOUNT") && !validMoney(text(request, "FEE_AMOUNT"), true)) {
            return TuxedoResponse.fail("2002", "金额格式错误");
        }
        if (request.fields().containsKey("WORK_DATE") && !validWorkDate(text(request, "WORK_DATE"))) {
            return TuxedoResponse.fail("2002", "工作日期格式错误");
        }
        return null;
    }

    private boolean validMoney(String value, boolean zeroAllowed) {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            return false;
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            return amount.scale() <= 2 && (zeroAllowed ? amount.signum() >= 0 : amount.signum() > 0);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean validWorkDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return false;
        }
        try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private void touch(Map<String, Object> voucher, TuxedoRequest request) {
        String now = now();
        voucher.put("UPDATED_AT", now);
        voucher.put("LAST_ACTION_TIME", now);
        voucher.put("LAST_OPERATOR_NO", text(request, "OPERATOR_NO"));
        voucher.put("LAST_REQUEST_ID", text(request, "REQ_ID"));
    }

    private void setOptional(Map<String, Object> fields, String key, String value) {
        if (value == null || value.isBlank()) {
            fields.remove(key);
            return;
        }
        fields.put(key, value);
    }

    private boolean matches(Map<String, Object> record, String key, String criterion, boolean substring) {
        if (criterion == null || criterion.isBlank()) {
            return true;
        }
        String value = String.valueOf(record.getOrDefault(key, ""));
        return substring ? value.contains(criterion) : value.equals(criterion);
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
        return positivePageValue(request.fields(), "PAGE_NO", 1);
    }

    private int pageSize(TuxedoRequest request) {
        return positivePageValue(request.fields(), "PAGE_SIZE", 10);
    }

    private int positivePageValue(Map<String, Object> fields, String key, int defaultValue) {
        Object value = fields.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private List<Map<String, Object>> pageSlice(List<Map<String, Object>> records, int pageNo, int pageSize) {
        long fromIndex = ((long) pageNo - 1L) * pageSize;
        if (fromIndex >= records.size()) {
            return List.of();
        }
        long toIndex = Math.min(fromIndex + (long) pageSize, records.size());
        return records.subList((int) fromIndex, (int) toIndex);
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

    private static Map<String, Object> dictItem(String type, String code, String name, int sortNo) {
        return Map.of(
            "DICT_TYPE", type,
            "DICT_CODE", code,
            "DICT_NAME", name,
            "STATUS", "1",
            "SORT_NO", sortNo
        );
    }
}
