package com.ruisui.cnaps.web.support;

import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSupportTest {
    @Test
    void suppliesDefaultBusinessContextWhenHeadersAreMissing() {
        HttpServletRequest request = request(Map.of());

        assertThat(RequestSupport.requestId(request)).startsWith("REQ-");
        assertThat(RequestSupport.operatorNo(request)).isEqualTo("77210021");
        assertThat(RequestSupport.branchNo(request)).isEqualTo("772");
        assertThat(RequestSupport.workDate(request)).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void keepsExplicitHeadersWhenProvided() {
        HttpServletRequest request = request(Map.of(
            "X-Request-Id", "REQ-X-1",
            "operatorNo", "90010001",
            "branchNo", "900",
            "workDate", "2026-07-08"
        ));

        assertThat(RequestSupport.requestId(request)).isEqualTo("REQ-X-1");
        assertThat(RequestSupport.operatorNo(request)).isEqualTo("90010001");
        assertThat(RequestSupport.branchNo(request)).isEqualTo("900");
        assertThat(RequestSupport.workDate(request)).isEqualTo("2026-07-08");
    }

    private HttpServletRequest request(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> {
                if ("getHeader".equals(method.getName())) {
                    return headers.get(String.valueOf(args[0]));
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
