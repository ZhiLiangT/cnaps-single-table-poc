# POST Voucher List API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two voucher-list GET contracts with JSON-body POST contracts while preserving voucher creation and detail behavior.

**Architecture:** Add explicit POST route mappings for the general and review-list queries, and dispatch list POST bodies through the existing validation and Tuxedo mapping pipeline. Reject retired and invalid GET list routes before generic detail routing, then update every repository-owned caller and public contract reference.

**Tech Stack:** Java 17, Servlet API 4, Jackson, Maven, JUnit 5, AssertJ, browser Fetch API, Bash.

## Global Constraints

- `POST /api/cnaps/vouchers` remains voucher creation mapped to `CNAPS5701E`.
- `POST /api/cnaps/vouchers/query` maps to `CNAPS4609Q`.
- `POST /api/cnaps/vouchers/review-list` maps to `CNAPS5702Q`.
- List filters move from URL query parameters to JSON request bodies without changing names or semantics.
- `GET /api/cnaps/vouchers`, `GET /api/cnaps/vouchers/query`, and `GET /api/cnaps/vouchers/review-list` return HTTP 405 without calling Tuxedo.
- `GET /api/cnaps/vouchers/{billId}` remains voucher detail mapped to `CNAPS5702I`.
- Dictionary, bank, health, Tuxedo C, FML32, Jolt metadata, and database behavior do not change.

---

### Task 1: Map the POST list routes

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`

**Interfaces:**
- Consumes: `String TuxedoRequestMapper.serviceName(String method, String path)`.
- Produces: explicit POST mappings for `/api/cnaps/vouchers/query` and `/api/cnaps/vouchers/review-list`; retired GET list mappings are unsupported.

- [ ] **Step 1: Write the failing route-contract tests**

Replace the old list assertions in `mapsHttpOperationToTraditionalTuxedoServiceName` with:

```java
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/query")).isEqualTo("CNAPS4609Q");
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/review-list")).isEqualTo("CNAPS5702Q");
```

Add a focused test proving old GET mappings are gone:

```java
@Test
void rejectsRetiredVoucherListGetMappings() {
    for (String path : List.of(
        "/api/cnaps/vouchers",
        "/api/cnaps/vouchers/query",
        "/api/cnaps/vouchers/review-list"
    )) {
        assertThatThrownBy(() -> mapper.serviceName("GET", path))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported WebFE operation");
    }
}
```

Add imports for `java.util.List` and `assertThatThrownBy`.

- [ ] **Step 2: Run the mapper test to verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest test`

Expected: FAIL because POST `/query` and POST `/review-list` are unsupported and the retired GET mappings still resolve.

- [ ] **Step 3: Implement exact POST mappings**

In `serviceName`, keep create routing exact and add list routes before the generic voucher path:

```java
if ("POST".equals(verb) && "/api/cnaps/vouchers".equals(cleanPath)) {
    return "CNAPS5701E";
}
if ("POST".equals(verb) && "/api/cnaps/vouchers/query".equals(cleanPath)) {
    return "CNAPS4609Q";
}
if ("POST".equals(verb) && "/api/cnaps/vouchers/review-list".equals(cleanPath)) {
    return "CNAPS5702Q";
}
```

Remove the permissive `/api/cnaps/vouchers` branch and the GET review-list branch. Prevent the three list path tokens from falling through to generic detail routing:

```java
if ("GET".equals(verb) && cleanPath.startsWith("/api/cnaps/vouchers/")
    && !cleanPath.equals("/api/cnaps/vouchers/query")
    && !cleanPath.equals("/api/cnaps/vouchers/review-list")) {
    return "CNAPS5702I";
}
```

Keep PUT and action POST mappings unchanged.

- [ ] **Step 4: Run the mapper test to verify GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest test`

Expected: PASS with no failures.

- [ ] **Step 5: Commit the route mapping**

```bash
git add web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java
git commit -m "feat(webfe): map voucher list POST routes"
```

---

### Task 2: Dispatch JSON-body list requests and reject GET

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`

**Interfaces:**
- Consumes: `JsonSupport.readBodyMap(HttpServletRequest)`, `RequestSupport.validateWorkDateFilter(Map<String, Object>)`, and `RequestSupport.apiPath(HttpServletRequest)`.
- Produces: POST list requests forwarded to Tuxedo with canonical fields; retired GET list routes answered with HTTP 405.

- [ ] **Step 1: Convert list behavior tests to POST JSON bodies**

Add a request helper that supplies method, JSON content type, content length, and body input stream:

```java
private HttpServletRequest jsonRequest(String method, String uri, String pathInfo, String json) {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    ServletInputStream input = new ServletInputStream() {
        private final ByteArrayInputStream delegate = new ByteArrayInputStream(bytes);

        @Override public int read() { return delegate.read(); }
        @Override public boolean isFinished() { return delegate.available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener readListener) { }
    };
    return proxy(HttpServletRequest.class, (methodCall, args) -> switch (methodCall.getName()) {
        case "getMethod" -> method;
        case "getRequestURI" -> uri;
        case "getContextPath" -> "";
        case "getPathInfo" -> pathInfo;
        case "getContentType" -> "application/json; charset=UTF-8";
        case "getContentLengthLong" -> (long) bytes.length;
        case "getInputStream" -> input;
        default -> defaultValue(methodCall.getReturnType());
    });
}
```

Import `ByteArrayInputStream`, `StandardCharsets`, `ReadListener`, and `ServletInputStream`.

Change the current list tests to call `servlet.doPost(jsonRequest(...))` for both `/api/cnaps/vouchers/query` and `/api/cnaps/vouchers/review-list`. Pass filters as JSON, for example:

```java
servlet.doPost(
    jsonRequest("POST", path, path.endsWith("query") ? "/query" : "/review-list",
        "{\"startWorkDate\":\"2026-07-10\",\"endWorkDate\":\"2026-07-12\"}"),
    response(new ByteArrayOutputStream())
);
```

Preserve assertions for canonical `START_WORK_DATE`, `END_WORK_DATE`, server-owned context, blank removal, and validation error `2002`.

- [ ] **Step 2: Add a failing 405 test**

```java
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
        servlet.doGet(request(path, path.equals("/api/cnaps/vouchers") ? null : path.substring(19), Map.of()),
            response(new ByteArrayOutputStream(), status));

        assertThat(status.get()).as(path).isEqualTo(405);
        assertThat(captured.get()).as(path).isNull();
    }
}
```

- [ ] **Step 3: Run servlet tests to verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest test`

Expected: FAIL because list behavior still reads GET query parameters, POST list bodies bypass validation, and GET list routes do not return 405.

- [ ] **Step 4: Implement shared list detection and validation**

In `CnapsVoucherServlet`, add:

```java
private static boolean isListPostPath(String apiPath) {
    return "/api/cnaps/vouchers/query".equals(apiPath)
        || "/api/cnaps/vouchers/review-list".equals(apiPath);
}

private static boolean isRejectedGetPath(String apiPath) {
    return "/api/cnaps/vouchers".equals(apiPath)
        || "/api/cnaps/vouchers/query".equals(apiPath)
        || "/api/cnaps/vouchers/review-list".equals(apiPath);
}

private boolean validateListFilters(Map<String, Object> fields, HttpServletResponse response) throws IOException {
    String validationError = RequestSupport.validateWorkDateFilter(fields);
    if (validationError == null) {
        return true;
    }
    JsonSupport.write(response, HttpServletResponse.SC_BAD_REQUEST,
        ApiResponse.fail("2002", validationError));
    return false;
}
```

At the start of `doGet`, return 405 for `isRejectedGetPath(apiPath)`; otherwise preserve the existing detail path flow:

```java
String apiPath = RequestSupport.apiPath(request);
if (isRejectedGetPath(apiPath)) {
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    return;
}
Map<String, Object> fields = new LinkedHashMap<>(RequestSupport.queryParams(request));
RequestSupport.includeBillPath(fields, request.getPathInfo());
callTuxedo(request, response, fields);
```

In `doPost`, read the JSON body once, validate only `isListPostPath(apiPath)`, then preserve bill-path enrichment and call dispatch:

```java
Map<String, Object> fields = JsonSupport.readBodyMap(request);
String apiPath = RequestSupport.apiPath(request);
if (isListPostPath(apiPath) && !validateListFilters(fields, response)) {
    return;
}
RequestSupport.includeBillPath(fields, request.getPathInfo());
callTuxedo(request, response, fields);
```

- [ ] **Step 5: Run focused servlet and mapper tests to verify GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest,TuxedoRequestMapperTest test`

Expected: PASS with no failures.

- [ ] **Step 6: Commit servlet behavior**

```bash
git add web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java
git commit -m "feat(webfe): accept voucher list JSON bodies"
```

---

### Task 3: Update repository-owned callers and API contracts

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `web-fe/src/main/webapp/static/js/cnaps.js`
- Modify: `scripts/smoke-test.sh`
- Modify: `docs/cnaps-frontend-api.md`
- Modify: `docs/cnaps-api-database.md`

**Interfaces:**
- Consumes: the POST routes and JSON request-body contract from Tasks 1 and 2.
- Produces: browser, smoke-test, and documentation examples that no longer call retired GET list routes.

- [ ] **Step 1: Write failing artifact contract assertions**

Update `frontendApiDocumentsTheApprovedHeaderlessV03Contract` and `frontendApiUsesTheDetailedOriginalFormatForAllCurrentEndpoints` to require:

```java
"| `POST /api/cnaps/vouchers/query` | `CNAPS4609Q` |",
"| `POST /api/cnaps/vouchers/review-list` | `CNAPS5702Q` |"
```

and reject the old table entries. Add:

```java
@Test
void voucherListCallersUsePostJsonBodies() throws Exception {
    String script = Files.readString(root.resolve("web-fe/src/main/webapp/static/js/cnaps.js"));
    String smoke = Files.readString(root.resolve("scripts/smoke-test.sh"));

    assertThat(script)
        .contains("fetch(\"api/cnaps/vouchers/query\"", "method: \"POST\"", "JSON.stringify(body)")
        .doesNotContain("api/cnaps/vouchers?");
    assertThat(smoke)
        .contains("/api/cnaps/vouchers/query", "-X POST", "Content-Type: application/json")
        .doesNotContain("/api/cnaps/vouchers?status=");
}
```

- [ ] **Step 2: Run artifact tests to verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: FAIL because JavaScript, smoke script, and documents still use GET list calls.

- [ ] **Step 3: Update browser and smoke callers**

Replace the browser query submission with:

```javascript
const body = Object.fromEntries(new FormData(event.currentTarget).entries());
await show(await fetch("api/cnaps/vouchers/query", {
  method: "POST",
  headers: jsonHeaders,
  body: JSON.stringify(body)
}));
```

Replace the smoke query with a POST JSON call:

```bash
curl -fsS -X POST "$BASE_URL/api/cnaps/vouchers/query" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"status":"10_PENDING_REVIEW"}'
```

- [ ] **Step 4: Update both API documents**

In `docs/cnaps-frontend-api.md`, replace the two list endpoint definitions, `Query 参数` headings, curl samples, and typical-flow calls with POST `/query` or POST `/review-list` plus JSON bodies. State explicitly that the retired GET list routes return 405.

In `docs/cnaps-api-database.md`, replace all legacy GET list signatures and curl examples with the same two POST JSON contracts. Do not alter detail GET examples.

- [ ] **Step 5: Run artifact tests to verify GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: PASS with no failures.

- [ ] **Step 6: Commit callers and documentation**

```bash
git add web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java web-fe/src/main/webapp/static/js/cnaps.js scripts/smoke-test.sh docs/cnaps-frontend-api.md docs/cnaps-api-database.md
git commit -m "docs(api): publish voucher list POST contract"
```

---

### Task 4: Run regression verification

**Files:**
- Verify only; modify a file only if a regression directly caused by Tasks 1-3 is discovered.

**Interfaces:**
- Consumes: all route, servlet, caller, and documentation changes.
- Produces: verified WebFE build and clean patch.

- [ ] **Step 1: Run the complete WebFE test suite**

Run: `mvn -f web-fe/pom.xml test`

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 2: Check shell syntax**

Run: `bash -n scripts/smoke-test.sh`

Expected: no output and exit code 0.

- [ ] **Step 3: Check formatting and scope**

Run: `git diff --check`

Expected: no output.

Run: `git status --short`

Expected: only pre-existing user-owned untracked files, if any; no uncommitted implementation files.
