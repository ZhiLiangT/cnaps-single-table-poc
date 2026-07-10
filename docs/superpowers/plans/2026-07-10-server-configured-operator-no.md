# Server-Configured Operator Number Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `operatorNo` from the caller-controlled HTTP contract and supply `OPERATOR_NO` to Tuxedo from WebFE server configuration.

**Architecture:** Extend the existing `TuxedoRuntimeConfig` resolution pipeline with `pocOperatorNo`, cache that configuration in the servlet context, and let `BaseJsonServlet` use the configured value instead of an HTTP header. Keep the downstream Tuxedo and Oracle contracts unchanged, and document/deploy the fixed POC operator through existing configuration artifacts.

**Tech Stack:** Java 17, Servlet 4, Maven, JUnit 5, AssertJ, POSIX shell, Markdown.

## Global Constraints

- Runtime precedence is JVM property `webfe.poc.operatorNo`, environment variable `POC_OPERATOR_NO`, `conf/app.properties`, servlet context parameter `poc.operatorNo`, then default `77210021`.
- Incoming HTTP `operatorNo` must not affect the Tuxedo request.
- `TuxedoRequestMapper` must continue producing `OPERATOR_NO`.
- Oracle schema, Jolt metadata, FML32 fields, and C service persistence logic remain unchanged.
- Existing uncommitted user changes must be preserved.

---

## File Structure

- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfig.java`: resolve and expose the fixed POC operator.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoClientProvider.java`: cache and expose one runtime configuration instance.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/BaseJsonServlet.java`: use the configured operator for every Tuxedo request.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`: remove caller-controlled operator parsing.
- Modify `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfigTest.java`: verify operator configuration precedence and default.
- Create `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`: verify an HTTP operator header is ignored.
- Modify `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java`: remove obsolete operator-header expectations.
- Modify `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`: verify deployable operator configuration and API documentation.
- Modify `conf/app.properties`: provide the explicit POC operator property.
- Modify `scripts/configure-tomcat.sh`: persist `POC_OPERATOR_NO` and its JVM property.
- Modify `docs/cnaps-frontend-api.md`: remove the public header and explain server-controlled operator context.

---

### Task 1: Resolve the Fixed Operator from Runtime Configuration

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfigTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfig.java`

**Interfaces:**
- Consumes: existing `TuxedoRuntimeConfig.resolve(Properties, Function, Function, Function)` precedence mechanism.
- Produces: `String TuxedoRuntimeConfig.pocOperatorNo()`.

- [ ] **Step 1: Write the failing configuration test**

Add a test that resolves all five sources in priority order and asserts the default:

```java
@Test
void resolvesPocOperatorNumberFromRuntimeSourcesAndDefault() {
    Properties appProperties = new Properties();
    appProperties.setProperty("webfe.poc.operatorNo", "APP-OP");

    TuxedoRuntimeConfig fromSystem = TuxedoRuntimeConfig.resolve(
        appProperties,
        key -> "webfe.poc.operatorNo".equals(key) ? "SYS-OP" : null,
        env(Map.of("POC_OPERATOR_NO", "ENV-OP")),
        key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null
    );
    assertThat(fromSystem.pocOperatorNo()).isEqualTo("SYS-OP");

    TuxedoRuntimeConfig fromEnvironment = TuxedoRuntimeConfig.resolve(
        appProperties, key -> null, env(Map.of("POC_OPERATOR_NO", "ENV-OP")),
        key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null);
    assertThat(fromEnvironment.pocOperatorNo()).isEqualTo("ENV-OP");

    TuxedoRuntimeConfig fromProperties = TuxedoRuntimeConfig.resolve(
        appProperties, key -> null, env(Map.of()),
        key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null);
    assertThat(fromProperties.pocOperatorNo()).isEqualTo("APP-OP");

    TuxedoRuntimeConfig fromContext = TuxedoRuntimeConfig.resolve(
        new Properties(), key -> null, env(Map.of()),
        key -> "poc.operatorNo".equals(key) ? "CTX-OP" : null);
    assertThat(fromContext.pocOperatorNo()).isEqualTo("CTX-OP");

    TuxedoRuntimeConfig defaults = TuxedoRuntimeConfig.resolve(
        new Properties(), key -> null, env(Map.of()), key -> null);
    assertThat(defaults.pocOperatorNo()).isEqualTo("77210021");
}
```

- [ ] **Step 2: Run RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRuntimeConfigTest test`

Expected: compilation fails because `pocOperatorNo()` does not exist.

- [ ] **Step 3: Implement minimal runtime configuration support**

Add `pocOperatorNo` to the record, define `DEFAULT_POC_OPERATOR_NO = "77210021"`, populate it in `defaults`, and resolve it with:

```java
resolveValue(
    "webfe.poc.operatorNo",
    "POC_OPERATOR_NO",
    "poc.operatorNo",
    DEFAULT_POC_OPERATOR_NO,
    appProperties,
    systemProperty,
    environment,
    contextParameter
)
```

- [ ] **Step 4: Run GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRuntimeConfigTest test`

Expected: all `TuxedoRuntimeConfigTest` tests pass.

### Task 2: Ignore the HTTP Operator Header in Servlet Requests

**Files:**
- Create: `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoClientProvider.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/BaseJsonServlet.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java`

**Interfaces:**
- Consumes: `TuxedoRuntimeConfig.pocOperatorNo()` from Task 1.
- Produces: `TuxedoClientProvider.config(ServletContext)` and servlet requests whose `OPERATOR_NO` always comes from server configuration.

- [ ] **Step 1: Write the failing servlet test**

Create a same-package servlet test with lightweight servlet proxies. Configure servlet context parameter `poc.operatorNo=SERVER-OP`, send header `operatorNo=CLIENT-OP`, inject a capturing `TuxedoClient`, invoke `/api/health`, and assert:

```java
assertThat(captured.get().fields())
    .containsEntry("OPERATOR_NO", "SERVER-OP")
    .doesNotContainValue("CLIENT-OP");
```

- [ ] **Step 2: Run RED**

Run: `mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest test`

Expected: FAIL because the current servlet reads `CLIENT-OP` from the request header.

- [ ] **Step 3: Implement the server-controlled request flow**

Add a cached configuration accessor to `TuxedoClientProvider`:

```java
public static TuxedoRuntimeConfig config(ServletContext context) {
    Object existing = context.getAttribute(CONFIG_ATTRIBUTE_NAME);
    if (existing instanceof TuxedoRuntimeConfig config) {
        return config;
    }
    TuxedoRuntimeConfig created = TuxedoRuntimeConfig.from(context);
    context.setAttribute(CONFIG_ATTRIBUTE_NAME, created);
    return created;
}
```

Make `get(ServletContext)` call `config(context)`. In `BaseJsonServlet.init()`, cache `config.pocOperatorNo()` and pass that value to `requestMapper.from(...)`. Remove `RequestSupport.operatorNo(HttpServletRequest)` and `DEFAULT_OPERATOR_NO`, then remove obsolete assertions that explicit request headers override the operator.

- [ ] **Step 4: Run GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest,RequestSupportTest,TuxedoRequestMapperTest test`

Expected: all selected tests pass, and the existing mapper test still confirms `OPERATOR_NO` is sent to Tuxedo.

### Task 3: Package and Document the Server Operator Configuration

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `conf/app.properties`
- Modify: `scripts/configure-tomcat.sh`
- Modify: `docs/cnaps-frontend-api.md`

**Interfaces:**
- Consumes: runtime keys defined in Task 1.
- Produces: deployable `POC_OPERATOR_NO` configuration and a frontend API contract without an `operatorNo` request header.

- [ ] **Step 1: Write failing deployment/documentation contract tests**

Add one test that reads the three artifacts and asserts:

```java
assertThat(Files.readString(root.resolve("conf/app.properties")))
    .contains("webfe.poc.operatorNo=77210021");
assertThat(Files.readString(root.resolve("scripts/configure-tomcat.sh")))
    .contains("POC_OPERATOR_NO", "-Dwebfe.poc.operatorNo=");
assertThat(Files.readString(root.resolve("docs/cnaps-frontend-api.md")))
    .contains("POC_OPERATOR_NO", "webfe.poc.operatorNo")
    .doesNotContain("| `operatorNo`", "-H \"operatorNo:");
```

- [ ] **Step 2: Run RED**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: FAIL because the configuration and documentation artifacts do not yet satisfy the contract.

- [ ] **Step 3: Implement deployment configuration**

Add `webfe.poc.operatorNo=77210021` to `conf/app.properties`. In `scripts/configure-tomcat.sh`, default `POC_OPERATOR_NO`, write it into the generated Tomcat sysconfig block, add `-Dwebfe.poc.operatorNo=$POC_OPERATOR_NO` to `JAVA_OPTS`, and include the selected value in the completion message.

- [ ] **Step 4: Update the frontend API document**

Remove the `operatorNo` row from the public request-header table and the `operatorNo` curl header. Add a server-context paragraph stating that WebFE resolves the operator from `webfe.poc.operatorNo` / `POC_OPERATOR_NO`, defaults to `77210021`, and still forwards `OPERATOR_NO` to Tuxedo for persistence and audit fields.

- [ ] **Step 5: Run GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: all deployment artifact tests pass.

### Task 4: Full Verification

**Files:**
- Read: all modified files.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: evidence that behavior, documentation, and deployment artifacts agree.

- [ ] **Step 1: Run the complete WebFE suite**

Run: `mvn -f web-fe/pom.xml test`

Expected: BUILD SUCCESS with no failing tests.

- [ ] **Step 2: Run shell syntax validation**

Run: `sh -n scripts/configure-tomcat.sh`

Expected: exit code 0 and no output.

- [ ] **Step 3: Search for stale public-header usage**

Run: `rg -n 'getHeader\("operatorNo"\)|-H "operatorNo:|\| `operatorNo`' web-fe/src/main docs/cnaps-frontend-api.md`

Expected: no matches.

- [ ] **Step 4: Review the final diff and worktree state**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; the intended implementation and previously untracked `docs/cnaps-frontend-api.md` are listed without unrelated changes.
