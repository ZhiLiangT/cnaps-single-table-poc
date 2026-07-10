package com.ruisui.cnaps.web.support;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
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

    public static void includeDefaultWorkDate(Map<String, Object> fields) {
        Object workDate = fields.get("workDate");
        if (workDate == null || String.valueOf(workDate).isBlank()) {
            fields.put("workDate", LocalDate.now().toString());
        }
    }
}
