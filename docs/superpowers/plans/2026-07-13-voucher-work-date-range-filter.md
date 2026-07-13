# Voucher Work Date Range Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add inclusive `startWorkDate` and `endWorkDate` filters to both voucher list APIs while retaining exact `workDate` filtering and making an unfiltered request return all dates.

**Architecture:** WebFE validates the public query contract and maps camel-case parameters to FML field names. Both Tuxedo query services independently validate the same contract and pass three optional date values to the shared Oracle query function; the mock client mirrors those semantics for local and contract tests.

**Tech Stack:** Java 17, Servlet API, JUnit 5, AssertJ, C, Oracle OCI SQL, Tuxedo FML32, Maven.

## Global Constraints

- Apply the change to both `GET /api/cnaps/vouchers` and `GET /api/cnaps/vouchers/review-list`.
- Preserve exact `workDate` filtering for existing callers.
- Use `startWorkDate` and `endWorkDate` as inclusive bounds and allow either bound independently.
- Reject `workDate` combined with either range bound using HTTP 400 / business code `2002`.
- Reject invalid calendar dates and `startWorkDate > endWorkDate` using HTTP 400 / business code `2002`.
- When all three date parameters are absent or blank, do not add a date predicate value; return all dates matching other filters.
- Do not change database schema, response fields, pagination, sorting, deleted-record behavior, or review-list status behavior.

---

### Task 1: WebFE query contract and request mapping

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`

**Interfaces:**
- Consumes: servlet query parameters named `workDate`, `startWorkDate`, and `endWorkDate`.
- Produces: `RequestSupport.validateWorkDateFilter(Map<String, Object>)` returning `null` for valid filters or a Chinese error message for invalid filters; Tuxedo fields `WORK_DATE`, `START_WORK_DATE`, and `END_WORK_DATE`.

- [ ] **Step 1: Write failing servlet tests for all-date default and range forwarding**

Replace `defaultsWorkDateForVoucherCollection` with a test that invokes both collection paths without date parameters and asserts the captured request has none of the three Tuxedo date keys. Add a test that submits `startWorkDate=2026-07-10` and `endWorkDate=2026-07-12` to each path and asserts:

```java
assertThat(captured.get().fields())
    .containsEntry("START_WORK_DATE", "2026-07-10")
    .containsEntry("END_WORK_DATE", "2026-07-12")
    .doesNotContainKey("WORK_DATE");
```

- [ ] **Step 2: Write failing servlet tests for invalid filter combinations**

Add parameterized loop coverage for malformed bounds, impossible calendar dates, exact/range mixing, and reversed bounds. Each request must assert status `400`, response body `{"respCode":"2002",...}`, and that the Tuxedo client was not called. Include at least these maps:

```java
Map.of("startWorkDate", new String[] {"2026/07/10"});
Map.of("endWorkDate", new String[] {"2026-02-30"});
Map.of("workDate", new String[] {"2026-07-10"}, "startWorkDate", new String[] {"2026-07-09"});
Map.of("startWorkDate", new String[] {"2026-07-12"}, "endWorkDate", new String[] {"2026-07-10"});
```

- [ ] **Step 3: Write a failing mapper test for explicit field names**

Call `TuxedoRequestMapper.from(...)` with both range keys and assert:

```java
assertThat(request.fields())
    .containsEntry("START_WORK_DATE", "2026-07-10")
    .containsEntry("END_WORK_DATE", "2026-07-12");
```

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```powershell
mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest,TuxedoRequestMapperTest test
```

Expected: FAIL because the servlet still injects `WORK_DATE`, does not reject mixed/reversed ranges, and the mapper contract has not explicitly registered the new names.

- [ ] **Step 5: Implement WebFE validation and stop default injection**

In `RequestSupport`, remove `includeDefaultWorkDate` and add helpers that treat absent/blank values as absent, validate each nonblank date with `isValidWorkDate`, reject exact/range mixing, and compare parsed ISO dates:

```java
public static String validateWorkDateFilter(Map<String, Object> fields) {
    String workDate = text(fields.get("workDate"));
    String start = text(fields.get("startWorkDate"));
    String end = text(fields.get("endWorkDate"));
    if (!validOptionalDate(workDate) || !validOptionalDate(start) || !validOptionalDate(end)) {
        return "工作日期格式错误";
    }
    if (workDate != null && (start != null || end != null)) {
        return "工作日期不能与起止日期同时使用";
    }
    if (start != null && end != null && LocalDate.parse(start).isAfter(LocalDate.parse(end))) {
        return "开始工作日期不能晚于结束工作日期";
    }
    return null;
}
```

In `CnapsVoucherServlet.doGet`, call this validator only for the two collection paths, write HTTP 400 / code `2002` with the returned message, and remove the default-date call. Add explicit mapper entries:

```java
Map.entry("startWorkDate", "START_WORK_DATE"),
Map.entry("endWorkDate", "END_WORK_DATE"),
```

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the Step 4 command. Expected: PASS with no test failures.

- [ ] **Step 7: Commit WebFE contract changes**

```powershell
git add web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java
git commit -m "feat(webfe): validate voucher work date ranges"
```

### Task 2: Mock Tuxedo range behavior

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`

**Interfaces:**
- Consumes: optional `WORK_DATE`, `START_WORK_DATE`, and `END_WORK_DATE` request fields.
- Produces: identical validation and inclusive filtering behavior for `CNAPS4609Q` and `CNAPS5702Q`.

- [ ] **Step 1: Write failing mock tests for inclusive and one-sided ranges**

Create vouchers for `2026-07-09`, `2026-07-10`, `2026-07-12`, and `2026-07-13` in one branch. For both query services assert `START_WORK_DATE=2026-07-10` plus `END_WORK_DATE=2026-07-12` returns exactly the two boundary dates. Add assertions that a start-only request excludes July 9 and an end-only request excludes July 13.

- [ ] **Step 2: Write failing mock tests for full default and invalid combinations**

Assert a request containing only the trusted branch returns vouchers across all work dates. For both services assert response code `2002` for malformed bounds, impossible dates, exact/range mixing, and a reversed interval.

- [ ] **Step 3: Run the mock date test and verify RED**

Run:

```powershell
mvn -f web-fe/pom.xml -Dtest=MockTuxedoClientWorkDateTest test
```

Expected: FAIL because `voucherPage` only performs exact `WORK_DATE` matching and call-level validation only validates that field.

- [ ] **Step 4: Implement shared mock validation and filtering**

At query dispatch, validate all three optional values before entering the switch. Reject the same invalid combinations as WebFE with code `2002`. In `voucherPage`, retain exact matching and add:

```java
.filter(voucher -> matchesLowerWorkDate(voucher, text(request, "START_WORK_DATE")))
.filter(voucher -> matchesUpperWorkDate(voucher, text(request, "END_WORK_DATE")))
```

Implement helpers using ISO strings or `LocalDate`, with `>=` for the lower bound and `<=` for the upper bound. Blank bounds must return `true`.

- [ ] **Step 5: Run the mock date test and verify GREEN**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 6: Commit mock behavior**

```powershell
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java
git commit -m "feat(mock): filter vouchers by work date range"
```

### Task 3: Tuxedo FML, service validation, and Oracle predicates

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- Modify: `tuxedo-server/fml/cnaps_poc.fml32`
- Modify: `tuxedo-server/include/cnaps_fields.h`
- Modify: `tuxedo-server/include/cnaps_db.h`
- Modify: `tuxedo-server/src/services/cnaps_query.c`
- Modify: `tuxedo-server/src/common/db_helper.c`

**Interfaces:**
- Consumes: FML strings `START_WORK_DATE` and `END_WORK_DATE`.
- Produces: `db_query_vouchers(const char *work_date, const char *start_work_date, const char *end_work_date, ...)` and inclusive Oracle predicates bound in both count and page statements.

- [ ] **Step 1: Write failing C source contract tests**

Extend `TuxedoCSourceContractTest` to require:

```java
assertThat(fields).contains("CNAPS_F_START_WORK_DATE", "CNAPS_F_END_WORK_DATE");
assertThat(fml).contains("START_WORK_DATE", "END_WORK_DATE");
assertThat(header).contains("const char *start_work_date", "const char *end_work_date");
assertThat(query).contains("CNAPS_F_START_WORK_DATE", "CNAPS_F_END_WORK_DATE");
assertThat(db).contains(
    "(:start_work_date IS NULL OR WORK_DATE>=TO_DATE(:start_work_date, 'YYYY-MM-DD'))",
    "(:end_work_date IS NULL OR WORK_DATE<TO_DATE(:end_work_date, 'YYYY-MM-DD')+1)"
);
```

Also assert both services validate optional dates, reject exact/range mixing and reversed ranges before `db_query_vouchers`, and both SQL executions bind `:start_work_date` and `:end_work_date`.

- [ ] **Step 2: Run the C contract test and verify RED**

Run:

```powershell
mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest test
```

Expected: FAIL because the new FML fields, function parameters, validation, predicates, and binds do not exist.

- [ ] **Step 3: Add FML names and database function parameters**

Add unique string field IDs after the existing custom string fields:

```text
START_WORK_DATE 12045 string
END_WORK_DATE   12046 string
```

Add `CNAPS_F_START_WORK_DATE` and `CNAPS_F_END_WORK_DATE` macros. Add `start_work_date` and `end_work_date` immediately after `work_date` in the declaration and definition of `db_query_vouchers`, and update both call sites in `cnaps_query.c`.

- [ ] **Step 4: Implement Tuxedo service validation**

For each list service, read the exact date and both range fields into bounded raw buffers. Reuse `cnaps_valid_work_date` for every nonempty value. Before the database call reject:

```c
if (work_date[0] != '\0' && (start_work_date[0] != '\0' || end_work_date[0] != '\0')) {
    cnaps_return_error(rqst, "2002", "work date cannot be combined with range");
    return;
}
if (start_work_date[0] != '\0' && end_work_date[0] != '\0'
    && strcmp(start_work_date, end_work_date) > 0) {
    cnaps_return_error(rqst, "2002", "start work date is after end work date");
    return;
}
```

Pass empty strings through; existing `bind_text` converts them to SQL NULL consistently with other optional filters.

- [ ] **Step 5: Add inclusive SQL predicates and binds**

Extend `CNAPS_QUERY_PREDICATES` immediately after the exact-date condition:

```c
"AND (:start_work_date IS NULL OR WORK_DATE>=TO_DATE(:start_work_date, 'YYYY-MM-DD')) " \
"AND (:end_work_date IS NULL OR WORK_DATE<TO_DATE(:end_work_date, 'YYYY-MM-DD')+1) " \
```

Bind both fields after `:work_date` in both the count query and page query paths.

- [ ] **Step 6: Run the C contract test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 7: Run available C build validation**

Run:

```powershell
bash -n tuxedo-server/build.sh
```

Expected: exit code 0. If the local environment has the Tuxedo/OCI toolchain configured, additionally run `bash tuxedo-server/build.sh`; otherwise record that native compilation is environment-dependent and rely on source contracts plus Maven regression tests.

- [ ] **Step 8: Commit server behavior**

```powershell
git add tuxedo-server/fml/cnaps_poc.fml32 tuxedo-server/include/cnaps_fields.h tuxedo-server/include/cnaps_db.h tuxedo-server/src/services/cnaps_query.c tuxedo-server/src/common/db_helper.c web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java
git commit -m "feat(tuxedo): query vouchers by work date range"
```

### Task 4: API documentation and full verification

**Files:**
- Modify: `docs/cnaps-frontend-api.md`

**Interfaces:**
- Consumes: the finalized public query behavior from Tasks 1–3.
- Produces: caller-facing documentation for both voucher list endpoints.

- [ ] **Step 1: Update both list endpoint parameter tables and examples**

For sections 6.7 and 6.8, change `workDate` default from “当前日期” to `-`, add optional `startWorkDate` and `endWorkDate`, and state:

```text
startWorkDate 和 endWorkDate 均包含边界，可单独使用；workDate 不得与日期范围参数同时使用。三个日期参数都不传时不按工作日期筛选。
```

Show one common-list curl example with both bounds and one review-list example with a single bound. Document error code `2002` for invalid dates, mixed filters, and reversed ranges.

- [ ] **Step 2: Run documentation consistency checks**

Run:

```powershell
rg -n "startWorkDate|endWorkDate|三个日期参数都不传|当前日期" docs/cnaps-frontend-api.md
```

Expected: both endpoints contain the new fields and rules; no list-query row describes the `workDate` default as current date.

- [ ] **Step 3: Run the complete Java test suite**

Run:

```powershell
mvn -f web-fe/pom.xml test
```

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 4: Run repository diff checks**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the intended documentation file remains uncommitted at this task boundary.

- [ ] **Step 5: Commit documentation**

```powershell
git add docs/cnaps-frontend-api.md
git commit -m "docs(api): document voucher work date ranges"
```

- [ ] **Step 6: Verify final committed state**

Run:

```powershell
mvn -f web-fe/pom.xml test
git diff --check HEAD^ HEAD
git status --short
```

Expected: Maven reports BUILD SUCCESS, the final commit has no whitespace errors, and only pre-existing unrelated files such as `.idea/` may remain untracked.
