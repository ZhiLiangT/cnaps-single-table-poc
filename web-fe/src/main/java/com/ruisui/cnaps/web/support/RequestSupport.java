package com.ruisui.cnaps.web.support;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestSupport {
    private RequestSupport() {
    }

    public static String newRequestId() {
        return "REQ-" + System.currentTimeMillis();
    }

    public static String apiPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String uri = request.getRequestURI();
        return uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    }

    public static Map<String, Object> queryParams(HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    public static void includeBillPath(Map<String, Object> fields, String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return;
        }
        String[] segments = pathInfo.substring(1).split("/");
        if (segments.length > 0 && !segments[0].isBlank()) {
            fields.put("billId", segments[0]);
        }
    }

    public static String reviewActionBillId(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return null;
        }
        String[] segments = pathInfo.substring(1).split("/");
        if (segments.length == 2
            && !segments[0].isBlank()
            && ("review-pass".equals(segments[1]) || "review-return".equals(segments[1]))) {
            return segments[0];
        }
        return null;
    }

    public static String validateWorkDateFilter(Map<String, Object> fields) {
        if (fields.containsKey("workDate")) {
            return "列表查询不支持 workDate，请使用 startWorkDate/endWorkDate";
        }
        removeBlank(fields, "startWorkDate");
        removeBlank(fields, "endWorkDate");
        String startWorkDate = text(fields.get("startWorkDate"));
        String endWorkDate = text(fields.get("endWorkDate"));
        if (!isValidOptionalWorkDate(startWorkDate)
            || !isValidOptionalWorkDate(endWorkDate)) {
            return "工作日期格式错误";
        }
        if (startWorkDate != null
            && endWorkDate != null
            && LocalDate.parse(startWorkDate).isAfter(LocalDate.parse(endWorkDate))) {
            return "开始工作日期不能晚于结束工作日期";
        }
        return null;
    }

    public static String validateReviewPageFilter(Map<String, Object> fields) {
        String pageNoError = validateOptionalPositiveInt(fields.get("pageNo"), "pageNo", Integer.MAX_VALUE);
        if (pageNoError != null) {
            return pageNoError;
        }
        return validateOptionalPositiveInt(fields.get("pageSize"), "pageSize", 100);
    }

    public static boolean isValidWorkDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return false;
        }
        try {
            LocalDate parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return parsed.getYear() >= 1 && parsed.getYear() <= 9999;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private static boolean isValidOptionalWorkDate(String value) {
        return value == null || isValidWorkDate(value);
    }

    private static String validateOptionalPositiveInt(Object value, String fieldName, int maximum) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            long parsed = number.longValue();
            return raw == parsed && parsed >= 1L && parsed <= maximum
                ? null
                : fieldName + " 必须为有效范围内的正整数";
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed >= 1L && parsed <= maximum
                ? null
                : fieldName + " 必须为有效范围内的正整数";
        } catch (NumberFormatException ex) {
            return fieldName + " 必须为有效范围内的正整数";
        }
    }

    private static void removeBlank(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value != null && String.valueOf(value).isBlank()) {
            fields.remove(key);
        }
    }
}
