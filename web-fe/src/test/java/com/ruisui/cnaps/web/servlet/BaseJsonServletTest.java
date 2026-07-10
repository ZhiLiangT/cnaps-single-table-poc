package com.ruisui.cnaps.web.servlet;

import com.ruisui.cnaps.web.tuxedo.TuxedoClient;
import com.ruisui.cnaps.web.tuxedo.TuxedoRequest;
import com.ruisui.cnaps.web.tuxedo.TuxedoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BaseJsonServletTest {
    @TempDir
    Path tempDir;

    @Test
    void usesServerConfiguredOperatorInsteadOfHttpHeader() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        TuxedoClient client = (serviceName, request) -> {
            captured.set(request);
            return TuxedoResponse.ok("healthy", Map.of());
        };
        ServletContext context = servletContext(Map.of(
            "poc.operatorNo", "SERVER-OP",
            "webfe.config", tempDir.resolve("missing-app.properties").toString()
        ));
        context.setAttribute(TuxedoClient.class.getName(), client);

        HealthServlet servlet = new HealthServlet();
        servlet.init(servletConfig(context));
        servlet.doGet(
            request(Map.of("operatorNo", "CLIENT-OP")),
            response(new ByteArrayOutputStream())
        );

        assertThat(captured.get().fields())
            .containsEntry("OPERATOR_NO", "SERVER-OP")
            .doesNotContainValue("CLIENT-OP");
    }

    private ServletContext servletContext(Map<String, String> initParameters) {
        Map<String, Object> attributes = new HashMap<>();
        return proxy(ServletContext.class, (method, args) -> switch (method.getName()) {
            case "getAttribute" -> attributes.get(String.valueOf(args[0]));
            case "setAttribute" -> {
                attributes.put(String.valueOf(args[0]), args[1]);
                yield null;
            }
            case "getInitParameter" -> initParameters.get(String.valueOf(args[0]));
            default -> defaultValue(method.getReturnType());
        });
    }

    private ServletConfig servletConfig(ServletContext context) {
        return proxy(ServletConfig.class, (method, args) -> switch (method.getName()) {
            case "getServletContext" -> context;
            case "getServletName" -> "health";
            default -> defaultValue(method.getReturnType());
        });
    }

    private HttpServletRequest request(Map<String, String> headers) {
        return proxy(HttpServletRequest.class, (method, args) -> switch (method.getName()) {
            case "getHeader" -> headers.get(String.valueOf(args[0]));
            case "getMethod" -> "GET";
            case "getRequestURI" -> "/api/health";
            case "getContextPath" -> "";
            default -> defaultValue(method.getReturnType());
        });
    }

    private HttpServletResponse response(ByteArrayOutputStream body) {
        ServletOutputStream output = new ServletOutputStream() {
            @Override
            public void write(int value) {
                body.write(value);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }
        };
        return proxy(HttpServletResponse.class, (method, args) ->
            "getOutputStream".equals(method.getName()) ? output : defaultValue(method.getReturnType())
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> invocation.invoke(method, args)
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws IOException;
    }
}
