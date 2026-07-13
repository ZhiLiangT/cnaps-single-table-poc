package com.ruisui.cnaps.web.servlet;

import com.ruisui.cnaps.web.tuxedo.TuxedoClient;
import com.ruisui.cnaps.web.tuxedo.TuxedoRequest;
import com.ruisui.cnaps.web.tuxedo.TuxedoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BaseJsonServletTest {
    @TempDir
    Path tempDir;

    @Test
    void usesServerGeneratedRequestContextInsteadOfHttpHeaders() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        TuxedoClient client = (serviceName, request) -> {
            captured.set(request);
            return TuxedoResponse.ok("healthy", Map.of());
        };
        ServletContext context = servletContext(Map.of(
            "poc.operatorNo", "SERVER-OP",
            "poc.branchNo", "SERVER-BRANCH",
            "webfe.config", tempDir.resolve("missing-app.properties").toString()
        ));
        context.setAttribute(TuxedoClient.class.getName(), client);

        HealthServlet servlet = new HealthServlet();
        servlet.init(servletConfig(context));
        servlet.doGet(
            request(Map.of(
                "requestId", "CLIENT-REQ",
                "operatorNo", "CLIENT-OP",
                "branchNo", "CLIENT-BRANCH",
                "workDate", "2026-07-08"
            )),
            response(new ByteArrayOutputStream())
        );

        assertThat(captured.get().fields())
            .containsEntry("OPERATOR_NO", "SERVER-OP")
            .containsEntry("BRANCH_NO", "SERVER-BRANCH")
            .doesNotContainValue("CLIENT-OP")
            .doesNotContainValue("CLIENT-BRANCH")
            .doesNotContainValue("CLIENT-REQ")
            .doesNotContainKey("WORK_DATE");
        assertThat(captured.get().fields().get("REQ_ID").toString()).startsWith("REQ-");
    }

    @Test
    void doesNotDefaultDateFilterForVoucherCollections() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        for (String path : List.of("/api/cnaps/vouchers/query", "/api/cnaps/vouchers/review-list")) {
            captured.set(null);
            servlet.doPost(
                jsonRequest(path, path.substring(19), "{}"),
                response(new ByteArrayOutputStream())
            );

            assertThat(captured.get().fields())
                .doesNotContainKeys("BILL_ID", "WORK_DATE", "START_WORK_DATE", "END_WORK_DATE");
        }
    }

    @Test
    void forwardsWorkDateRangeForVoucherCollections() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        for (String path : List.of("/api/cnaps/vouchers/query", "/api/cnaps/vouchers/review-list")) {
            captured.set(null);
            servlet.doPost(
                jsonRequest(
                    path,
                    path.substring(19),
                    "{\"startWorkDate\":\"2026-07-10\",\"endWorkDate\":\"2026-07-12\"}"
                ),
                response(new ByteArrayOutputStream())
            );

            assertThat(captured.get().fields())
                .containsEntry("START_WORK_DATE", "2026-07-10")
                .containsEntry("END_WORK_DATE", "2026-07-12")
                .doesNotContainKeys("BILL_ID", "WORK_DATE");
        }
    }

    @Test
    void removesBlankWorkDateFiltersBeforeCallingTuxedo() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        servlet.doPost(
            jsonRequest(
                "/api/cnaps/vouchers/query",
                "/query",
                "{\"workDate\":\" \",\"startWorkDate\":\"\",\"endWorkDate\":\"  \"}"
            ),
            response(new ByteArrayOutputStream())
        );

        assertThat(captured.get().fields())
            .doesNotContainKeys("WORK_DATE", "START_WORK_DATE", "END_WORK_DATE");
    }

    @Test
    void preservesExplicitWorkDateForVoucherCollection() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        servlet.doPost(
            jsonRequest(
                "/api/cnaps/vouchers/review-list",
                "/review-list",
                Map.of(
                    "requestId", "CLIENT-REQ",
                    "operatorNo", "CLIENT-OP",
                    "branchNo", "CLIENT-BRANCH",
                    "workDate", "HEADER-WORK-DATE"
                ),
                "{\"workDate\":\"2026-07-08\"}"
            ),
            response(new ByteArrayOutputStream())
        );

        assertThat(captured.get().fields())
            .containsEntry("WORK_DATE", "2026-07-08")
            .containsEntry("OPERATOR_NO", "SERVER-OP")
            .containsEntry("BRANCH_NO", "SERVER-BRANCH")
            .doesNotContainValue("CLIENT-REQ")
            .doesNotContainValue("CLIENT-OP")
            .doesNotContainValue("CLIENT-BRANCH")
            .doesNotContainValue("HEADER-WORK-DATE");
    }

    @Test
    void doesNotDefaultWorkDateForVoucherDetail() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        servlet.doGet(
            request("/api/cnaps/vouchers/BILL-1", "/BILL-1", Map.of()),
            response(new ByteArrayOutputStream())
        );

        assertThat(captured.get().fields()).doesNotContainKey("WORK_DATE");
    }

    @Test
    void rejectsInvalidExplicitCollectionWorkDateWithoutCallingTuxedo() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        for (String[] testCase : new String[][] {
            {"/api/cnaps/vouchers/query", "2026/07/10"},
            {"/api/cnaps/vouchers/query", "0000-01-01"},
            {"/api/cnaps/vouchers/review-list", "2026-02-30"},
            {"/api/cnaps/vouchers/review-list", "2026-07-10-extra"}
        }) {
            captured.set(null);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            AtomicInteger status = new AtomicInteger();
            servlet.doPost(
                jsonRequest(
                    testCase[0],
                    testCase[0].substring(19),
                    "{\"workDate\":\"" + testCase[1] + "\"}"
                ),
                response(body, status)
            );

            assertThat(status.get()).as(testCase[0] + " " + testCase[1]).isEqualTo(400);
            assertThat(body.toString(java.nio.charset.StandardCharsets.UTF_8))
                .as(testCase[0] + " " + testCase[1])
                .isEqualTo("{\"respCode\":\"2002\",\"respMsg\":\"工作日期格式错误\",\"data\":null}");
            assertThat(captured.get()).as(testCase[0] + " " + testCase[1]).isNull();
        }
    }

    @Test
    void rejectsInvalidWorkDateRangesWithoutCallingTuxedo() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);
        List<String> invalidFilters = List.of(
            "{\"startWorkDate\":\"2026/07/10\"}",
            "{\"endWorkDate\":\"2026-02-30\"}",
            "{\"workDate\":\"2026-07-10\",\"startWorkDate\":\"2026-07-09\"}",
            "{\"startWorkDate\":\"2026-07-12\",\"endWorkDate\":\"2026-07-10\"}"
        );

        for (String path : List.of("/api/cnaps/vouchers/query", "/api/cnaps/vouchers/review-list")) {
            for (String filter : invalidFilters) {
                captured.set(null);
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                AtomicInteger status = new AtomicInteger();

                servlet.doPost(
                    jsonRequest(path, path.substring(19), filter),
                    response(body, status)
                );

                assertThat(status.get()).as(path + " " + filter).isEqualTo(400);
                assertThat(body.toString(java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"respCode\":\"2002\"");
                assertThat(captured.get()).as(path + " " + filter).isNull();
            }
        }
    }

    @Test
    void rejectsVoucherListGetRequestsWithoutCallingTuxedo() throws Exception {
        AtomicReference<TuxedoRequest> captured = new AtomicReference<>();
        CnapsVoucherServlet servlet = voucherServlet(captured);

        for (String path : List.of(
            "/api/cnaps/vouchers",
            "/api/cnaps/vouchers/query",
            "/api/cnaps/vouchers/review-list"
        )) {
            captured.set(null);
            AtomicInteger status = new AtomicInteger();
            servlet.doGet(
                request(path, path.equals("/api/cnaps/vouchers") ? null : path.substring(19), Map.of()),
                response(new ByteArrayOutputStream(), status)
            );

            assertThat(status.get()).as(path).isEqualTo(405);
            assertThat(captured.get()).as(path).isNull();
        }
    }

    private CnapsVoucherServlet voucherServlet(AtomicReference<TuxedoRequest> captured) throws Exception {
        TuxedoClient client = (serviceName, request) -> {
            captured.set(request);
            return TuxedoResponse.ok("ok", Map.of());
        };
        ServletContext context = servletContext(Map.of(
            "poc.operatorNo", "SERVER-OP",
            "poc.branchNo", "SERVER-BRANCH",
            "webfe.config", tempDir.resolve("missing-app.properties").toString()
        ));
        context.setAttribute(TuxedoClient.class.getName(), client);
        CnapsVoucherServlet servlet = new CnapsVoucherServlet();
        servlet.init(servletConfig(context));
        return servlet;
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

    private HttpServletRequest request(String uri, String pathInfo, Map<String, String[]> parameters) {
        return request(uri, pathInfo, Map.of(), parameters);
    }

    private HttpServletRequest request(
        String uri,
        String pathInfo,
        Map<String, String> headers,
        Map<String, String[]> parameters
    ) {
        return proxy(HttpServletRequest.class, (method, args) -> switch (method.getName()) {
            case "getHeader" -> headers.get(String.valueOf(args[0]));
            case "getMethod" -> "GET";
            case "getRequestURI" -> uri;
            case "getContextPath" -> "";
            case "getPathInfo" -> pathInfo;
            case "getParameterMap" -> parameters;
            default -> defaultValue(method.getReturnType());
        });
    }

    private HttpServletRequest jsonRequest(String uri, String pathInfo, String json) {
        return jsonRequest(uri, pathInfo, Map.of(), json);
    }

    private HttpServletRequest jsonRequest(
        String uri,
        String pathInfo,
        Map<String, String> headers,
        String json
    ) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ServletInputStream input = new ServletInputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(bytes);

            @Override
            public int read() {
                return delegate.read();
            }

            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
        return proxy(HttpServletRequest.class, (method, args) -> switch (method.getName()) {
            case "getHeader" -> headers.get(String.valueOf(args[0]));
            case "getMethod" -> "POST";
            case "getRequestURI" -> uri;
            case "getContextPath" -> "";
            case "getPathInfo" -> pathInfo;
            case "getContentType" -> "application/json; charset=UTF-8";
            case "getContentLengthLong" -> (long) bytes.length;
            case "getInputStream" -> input;
            default -> defaultValue(method.getReturnType());
        });
    }

    private HttpServletResponse response(ByteArrayOutputStream body) {
        return response(body, new AtomicInteger());
    }

    private HttpServletResponse response(ByteArrayOutputStream body, AtomicInteger status) {
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
        return proxy(HttpServletResponse.class, (method, args) -> switch (method.getName()) {
            case "getOutputStream" -> output;
            case "setStatus" -> {
                status.set((Integer) args[0]);
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
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
