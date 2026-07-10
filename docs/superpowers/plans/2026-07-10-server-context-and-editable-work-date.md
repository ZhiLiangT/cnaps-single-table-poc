# Server Context and Editable Work Date Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove caller-controlled business common headers, resolve operator and branch on the server, and make `workDate` an explicit create/update/list field throughout WebFE, Jolt, Tuxedo C, Oracle SQL, and API documentation.

**Architecture:** WebFE generates request IDs and resolves fixed operator/branch values through the existing runtime configuration pipeline. Endpoint fields map first and trusted context overlays only request ID, operator, and branch; `workDate` remains an endpoint-owned field. The Java mock and native Tuxedo/OCI paths implement the same create-required/update-optional semantics while preserving voucher IDs and serial numbers.

**Tech Stack:** Java 17, Servlet 4, Maven, JUnit 5, AssertJ, Oracle Tuxedo Jolt/FML32 C sources, OCI SQL, POSIX shell deployment artifacts, Markdown.

## Global Constraints

- No HTTP business common request headers: `requestId`, `operatorNo`, `branchNo`, and `workDate` are not caller-controlled headers.
- WebFE generates `requestId` as `REQ-<timestamp>`.
- Operator configuration precedence remains JVM `webfe.poc.operatorNo`, environment `POC_OPERATOR_NO`, app property `webfe.poc.operatorNo`, servlet context `poc.operatorNo`, then `77210021`.
- Branch configuration precedence is JVM `webfe.poc.branchNo`, environment `POC_BRANCH_NO`, app property `webfe.poc.branchNo`, servlet context `poc.branchNo`, then `772`.
- Create-body `workDate` is required; update-body `workDate` is optional; list-query `workDate` defaults to the WebFE server current date.
- Editing `workDate` must not change `BILL_ID` or `SERIAL_NO`.
- Invalid dates and target-date unique-index conflicts retain the existing `4001` database-error behavior.
- Oracle schema and FML32 field definitions remain unchanged.
- Existing unrelated commits and user changes must be preserved.

---

## File Structure

- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfig.java`: resolve fixed branch configuration.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/BaseJsonServlet.java`: use generated request IDs and configured operator/branch values.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`: remove header parsing and add server request-ID/list-date helpers.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`: remove common `workDate` argument and protect only trusted server context.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`: default missing list-query `workDate`.
- Modify `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`: require create `WORK_DATE` and preserve/update it consistently.
- Create `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`: executable mock create/update behavior tests.
- Modify existing runtime, mapper, servlet, support, deployment, and C source contract tests.
- Modify `tuxedo-server/src/services/cnaps_create.c`, `cnaps_update.c`, and `src/common/db_helper.c`: native work-date semantics.
- Modify `tuxedo/jolt/cnaps_services.bulk`: expose `WORK_DATE` for `CNAPS5701U`.
- Modify `conf/app.properties` and `scripts/configure-tomcat.sh`: deploy fixed branch configuration.
- Modify `docs/cnaps-frontend-api.md`: publish the new HTTP contract.

---

### Task 1: Server-Generated Request and Fixed Branch Context

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfigTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRuntimeConfig.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/BaseJsonServlet.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`

**Interfaces:**
- Produces: `String TuxedoRuntimeConfig.pocBranchNo()` and `String RequestSupport.newRequestId()`.
- Consumes: cached `TuxedoRuntimeConfig` from `TuxedoClientProvider.config(ServletContext)`.

- [ ] **Step 1: Write the failing branch-configuration test**

Extend `TuxedoRuntimeConfigTest` to exercise JVM, environment, app property, servlet context, and default sources:

```java
appProperties.setProperty("webfe.poc.branchNo", "APP-BRANCH");
assertThat(fromSystem.pocBranchNo()).isEqualTo("SYS-BRANCH");
assertThat(fromEnvironment.pocBranchNo()).isEqualTo("ENV-BRANCH");
assertThat(fromProperties.pocBranchNo()).isEqualTo("APP-BRANCH");
assertThat(fromContext.pocBranchNo()).isEqualTo("CTX-BRANCH");
assertThat(defaults.pocBranchNo()).isEqualTo("772");
```

Use exact keys `webfe.poc.branchNo`, `POC_BRANCH_NO`, and `poc.branchNo`.

- [ ] **Step 2: Run branch-config RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRuntimeConfigTest test`

Expected: compilation fails because `pocBranchNo()` does not exist.

- [ ] **Step 3: Implement branch configuration**

Add record component `pocBranchNo`, default constant `772`, initialize `defaults`, and resolve it through the existing `resolveValue` method with the exact keys above.

- [ ] **Step 4: Run branch-config GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRuntimeConfigTest test`

Expected: all runtime configuration tests pass.

- [ ] **Step 5: Write the failing servlet context test**

Update `BaseJsonServletTest` so the servlet context supplies `SERVER-OP` and `SERVER-BRANCH`, while the request sends all four removed headers. Assert the captured Tuxedo fields contain server values, contain a generated `REQ-` ID, and do not contain client header values or `WORK_DATE`:

```java
assertThat(captured.get().fields())
    .containsEntry("OPERATOR_NO", "SERVER-OP")
    .containsEntry("BRANCH_NO", "SERVER-BRANCH")
    .doesNotContainValue("CLIENT-OP")
    .doesNotContainValue("CLIENT-BRANCH")
    .doesNotContainValue("CLIENT-REQ")
    .doesNotContainKey("WORK_DATE");
assertThat(captured.get().fields().get("REQ_ID").toString()).startsWith("REQ-");
```

- [ ] **Step 6: Run servlet RED**

Run: `mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest test`

Expected: FAIL because branch/work date/request ID still come from request headers.

- [ ] **Step 7: Implement server-only context**

Replace `RequestSupport.requestId(HttpServletRequest)` with:

```java
public static String newRequestId() {
    return "REQ-" + System.currentTimeMillis();
}
```

Remove `branchNo(HttpServletRequest)`, `workDate(HttpServletRequest)`, the default-branch constant, and the generic header helper. In `BaseJsonServlet`, cache configured operator and branch during `init`; generate the request ID per call; pass the configured branch into the mapper. Update `RequestSupportTest` to test generated request IDs plus the remaining path/query helpers without any header expectations.

- [ ] **Step 8: Run Task 1 GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRuntimeConfigTest,BaseJsonServletTest,RequestSupportTest test`

Expected: all selected tests pass.

### Task 2: Endpoint-Owned Work Date and Mock Behavior

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/V03TuxedoContractTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java`
- Create: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`

**Interfaces:**
- Consumes: server request/operator/branch context from Task 1.
- Produces: `TuxedoRequestMapper.from(String requestId, String operatorNo, String branchNo, Map<String, ?> fields)` and `RequestSupport.includeDefaultWorkDate(Map<String,Object>)`.

- [ ] **Step 1: Write failing mapper tests**

Change mapper tests to the four-argument API and add assertions that an endpoint `workDate` becomes `WORK_DATE`, while hostile `requestId`, `operatorNo`, and `branchNo` map entries cannot override trusted values:

```java
TuxedoRequest request = mapper.from("SERVER-REQ", "SERVER-OP", "SERVER-BRANCH", Map.of(
    "requestId", "CLIENT-REQ",
    "operatorNo", "CLIENT-OP",
    "branchNo", "CLIENT-BRANCH",
    "workDate", "2026-07-10"
));
assertThat(request.fields())
    .containsEntry("REQ_ID", "SERVER-REQ")
    .containsEntry("OPERATOR_NO", "SERVER-OP")
    .containsEntry("BRANCH_NO", "SERVER-BRANCH")
    .containsEntry("WORK_DATE", "2026-07-10");
```

- [ ] **Step 2: Run mapper RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest,V03TuxedoContractTest test`

Expected: compilation fails because the mapper still requires common `workDate`.

- [ ] **Step 3: Implement mapper signature and callers**

Remove the `workDate` argument and trusted `WORK_DATE` overlay. Keep trusted overlays for `REQUEST_ID`, `REQ_ID`, `OPERATOR_NO`, and `BRANCH_NO`. Update `BaseJsonServlet`, `V03TuxedoContractTest`, and all call sites.

- [ ] **Step 4: Write failing list-date helper tests**

Add tests that `includeDefaultWorkDate` inserts `LocalDate.now().toString()` when `workDate` is missing or blank and preserves an explicit value.

- [ ] **Step 5: Run list-date RED**

Run: `mvn -f web-fe/pom.xml -Dtest=RequestSupportTest test`

Expected: compilation fails because `includeDefaultWorkDate` does not exist.

- [ ] **Step 6: Implement list-date defaulting**

Implement the helper and call it from `CnapsVoucherServlet.doGet` only when `RequestSupport.apiPath(request)` equals `/api/cnaps/vouchers` or `/api/cnaps/vouchers/review-list`. An explicit query value must remain unchanged; detail requests must not gain `WORK_DATE`.

- [ ] **Step 7: Write failing mock work-date tests**

Create `MockTuxedoClientWorkDateTest` with three behaviors:

```java
assertThat(client.call("CNAPS5701E", createWithoutWorkDate).respCode()).isEqualTo("2001");
assertThat(created.fields()).containsEntry("WORK_DATE", "2026-07-10");
assertThat(updated.fields())
    .containsEntry("WORK_DATE", "2026-07-11")
    .containsEntry("BILL_ID", originalBillId)
    .containsEntry("SERIAL_NO", originalSerialNo);
```

Build requests with trusted `OPERATOR_NO` and `BRANCH_NO`; include required payee fields and amount. Create first, then update the returned `BILL_ID` with a new `WORK_DATE`.

- [ ] **Step 8: Run mock RED**

Run: `mvn -f web-fe/pom.xml -Dtest=MockTuxedoClientWorkDateTest test`

Expected: missing-work-date creation currently succeeds, so the first behavior fails.

- [ ] **Step 9: Implement mock semantics**

Require nonblank `WORK_DATE` during create, remove its `LocalDate.now()` fallback, and keep update behavior limited to supplied fields so absent `WORK_DATE` remains unchanged. Do not mutate `BILL_ID` or `SERIAL_NO` on update.

- [ ] **Step 10: Run Task 2 GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest,V03TuxedoContractTest,RequestSupportTest,MockTuxedoClientWorkDateTest,BaseJsonServletTest test`

Expected: all selected tests pass.

### Task 3: Native Tuxedo and Oracle Work-Date Update

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `tuxedo-server/src/services/cnaps_create.c`
- Modify: `tuxedo-server/src/services/cnaps_update.c`
- Modify: `tuxedo-server/src/common/db_helper.c`
- Modify: `tuxedo/jolt/cnaps_services.bulk`

**Interfaces:**
- Consumes: canonical FML32 `WORK_DATE`, existing `cnaps_voucher_row.work_date`, and existing OCI `:work_date` binding.
- Produces: create-required and update-optional native work-date behavior.

- [ ] **Step 1: Write failing native source/metadata contract tests**

Add assertions that:

```java
assertThat(createSource)
    .contains("row.work_date[0] == '\\0'")
    .doesNotContain("row->work_date, sizeof(row->work_date), \"2026-07-09\"");
assertThat(updateSource)
    .contains("CNAPS_F_WORK_DATE, row.work_date");
assertThat(dbHelper)
    .contains("WORK_DATE=COALESCE(TO_DATE(:work_date, 'YYYY-MM-DD'), WORK_DATE)");
```

In `DeploymentArtifactTest`, isolate the `CNAPS5701U` metadata block and assert it contains `param=WORK_DATE`, `type=string`, and `access=inout`.

- [ ] **Step 2: Run native-contract RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest,DeploymentArtifactTest test`

Expected: FAIL because create has a hard-coded fallback, update ignores the field, SQL does not assign it, and update metadata omits it.

- [ ] **Step 3: Implement C create/update behavior**

In `cnaps_create.c`, read `WORK_DATE` with an empty fallback and include it in the required-field check that returns `2001`. In `cnaps_update.c`, copy `CNAPS_F_WORK_DATE` into `row.work_date` before database update.

- [ ] **Step 4: Implement OCI and Jolt metadata changes**

Add conditional `WORK_DATE` assignment to `db_update_voucher`. Add `WORK_DATE` as an `inout` string inside the `CNAPS5701U` block of `tuxedo/jolt/cnaps_services.bulk`. Keep existing metadata generation fields unchanged because `scripts/load-jolt-metadata.sh` already adds `WORK_DATE`.

- [ ] **Step 5: Run Task 3 GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest,DeploymentArtifactTest test`

Expected: all selected tests pass.

### Task 4: Deploy Fixed Branch and Publish the HTTP Contract

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `conf/app.properties`
- Modify: `scripts/configure-tomcat.sh`
- Modify: `docs/cnaps-frontend-api.md`

**Interfaces:**
- Consumes: Task 1 runtime keys and Tasks 2-3 work-date semantics.
- Produces: deployable branch configuration and aligned frontend API documentation.

- [ ] **Step 1: Write failing deployment/documentation assertions**

Assert that app properties and Tomcat configuration contain `webfe.poc.branchNo=772`, `POC_BRANCH_NO`, and `-Dwebfe.poc.branchNo=`. Assert the frontend API document:

```java
.contains("POC_OPERATOR_NO", "POC_BRANCH_NO")
.contains("workDate", "创建时必填", "修改时可选")
.doesNotContain("### 1.4 公共请求头")
.doesNotContain("-H \"requestId:", "-H \"operatorNo:", "-H \"branchNo:", "-H \"workDate:");
```

- [ ] **Step 2: Run documentation RED**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: FAIL because fixed branch deployment and the new API contract are not documented.

- [ ] **Step 3: Implement deployable branch configuration**

Add `webfe.poc.branchNo=772` to `conf/app.properties`. Extend `scripts/configure-tomcat.sh` with `POC_BRANCH_NO=${POC_BRANCH_NO:-772}`, persist it in sysconfig, include `-Dwebfe.poc.branchNo=$POC_BRANCH_NO` in `JAVA_OPTS`, and report the configured branch in the completion message.

- [ ] **Step 4: Rewrite affected API documentation sections**

Remove the public-header section and all four business-header curl examples. Add server-context documentation for generated request ID and configured operator/branch. Update create fields/example so `workDate` is required, update fields/example so it is optional and editable without changing `billId`/`serialNo`, and list/review-list query tables so `workDate` is optional with current-date default. Preserve unrelated endpoint details.

- [ ] **Step 5: Run Task 4 GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: all deployment/documentation contract tests pass.

### Task 5: Full Verification

**Files:**
- Read: all modified files.

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: final evidence that WebFE, mock, native contracts, deployment, and documentation agree.

- [ ] **Step 1: Run the complete WebFE suite**

Run: `mvn -f web-fe/pom.xml test`

Expected: BUILD SUCCESS with no failing tests.

- [ ] **Step 2: Search for stale business-header parsing/examples**

Run: `rg -n 'getHeader\("(requestId|X-Request-Id|operatorNo|branchNo|workDate)"\)|-H "(requestId|operatorNo|branchNo|workDate):|### 1\.4 公共请求头' web-fe/src/main docs/cnaps-frontend-api.md`

Expected: no matches.

- [ ] **Step 3: Verify cross-layer work-date wiring**

Run: `rg -n 'WORK_DATE|workDate|POC_BRANCH_NO|webfe\.poc\.branchNo' web-fe tuxedo tuxedo-server conf scripts docs/cnaps-frontend-api.md`

Expected: create/update/list fields and branch configuration appear in their intended layers.

- [ ] **Step 4: Review final worktree integrity**

Run: `git diff --check && git status --short && git log --oneline -12`

Expected: no whitespace errors, no unrelated uncommitted changes, and task commits are present.
