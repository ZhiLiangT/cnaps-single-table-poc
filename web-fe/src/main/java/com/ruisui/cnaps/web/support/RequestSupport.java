package com.ruisui.cnaps.web.support;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestSupport {
    private RequestSupport() {
    }

    public static String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("requestId");
        return requestId == null || requestId.isBlank() ? request.getHeader("X-Request-Id") : requestId;
    }

    public static String operatorNo(HttpServletRequest request) {
        return request.getHeader("operatorNo");
    }

    public static String branchNo(HttpServletRequest request) {
        return request.getHeader("branchNo");
    }

    public static String workDate(HttpServletRequest request) {
        return request.getHeader("workDate");
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
}
