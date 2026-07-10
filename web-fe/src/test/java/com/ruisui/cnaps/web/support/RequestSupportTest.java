package com.ruisui.cnaps.web.support;

import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSupportTest {
    @Test
    void generatesRequestIdsWithServerPrefix() {
        assertThat(RequestSupport.newRequestId()).startsWith("REQ-");
    }

    @Test
    void extractsApiPathOutsideTheContextPath() {
        HttpServletRequest request = request("/web-fe/api/bills", "/web-fe", Map.of());

        assertThat(RequestSupport.apiPath(request)).isEqualTo("/api/bills");
    }

    @Test
    void keepsFirstValueForEachQueryParameter() {
        HttpServletRequest request = request(
            "/api/bills",
            "",
            Map.of("status", new String[] {"OPEN", "CLOSED"}, "empty", new String[0])
        );

        assertThat(RequestSupport.queryParams(request)).containsExactly(Map.entry("status", "OPEN"));
    }

    @Test
    void includesBillIdFromPath() {
        Map<String, Object> fields = new LinkedHashMap<>();

        RequestSupport.includeBillPath(fields, "/BILL-1/details");

        assertThat(fields).containsExactly(Map.entry("billId", "BILL-1"));
    }

    @Test
    void includesCurrentWorkDateWhenMissing() {
        Map<String, Object> fields = new LinkedHashMap<>();

        RequestSupport.includeDefaultWorkDate(fields);

        assertThat(fields).containsEntry("workDate", LocalDate.now().toString());
    }

    @Test
    void includesCurrentWorkDateWhenBlank() {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("workDate", "  "));

        RequestSupport.includeDefaultWorkDate(fields);

        assertThat(fields).containsEntry("workDate", LocalDate.now().toString());
    }

    @Test
    void preservesExplicitWorkDate() {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("workDate", "2026-07-08"));

        RequestSupport.includeDefaultWorkDate(fields);

        assertThat(fields).containsEntry("workDate", "2026-07-08");
    }

    private HttpServletRequest request(String uri, String contextPath, Map<String, String[]> parameters) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> {
                if ("getRequestURI".equals(method.getName())) {
                    return uri;
                }
                if ("getContextPath".equals(method.getName())) {
                    return contextPath;
                }
                if ("getParameterMap".equals(method.getName())) {
                    return parameters;
                }
                return switch (method.getReturnType().getName()) {
                    case "boolean" -> false;
                    case "byte", "short", "int", "long", "float", "double" -> 0;
                    default -> null;
                };
            }
        );
    }
}
