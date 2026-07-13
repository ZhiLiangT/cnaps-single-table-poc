# Voucher List Range-Only Work Date Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `startWorkDate` and `endWorkDate` the only list-query work-date inputs, reject every list request containing `workDate`, and complete the range fields across WebFE, Jolt, Tuxedo C, Oracle SQL, tests, and documentation.

**Architecture:** Enforce the retired-field contract at the HTTP boundary, map the two accepted range fields into FML32, and keep a matching defensive range validator in the mock and native Tuxedo services. Remove exact-date filtering from the shared native query signature and SQL while retaining `WORK_DATE` everywhere it represents voucher data rather than a list filter.

**Tech Stack:** Java 17, Servlet 4, Jackson, JUnit 5, AssertJ, Oracle Tuxedo/Jolt FML32 metadata, C, OCI SQL, Maven.

## Global Constraints

- A list JSON object containing the `workDate` key, including `null`, empty, or whitespace-only values, returns HTTP 400 / `2002` with exactly `列表查询不支持 workDate，请使用 startWorkDate/endWorkDate`.
- `startWorkDate` and `endWorkDate` are optional strict `yyyy-MM-dd` inclusive bounds and may be used independently.
- Blank range bounds are treated as absent; malformed, nonexistent, or reversed ranges return `2002`.
- Voucher create, update, detail, and list record output continue to use `workDate` / `WORK_DATE`.
- `CNAPS4609Q` and `CNAPS5702Q` keep all existing non-date filters, scoping, pagination, response fields, and status behavior.

---

### Task 1: Enforce the range-only HTTP contract

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`

**Interfaces:**
- Consumes: mutable list request fields as `Map<String, Object>`.
- Produces: `RequestSupport.validateWorkDateFilter(Map<String, Object>)`, returning `null` for a valid range or the exact user-facing validation message; blank range entries are removed in place.

- [ ] **Step 1: Write failing validator and servlet tests**

Replace the old blank/exact-`workDate` tests with a validator test that preserves key presence semantics:

```java
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
```

Add a servlet test that runs the same four JSON bodies against both list paths, asserts HTTP 400 and the exact response JSON, and asserts the captured Tuxedo request remains null. Keep the existing tests for valid bounds, blank bounds, malformed bounds, and reversed ranges, but remove exact-date success and mixed-date cases that no longer describe the contract.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
mvn -f web-fe/pom.xml -Dtest=RequestSupportTest,BaseJsonServletTest test
```

Expected: FAIL because `validateWorkDateFilter` currently removes blank `workDate` and accepts exact `workDate` values.

- [ ] **Step 3: Implement minimal WebFE validation**

Change `validateWorkDateFilter` so key presence is checked before blank removal:

```java
public static String validateWorkDateFilter(Map<String, Object> fields) {
    if (fields.containsKey("workDate")) {
        return "列表查询不支持 workDate，请使用 startWorkDate/endWorkDate";
    }
    removeBlank(fields, "startWorkDate");
    removeBlank(fields, "endWorkDate");
    String startWorkDate = text(fields.get("startWorkDate"));
    String endWorkDate = text(fields.get("endWorkDate"));
    if (!isValidOptionalWorkDate(startWorkDate) || !isValidOptionalWorkDate(endWorkDate)) {
        return "工作日期格式错误";
    }
    if (startWorkDate != null
        && endWorkDate != null
        && LocalDate.parse(startWorkDate).isAfter(LocalDate.parse(endWorkDate))) {
        return "开始工作日期不能晚于结束工作日期";
    }
    return null;
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all `RequestSupportTest` and `BaseJsonServletTest` tests pass.

- [ ] **Step 5: Commit the HTTP contract**

```powershell
git add web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java
git commit -m "feat(webfe): require range dates for voucher lists"
```

### Task 2: Remove exact-date filtering from the mock service

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`

**Interfaces:**
- Consumes: list Tuxedo requests containing optional `START_WORK_DATE` and `END_WORK_DATE`.
- Produces: range-filtered mock pages for `CNAPS4609Q` and `CNAPS5702Q`; `WORK_DATE` remains a voucher record field but has no list-filter effect.

- [ ] **Step 1: Write a failing mock compatibility test**

Replace mock tests for exact list filtering with:

```java
@Test
void voucherQueriesDoNotUseWorkDateAsAnExactFilter() {
    createVoucher("2026-07-10", "SERVER-BRANCH");
    createVoucher("2026-07-11", "SERVER-BRANCH");

    for (String service : List.of("CNAPS4609Q", "CNAPS5702Q")) {
        assertThat(records(client.call(
            service,
            request(Map.of("WORK_DATE", "2026-07-10", "BRANCH_NO", "SERVER-BRANCH"))
        )))
            .extracting(record -> record.get("WORK_DATE"))
            .containsExactly("2026-07-10", "2026-07-11");
    }
}
```

Remove list assertions that expect invalid `WORK_DATE` to return `2002`; retain create/update validation tests and all range validation tests.

- [ ] **Step 2: Run the mock test and verify RED**

```powershell
mvn -f web-fe/pom.xml -Dtest=MockTuxedoClientWorkDateTest test
```

Expected: FAIL because `voucherPage` still applies exact `WORK_DATE` matching.

- [ ] **Step 3: Remove mock exact-date logic**

Delete this stream filter:

```java
.filter(voucher -> matches(voucher, "WORK_DATE", optionalText(request, "WORK_DATE"), false))
```

Update `validateWorkDateFilter(TuxedoRequest)` to read and validate only `START_WORK_DATE` and `END_WORK_DATE`, deleting exact-date validation and the mutual-exclusion branch. Do not reject `WORK_DATE` in the mock because the HTTP boundary owns the retired-field error and the mock contract verifies that native-style list processing does not interpret it as a filter.

- [ ] **Step 4: Run the mock test and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit mock parity**

```powershell
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java
git commit -m "refactor(mock): remove exact voucher list date filter"
```

### Task 3: Complete range-only Jolt metadata

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `tuxedo/jolt/cnaps_services.bulk`

**Interfaces:**
- Consumes: `START_WORK_DATE` and `END_WORK_DATE` scalar string inputs on `CNAPS4609Q` and `CNAPS5702Q`.
- Produces: Jolt service definitions that accept both bounds and expose voucher `WORK_DATE` only as repeated output data for list services.

- [ ] **Step 1: Write failing metadata assertions**

Add these assertions for both list services:

```java
for (String service : new String[] {"CNAPS4609Q", "CNAPS5702Q"}) {
    assertScalarParam(metadata, service, "START_WORK_DATE", "string", "in");
    assertScalarParam(metadata, service, "END_WORK_DATE", "string", "in");
    assertRepeatedParam(metadata, service, "WORK_DATE", "string", "out");
}
```

Update the repeated voucher-field direction switch so `WORK_DATE` is `out` for both list services. Keep `BRANCH_NO`, `STATUS`, `SERIAL_NO`, and other bidirectional fields unchanged.

- [ ] **Step 2: Run the metadata test and verify RED**

```powershell
mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test
```

Expected: FAIL because neither list-service block currently contains the range inputs and `WORK_DATE` is still `inout`.

- [ ] **Step 3: Update both Jolt service blocks**

For both `CNAPS4609Q` and `CNAPS5702Q`, change the repeated record field to output-only:

```text
param=WORK_DATE
type=string
access=out
count=0
```

Add scalar range inputs before repeated record outputs:

```text
param=START_WORK_DATE
type=string
access=in
param=END_WORK_DATE
type=string
access=in
```

- [ ] **Step 4: Run the metadata test and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit Jolt metadata**

```powershell
git add tuxedo/jolt/cnaps_services.bulk web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "fix(jolt): register voucher list date ranges"
```

### Task 4: Remove native exact-date query inputs and predicates

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- Modify: `tuxedo-server/include/cnaps_db.h`
- Modify: `tuxedo-server/src/services/cnaps_query.c`
- Modify: `tuxedo-server/src/common/db_helper.c`

**Interfaces:**
- Consumes: `db_query_vouchers(const char *start_work_date, const char *end_work_date, ...)`.
- Produces: inclusive Oracle range filtering without an exact `work_date` bind.

- [ ] **Step 1: Write failing native source contract assertions**

Update `nativeVoucherQueriesSupportValidatedInclusiveWorkDateRanges` to require range validation but prohibit retired exact-date input logic:

```java
assertThat(query)
    .contains(
        "CNAPS_F_START_WORK_DATE", "CNAPS_F_END_WORK_DATE",
        "cnaps_valid_work_date(raw_start_work_date)",
        "cnaps_valid_work_date(raw_end_work_date)",
        "start work date is after end work date"
    )
    .doesNotContain(
        "get_field(fbfr, CNAPS_F_WORK_DATE",
        "work date cannot be combined with range",
        "char raw_work_date[513]",
        "char work_date[11]"
    );
assertThat(header).doesNotContain("const char *work_date,");
assertThat(db).doesNotContain(
    ":work_date",
    "WORK_DATE=TO_DATE(:work_date, 'YYYY-MM-DD')"
);
```

Adjust `nativeWorkDateValidationUsesCompleteUntruncatedInput` so its query assertions cover two raw start buffers and two raw end buffers, while create/update assertions continue to cover `WORK_DATE` unchanged.

- [ ] **Step 2: Run the native contract test and verify RED**

```powershell
mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest test
```

Expected: FAIL because the native query service, function signature, and SQL still contain exact `work_date` handling.

- [ ] **Step 3: Remove the exact-date native argument**

In both `CNAPS4609Q` and `CNAPS5702Q`, delete `work_date`, `raw_work_date`, the `CNAPS_F_WORK_DATE` input read, exact-date validation, mixed-parameter validation, copying to `work_date`, and the first `db_query_vouchers` argument.

Change the declaration and definition to begin:

```c
int db_query_vouchers(
    const char *start_work_date,
    const char *end_work_date,
    const char *branch_no,
```

Delete the exact predicate and both count/page bindings:

```c
"WHERE (:start_work_date IS NULL OR WORK_DATE>=TO_DATE(:start_work_date, 'YYYY-MM-DD')) " \
"AND (:end_work_date IS NULL OR WORK_DATE<TO_DATE(:end_work_date, 'YYYY-MM-DD')+1) " \
```

- [ ] **Step 4: Run the native contract test and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit native query cleanup**

```powershell
git add tuxedo-server/include/cnaps_db.h tuxedo-server/src/services/cnaps_query.c tuxedo-server/src/common/db_helper.c web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java
git commit -m "refactor(tuxedo): remove exact voucher list date filter"
```

### Task 5: Publish the final API contract and verify the repository

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `docs/cnaps-frontend-api.md`

**Interfaces:**
- Consumes: the completed range-only behavior from Tasks 1-4.
- Produces: public documentation that distinguishes request-side list filters from response-side voucher `workDate`.

- [ ] **Step 1: Write a failing documentation contract assertion**

Extract the general and review list sections and assert each contains both range fields and the retired-field error, while neither Body parameter table contains a `workDate` row:

```java
String api = Files.readString(root.resolve("docs/cnaps-frontend-api.md"));
String generalList = api.substring(api.indexOf("## 6.7 通用查询"), api.indexOf("## 6.8 待复核查询"));
String reviewList = api.substring(api.indexOf("## 6.8 待复核查询"), api.indexOf("## 6.9", api.indexOf("## 6.8 待复核查询")));
for (String section : List.of(generalList, reviewList)) {
    assertThat(section)
        .contains("`startWorkDate`", "`endWorkDate`", "列表查询不支持 workDate，请使用 startWorkDate/endWorkDate")
        .doesNotContain("| `workDate` | string | 否");
}
```

- [ ] **Step 2: Run the documentation contract and verify RED**

```powershell
mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test
```

Expected: FAIL because both list parameter tables still document exact `workDate` filtering and do not contain the retired-field response.

- [ ] **Step 3: Update the public API document**

In sections 6.7 and 6.8:

- remove the request parameter row for `workDate`;
- retain optional inclusive `startWorkDate` and `endWorkDate` rows;
- state that neither bound means no work-date filtering and either bound may be used alone;
- add the exact HTTP 400 / `2002` retired-field message;
- remove all list-query statements describing exact-date/range mutual exclusion;
- retain `workDate` in voucher record response examples and create/update documentation.

- [ ] **Step 4: Run focused and full verification**

```powershell
mvn -f web-fe/pom.xml -Dtest=RequestSupportTest,BaseJsonServletTest,MockTuxedoClientWorkDateTest,DeploymentArtifactTest,TuxedoCSourceContractTest test
mvn -f web-fe/pom.xml test
git diff --check
rg -n "work date cannot be combined with range|:work_date IS NULL|列表查询不支持 workDate" web-fe tuxedo-server tuxedo docs/cnaps-frontend-api.md
```

Expected: focused and full Maven suites pass, `git diff --check` is clean, retired native exact-date tokens are absent from list-query code, and the new user-facing message appears in implementation, tests, and documentation.

- [ ] **Step 5: Commit documentation**

```powershell
git add docs/cnaps-frontend-api.md web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "docs(api): publish range-only voucher list dates"
```
