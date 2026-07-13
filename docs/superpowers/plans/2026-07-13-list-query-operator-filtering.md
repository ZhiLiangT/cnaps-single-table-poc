# List Query Operator Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent `CNAPS4609Q` and `CNAPS5702Q` Jolt requests from writing the output-only `OPERATOR_NO` field while preserving that field for all other requests and list responses.

**Architecture:** Keep server request context generation unchanged and filter fields at the Jolt transport boundary, where service metadata permissions apply. A small service-aware predicate in `JoltTuxedoClient` decides whether each request field is writable; response field extraction remains untouched.

**Tech Stack:** Java 17, Maven, JUnit 5, AssertJ, Oracle Jolt-compatible reflection boundary.

## Global Constraints

- Only `CNAPS4609Q` and `CNAPS5702Q` omit outbound `OPERATOR_NO`.
- Other services continue sending the server-configured `OPERATOR_NO`.
- List response records continue reading and mapping `OPERATOR_NO`.
- Jolt metadata, Tuxedo C services, database queries, and `4002` error mapping remain unchanged.

---

### Task 1: Filter output-only operator field at the Jolt request boundary

**Files:**
- Modify: `web-fe/src/test/java/bea/jolt/JoltRemoteService.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`

**Interfaces:**
- Consumes: `JoltTuxedoClient.call(String serviceName, TuxedoRequest request)` and `TuxedoRequest.fields()`.
- Produces: private `boolean shouldWriteRequestField(String serviceName, String fieldName)` used only by the Jolt request loop.

- [x] **Step 1: Write the failing transport-boundary tests**

Extend the fake Jolt service with captured request fields and metadata-compatible rejection:

```java
private static Map<String, String> lastStrings = Map.of();

public static void reset() {
    lastStrings = Map.of();
}

public static Map<String, String> lastStrings() {
    return lastStrings;
}

public void setString(String name, String value) {
    if (("CNAPS4609Q".equals(serviceName) || "CNAPS5702Q".equals(serviceName))
        && "OPERATOR_NO".equals(name)) {
        throw new IllegalArgumentException(name);
    }
    strings.put(name, value);
    lastStrings = Map.copyOf(strings);
}
```

Add tests that call both list services with `OPERATOR_NO` and assert success plus absence, then call a non-list service and assert presence:

```java
@Test
void omitsOperatorNoFromPagedVoucherQueryRequests() {
    for (String serviceName : List.of("CNAPS4609Q", "CNAPS5702Q")) {
        JoltRemoteService.reset();
        TuxedoResponse response = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"))
            .call(serviceName, new TuxedoRequest(Map.of("OPERATOR_NO", "77210021", "WORK_DATE", "2026-07-13")));

        assertThat(response.success()).as(serviceName).isTrue();
        assertThat(JoltRemoteService.lastStrings()).as(serviceName)
            .containsEntry("WORK_DATE", "2026-07-13")
            .doesNotContainKey("OPERATOR_NO");
    }
}

@Test
void keepsOperatorNoInNonListRequests() {
    JoltRemoteService.reset();
    TuxedoResponse response = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"))
        .call("CNAPS5702I", new TuxedoRequest(Map.of("OPERATOR_NO", "77210021", "BILL_ID", "BILL-1")));

    assertThat(response.success()).isTrue();
    assertThat(JoltRemoteService.lastStrings()).containsEntry("OPERATOR_NO", "77210021");
}
```

- [x] **Step 2: Run the focused test to verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=JoltTuxedoClientTest test`

Expected: FAIL because both list calls return `4002` with `Tuxedo service call failed: OPERATOR_NO`.

- [x] **Step 3: Implement the minimal service-aware filter**

Add a constant and filter the existing request loop:

```java
private static final List<String> OPERATOR_NO_OUTPUT_ONLY_SERVICES = List.of("CNAPS4609Q", "CNAPS5702Q");

for (Map.Entry<String, Object> field : request.fields().entrySet()) {
    if (shouldWriteRequestField(serviceName, field.getKey())) {
        putField(remoteServiceClass, remoteService, field.getKey(), field.getValue());
    }
}

private boolean shouldWriteRequestField(String serviceName, String fieldName) {
    return !("OPERATOR_NO".equals(fieldName) && OPERATOR_NO_OUTPUT_ONLY_SERVICES.contains(serviceName));
}
```

- [x] **Step 4: Run focused and regression tests to verify GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=JoltTuxedoClientTest,JoltPagedResponseTest,DeploymentArtifactTest test`

Expected: PASS with no test failures.

Run: `mvn -f web-fe/pom.xml test`

Expected: BUILD SUCCESS with all WebFE tests passing.

- [x] **Step 5: Review and commit the implementation**

Run: `git diff --check`

Expected: no output.

Run:

```bash
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java web-fe/src/test/java/bea/jolt/JoltRemoteService.java docs/superpowers/plans/2026-07-13-list-query-operator-filtering.md
git commit -m "fix(jolt): omit operator from list requests"
```
