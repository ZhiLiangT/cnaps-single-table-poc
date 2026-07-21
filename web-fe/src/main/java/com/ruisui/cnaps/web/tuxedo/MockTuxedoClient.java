package com.ruisui.cnaps.web.tuxedo;

import com.ruisui.cnaps.web.support.RequestSupport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        "PAYER_ADDRESS",
        "PAYEE_ACCT",
        "PAYEE_NAME",
        "PAYEE_ADDRESS",
        "PAYER_BANK_NAME",
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
    private static final List<String> LIST_FIELDS = List.of(
        "BILL_ID", "WORK_DATE", "SERIAL_NO", "VOUCHER_NO", "PAYEE_ACCT", "PAYEE_NAME",
        "AMOUNT", "STATUS", "VERSION_NO"
    );
    private static final List<String> REVIEW_ACTION_FIELDS = List.of(
        "BILL_ID", "STATUS", "CHECKER_NO", "CHECKER_TIME", "LAST_ACTION", "VERSION_NO"
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
    private static final Map<String, Set<String>> DICTIONARY_VALUES = Map.of(
        "BUSINESS_TYPE", Set.of("02102"),
        "PRIORITY", Set.of("NORM"),
        "SYSTEM_TYPE", Set.of("CNAPS"),
        "DEBIT_MODE", Set.of("1"),
        "FEE_CHARGE_MODE", Set.of("1"),
        "SEND_MODE", Set.of("0"),
        "FAX_FLAG", Set.of("0", "1")
    );
    private static final Set<String> OPTIONAL_DICTIONARY_FIELDS = Set.of(
        "DEBIT_MODE", "FEE_CHARGE_MODE", "SEND_MODE", "FAX_FLAG"
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
        if ("CNAPS4609Q".equals(serviceName)) {
            TuxedoResponse validation = validateWorkDateFilter(request);
            if (validation != null) {
                return validation;
            }
        }
        return switch (serviceName) {
            case "SYSHEALTH" -> ok("健康检查成功", health());
            case "DICTQRY" -> dictQuery(request);
            case "BANKQRY" -> ok("查询成功", data(bankPage(request)));
            case "CNAPS5701E" -> create(request);
            case "CNAPS4609Q" -> ok("查询成功", data(voucherPage(request, null)));
            case "CNAPS5702I" -> detail(request);
            case "CNAPS5702A" -> review(request, "20_REVIEW_APPROVED", "REVIEW_PASS", "review pass success");
            case "CNAPS5702R" -> review(request, "30_REVIEW_REJECTED", "REVIEW_RETURN", "review return success");
            case "CNAPS5701U" -> update(request);
            case "CNAPS5701D" -> delete(request);
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
                String value = text(request, field);
                if (OPTIONAL_DICTIONARY_FIELDS.contains(field)
                    && (value == null || value.isBlank())) {
                    continue;
                }
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
        return ok("录入成功，待审核", voucher);
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
                String value = text(request, field);
                if (OPTIONAL_DICTIONARY_FIELDS.contains(field)
                    && (value == null || value.isBlank())) {
                    continue;
                }
                voucher.put(field, request.fields().get(field));
            }
        }
        voucher.put("STATUS", "10_PENDING_REVIEW");
        voucher.remove("CHECKER_NO");
        voucher.remove("CHECKER_TIME");
        voucher.remove("REVIEW_COMMENT");
        voucher.remove("REJECT_REASON");
        voucher.put("LAST_ACTION", "UPDATE");
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        touch(voucher, request);
        return ok("修改成功", voucher);
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

    private TuxedoResponse detail(TuxedoRequest request) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        return ok("查询成功", voucher);
    }

    private TuxedoResponse review(
        TuxedoRequest request,
        String targetStatus,
        String lastAction,
        String message
    ) {
        Map<String, Object> voucher = load(request);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        synchronized (voucher) {
            if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
                return TuxedoResponse.fail("3004", "当前状态不允许审核");
            }
            voucher.put("STATUS", targetStatus);
            voucher.put("CHECKER_NO", text(request, "OPERATOR_NO"));
            voucher.put("CHECKER_TIME", now());
            voucher.put("LAST_ACTION", lastAction);
            voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
            touch(voucher, request);
            return ok(message, reviewSummary(voucher));
        }
    }

    private Map<String, Object> voucherPage(TuxedoRequest request, String forcedStatus) {
        String status = forcedStatus == null ? text(request, "STATUS") : forcedStatus;
        int pageNo = pageNo(request);
        int pageSize = pageSize(request);
        List<Map<String, Object>> matching = vouchers.values().stream()
            .filter(voucher -> matches(voucher, "STATUS", status, false))
            .filter(voucher -> matchesLowerWorkDate(voucher, optionalText(request, "START_WORK_DATE")))
            .filter(voucher -> matchesUpperWorkDate(voucher, optionalText(request, "END_WORK_DATE")))
            .filter(voucher -> matches(voucher, "BRANCH_NO", text(request, "BRANCH_NO"), false))
            .filter(voucher -> matches(voucher, "SERIAL_NO", text(request, "SERIAL_NO"), false))
            .filter(voucher -> matches(voucher, "VOUCHER_NO", text(request, "VOUCHER_NO"), false))
            .filter(voucher -> matches(voucher, "PAYEE_ACCT", text(request, "PAYEE_ACCT"), false))
            .filter(voucher -> matches(voucher, "PAYEE_NAME", text(request, "PAYEE_NAME"), true))
            .filter(voucher -> Boolean.parseBoolean(text(request, "INCLUDE_DELETED", "false"))
                || !"40_DELETED".equals(voucher.get("STATUS")))
            .sorted(Comparator.comparing(voucher -> String.valueOf(voucher.get("BILL_ID"))))
            .map(this::listRecord)
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
            .filter(bank -> {
                String keyword = text(request, "KEYWORD");
                return matches(bank, "BANK_NAME", keyword, true)
                    || matches(bank, "BANK_NO", keyword, true);
            })
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
        return "10_PENDING_REVIEW".equals(voucher.get("STATUS"))
            || "30_REVIEW_REJECTED".equals(voucher.get("STATUS"));
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
        TuxedoResponse partyFields = validatePartyFields(request);
        if (partyFields != null) {
            return partyFields;
        }
        return validateDictionaryFields(request);
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
        TuxedoResponse partyFields = validatePartyFields(request);
        if (partyFields != null) {
            return partyFields;
        }
        return validateDictionaryFields(request);
    }

    private TuxedoResponse validatePartyFields(TuxedoRequest request) {
        if (tooLong(request, "PAYER_ADDRESS", 256)
            || tooLong(request, "PAYEE_ADDRESS", 256)
            || tooLong(request, "PAYER_BANK_NAME", 128)) {
            return TuxedoResponse.fail("2002", "字段长度超限");
        }
        return null;
    }

    private boolean tooLong(TuxedoRequest request, String field, int maximum) {
        if (!request.fields().containsKey(field)) {
            return false;
        }
        String value = text(request, field, "");
        return value.codePointCount(0, value.length()) > maximum;
    }

    private Map<String, Object> listRecord(Map<String, Object> voucher) {
        Map<String, Object> record = new LinkedHashMap<>();
        for (String field : LIST_FIELDS) {
            Object value = voucher.get(field);
            record.put(field, value == null && "VOUCHER_NO".equals(field) ? "" : value);
        }
        return record;
    }

    private Map<String, Object> reviewSummary(Map<String, Object> voucher) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String field : REVIEW_ACTION_FIELDS) {
            summary.put(field, voucher.get(field));
        }
        return summary;
    }

    private TuxedoResponse validateDictionaryFields(TuxedoRequest request) {
        for (Map.Entry<String, Set<String>> dictionary : DICTIONARY_VALUES.entrySet()) {
            if (!request.fields().containsKey(dictionary.getKey())) {
                continue;
            }
            String value = text(request, dictionary.getKey());
            if (OPTIONAL_DICTIONARY_FIELDS.contains(dictionary.getKey())
                && (value == null || value.isBlank())) {
                continue;
            }
            if (!dictionary.getValue().contains(value)) {
                return TuxedoResponse.fail("2003", "字典值不存在：" + dictionary.getKey());
            }
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
        return RequestSupport.isValidWorkDate(value);
    }

    private TuxedoResponse validateWorkDateFilter(TuxedoRequest request) {
        String startWorkDate = optionalText(request, "START_WORK_DATE");
        String endWorkDate = optionalText(request, "END_WORK_DATE");
        if (!validOptionalWorkDate(startWorkDate)
            || !validOptionalWorkDate(endWorkDate)) {
            return TuxedoResponse.fail("2002", "工作日期格式错误");
        }
        if (startWorkDate != null
            && endWorkDate != null
            && startWorkDate.compareTo(endWorkDate) > 0) {
            return TuxedoResponse.fail("2002", "开始工作日期不能晚于结束工作日期");
        }
        return null;
    }

    private boolean validOptionalWorkDate(String value) {
        return value == null || validWorkDate(value);
    }

    private String optionalText(TuxedoRequest request, String key) {
        String value = text(request, key);
        return value == null || value.isBlank() ? null : value;
    }

    private boolean matchesLowerWorkDate(Map<String, Object> voucher, String startWorkDate) {
        return startWorkDate == null
            || String.valueOf(voucher.get("WORK_DATE")).compareTo(startWorkDate) >= 0;
    }

    private boolean matchesUpperWorkDate(Map<String, Object> voucher, String endWorkDate) {
        return endWorkDate == null
            || String.valueOf(voucher.get("WORK_DATE")).compareTo(endWorkDate) <= 0;
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
