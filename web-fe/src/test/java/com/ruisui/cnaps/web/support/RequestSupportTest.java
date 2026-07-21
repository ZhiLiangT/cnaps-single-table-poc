package com.ruisui.cnaps.web.support;

import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
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
    void extractsReviewActionBillIdOnlyFromStrictActionPaths() {
        assertThat(RequestSupport.reviewActionBillId("/BILL-1/review-pass")).isEqualTo("BILL-1");
        assertThat(RequestSupport.reviewActionBillId("/BILL-1/review-return")).isEqualTo("BILL-1");
        assertThat(RequestSupport.reviewActionBillId("/review-pass")).isNull();
        assertThat(RequestSupport.reviewActionBillId("/BILL-1/review-pass/extra")).isNull();
    }

    @Test
    void acceptsMissingWorkDateFiltersWithoutAddingDefaults() {
        Map<String, Object> fields = new LinkedHashMap<>();

        String error = RequestSupport.validateWorkDateFilter(fields);

        assertThat(error).isNull();
        assertThat(fields).isEmpty();
    }

    @Test
    void rejectsRetiredWorkDateKeyEvenWhenBlankOrNull() {
        for (Object value : java.util.Arrays.asList("2026-07-10", " ", "", null)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("workDate", value);

            assertThat(RequestSupport.validateWorkDateFilter(fields))
                .as(String.valueOf(value))
                .isEqualTo("列表查询不支持 workDate，请使用 startWorkDate/endWorkDate");
        }
    }

    @Test
    void removesBlankRangeBoundsWithoutAddingDefaults() {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of(
            "startWorkDate", " ",
            "endWorkDate", ""
        ));

        String error = RequestSupport.validateWorkDateFilter(fields);

        assertThat(error).isNull();
        assertThat(fields).isEmpty();
    }

    @Test
    void acceptsInclusiveOrderedWorkDateRange() {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of(
            "startWorkDate", "2026-07-08",
            "endWorkDate", "2026-07-08"
        ));

        String error = RequestSupport.validateWorkDateFilter(fields);

        assertThat(error).isNull();
    }

    @Test
    void validatesOnlyFourDigitPositiveCalendarYears() {
        assertThat(RequestSupport.isValidWorkDate("0001-01-01")).isTrue();
        assertThat(RequestSupport.isValidWorkDate("9999-12-31")).isTrue();
        assertThat(RequestSupport.isValidWorkDate("0000-01-01")).isFalse();
        assertThat(RequestSupport.isValidWorkDate("2026-07-10-extra")).isFalse();
    }

    @Test
    void validatesReviewPageBounds() {
        assertThat(RequestSupport.validateReviewPageFilter(new LinkedHashMap<>(Map.of(
            "pageNo", 1,
            "pageSize", 100
        )))).isNull();
        assertThat(RequestSupport.validateReviewPageFilter(new LinkedHashMap<>(Map.of("pageNo", 0))))
            .contains("pageNo");
        assertThat(RequestSupport.validateReviewPageFilter(new LinkedHashMap<>(Map.of("pageSize", 101))))
            .contains("pageSize");
        assertThat(RequestSupport.validateReviewPageFilter(new LinkedHashMap<>(Map.of("pageSize", "1.5"))))
            .contains("pageSize");
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
