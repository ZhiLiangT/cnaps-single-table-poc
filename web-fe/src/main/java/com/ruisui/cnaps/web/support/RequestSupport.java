package com.ruisui.cnaps.web.support;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestSupport {
    private static final String DEFAULT_BRANCH_NO = "772";

    private RequestSupport() {
    }

    public static String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("requestId");
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        String fallback = request.getHeader("X-Request-Id");
        return fallback == null || fallback.isBlank() ? "REQ-" + System.currentTimeMillis() : fallback;
    }

    public static String branchNo(HttpServletRequest request) {
        return headerOrDefault(request, "branchNo", DEFAULT_BRANCH_NO);
    }

    public static String workDate(HttpServletRequest request) {
        return headerOrDefault(request, "workDate", LocalDate.now().toString());
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

    private static String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
