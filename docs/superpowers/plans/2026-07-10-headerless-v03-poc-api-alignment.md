# Headerless v0.3 POC API Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the existing WebFE, mock client, native Tuxedo services, Oracle access, JSP entry points, and public documentation with the approved headerless v0.3 single-table POC API.

**Architecture:** Keep the current Servlet → request mapper → Mock/Jolt client → Tuxedo/FML32 → OCI/Oracle flow. HTTP callers send only path, query, and JSON body business data; WebFE owns request IDs and fixed POC operator/branch values, while both mock and native paths enforce the same voucher state machine and return the same three-field response envelope.

**Tech Stack:** Java 17, Servlet 4, Jackson 2.17, JUnit 5, AssertJ, JSP/JavaScript, Oracle Tuxedo ATMI/FML32/Jolt, C/OCI, Oracle SQL, Maven.

## Global Constraints

- Do not add login, account, role, permission, or operator-switching behavior.
- Do not accept `requestId`, `operatorNo`, `branchNo`, or `workDate` as business request headers.
- Keep `Content-Type: application/json; charset=UTF-8` for JSON requests.
- Generate request IDs inside WebFE; obtain operator and branch only from fixed server-side POC configuration.
- Allow the fixed POC operator to create and review the same voucher; remove error code `3005` from active behavior.
- Create requires body field `workDate`; update accepts optional body field `workDate`; collection queries accept optional query field `workDate` and default it to the server date.
- Return exactly `respCode`, `respMsg`, and `data` at the HTTP response top level.
- Keep the existing `T_CNAPS_BILL_POC` table and logical-delete state model; do not add tables.
- Expose only `pageNo` and `pageSize` as public pagination request names.

---

## File Structure

- `web-fe/src/main/java/com/ruisui/cnaps/web/dto/ApiResponse.java`: public three-field response envelope.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`: trusted server context and public field-to-FML32 mapping.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`: FML32 field names and page/result JSON shaping.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`: in-memory reference implementation of the entire v0.3 contract.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`: real Jolt response extraction, including repeated query records.
- `web-fe/src/main/webapp/cnaps-create.jsp`: minimal usable create form with `workDate`.
- `tuxedo-server/include/cnaps_db.h`: complete voucher row and OCI query/update interfaces.
- `tuxedo-server/include/cnaps_fields.h`: canonical FML32 field-name constants.
- `tuxedo-server/include/cnaps_service.h`: FML32 occurrence writer declarations.
- `tuxedo-server/src/common/db_helper.c`: complete single-row CRUD and paged query OCI statements.
- `tuxedo-server/src/common/fml_helper.c`: complete voucher serialization and repeated-record output.
- `tuxedo-server/src/services/*.c`: native validation, status checks, CRUD, review, reference data, and query orchestration.
- `tuxedo-server/fml/cnaps_poc.fml32`: missing response/query fields used by the approved JSON contract.
- `tuxedo/jolt/cnaps_services.bulk`: complete in/out metadata for every HTTP-visible service.
- `docs/cnaps-frontend-api.md`: current public API contract.
- `web-fe/src/test/java/com/ruisui/cnaps/web/**`: Java behavior and source-contract tests.

---

### Task 1: Make the HTTP Response Envelope Exact

**Files:**
- Create: `web-fe/src/test/java/com/ruisui/cnaps/web/dto/ApiResponseContractTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/dto/ApiResponse.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/V03TuxedoContractTest.java`

**Interfaces:**
- Produces: `ApiResponse<T>(String respCode, String respMsg, T data)`.
- Produces: `ApiResponse.ok(String message, T data)` and `ApiResponse.fail(String code, String message)`.
- Consumes: unchanged internal `TuxedoResponse` success flag and response code.

- [ ] **Step 1: Write the failing three-field envelope test**

```java
package com.ruisui.cnaps.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOnlyRespCodeRespMsgAndData() throws Exception {
        ApiResponse<Map<String, String>> response = ApiResponse.ok(
            "查询成功",
            Map.of("status", "10_PENDING_REVIEW")
        );

        assertThat(ApiResponse.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("respCode", "respMsg", "data");
        assertThat(objectMapper.writeValueAsString(response))
            .isEqualTo("{\"respCode\":\"0000\",\"respMsg\":\"查询成功\",\"data\":{\"status\":\"10_PENDING_REVIEW\"}}");
    }

    @Test
    void failureUsesNullData() {
        assertThat(ApiResponse.fail("3001", "单据不存在"))
            .isEqualTo(new ApiResponse<>("3001", "单据不存在", null));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=ApiResponseContractTest test`

Expected: FAIL because the record still contains `success` and the two-argument factory methods do not exist.

- [ ] **Step 3: Replace the public response record and update the mapper**

```java
package com.ruisui.cnaps.web.dto;

public record ApiResponse<T>(
    String respCode,
    String respMsg,
    T data
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("0000", message, data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

Change `TuxedoResponseMapper.toApiResponse` to use the new factories:

```java
public ApiResponse<Object> toApiResponse(String requestId, TuxedoResponse response) {
    if (response.success()) {
        Object data = response.fields().containsKey(DATA_FIELD)
            ? mapValue(response.fields().get(DATA_FIELD))
            : mapFields(response.fields());
        return ApiResponse.ok(response.respMsg(), data);
    }
    return ApiResponse.fail(response.respCode(), response.respMsg());
}
```

Keep the `requestId` parameter temporarily because `BaseJsonServlet` already supplies it; it remains internal and unused. Remove `response.success()` assertions from `V03TuxedoContractTest` and assert only `respCode`, `respMsg`, and `data`.

- [ ] **Step 4: Run envelope and mapper tests and verify GREEN**

Run: `mvn -f web-fe/pom.xml -Dtest=ApiResponseContractTest,V03TuxedoContractTest test`

Expected: PASS with 0 failures.

- [ ] **Step 5: Run the WebFE test suite**

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS with 0 failures and no remaining Java references to `ApiResponse.success()`.

- [ ] **Step 6: Commit**

```bash
git add web-fe/src/main/java/com/ruisui/cnaps/web/dto/ApiResponse.java web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java web-fe/src/test/java/com/ruisui/cnaps/web/dto/ApiResponseContractTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/V03TuxedoContractTest.java
git commit -m "fix(web-fe): match v03 response envelope"
```

---

### Task 2: Make the Mock Client the Complete v0.3 Reference Behavior

**Files:**
- Create: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientV03ContractTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`

**Interfaces:**
- Consumes: canonical FML32 keys in `TuxedoRequest.fields()`.
- Produces: all eleven service responses used by `TuxedoResponseMapper`.
- Enforces: create validation, editable/reviewable state checks, logical deletion, query filters, and stable pagination.

- [ ] **Step 1: Write failing lifecycle, validation, filtering, and pagination tests**

Create a test class with this shared request builder and focused tests:

```java
package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockTuxedoClientV03ContractTest {
    private final MockTuxedoClient client = new MockTuxedoClient();

    @Test
    void rejectsNonPositiveOrOverScaledAmount() {
        assertThat(client.call("CNAPS5701E", request(Map.of("AMOUNT", "0"))).respCode())
            .isEqualTo("2002");
        assertThat(client.call("CNAPS5701E", request(Map.of("AMOUNT", "1.001"))).respCode())
            .isEqualTo("2002");
    }

    @Test
    void fixedPocOperatorCanCreateAndReviewTheSameVoucher() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        TuxedoResponse reviewed = client.call(
            "CNAPS5702A",
            request(Map.of("BILL_ID", created.fields().get("BILL_ID")))
        );

        assertThat(reviewed.respCode()).isEqualTo("0000");
        assertThat(reviewed.fields()).containsEntry("STATUS", "20_REVIEW_APPROVED");
    }

    @Test
    void returnUpdateAndDeleteFollowTheApprovedStateFlow() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");
        TuxedoResponse returned = client.call(
            "CNAPS5702R",
            request(Map.of("BILL_ID", billId, "REJECT_REASON", "户名有误"))
        );
        TuxedoResponse updated = client.call(
            "CNAPS5701U",
            request(Map.of("BILL_ID", billId, "PAYEE_NAME", "修改后户名"))
        );
        TuxedoResponse deleted = client.call(
            "CNAPS5701D",
            request(Map.of("BILL_ID", billId, "DELETE_REASON", "录入错误"))
        );

        assertThat(returned.fields()).containsEntry("STATUS", "30_REVIEW_REJECTED");
        assertThat(updated.fields())
            .containsEntry("STATUS", "10_PENDING_REVIEW")
            .containsEntry("PAYEE_NAME", "修改后户名");
        assertThat(deleted.fields()).containsEntry("STATUS", "40_DELETED");
    }

    @Test
    void generalQueryAppliesDocumentedFiltersAndPagination() {
        TuxedoResponse first = client.call(
            "CNAPS5701E",
            request(Map.of("VOUCHER_NO", "PZ-001", "PAYEE_NAME", "甲收款人"))
        );
        client.call(
            "CNAPS5701E",
            request(Map.of("VOUCHER_NO", "PZ-002", "PAYEE_NAME", "乙收款人"))
        );

        TuxedoResponse queried = client.call(
            "CNAPS4609Q",
            request(Map.of(
                "VOUCHER_NO", "PZ-001",
                "PAYEE_NAME", "甲",
                "PAGE_NO", "1",
                "PAGE_SIZE", "1"
            ))
        );

        Map<String, Object> page = page(queried);
        assertThat(page).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 1).containsEntry("TOTAL", 1);
        assertThat(records(page)).extracting(record -> record.get("BILL_ID"))
            .containsExactly(first.fields().get("BILL_ID"));
    }

    @Test
    void deletedVoucherIsHiddenUnlessIncludeDeletedIsTrue() {
        TuxedoResponse created = client.call("CNAPS5701E", request(Map.of()));
        Object billId = created.fields().get("BILL_ID");
        client.call("CNAPS5701D", request(Map.of("BILL_ID", billId)));

        assertThat(records(page(client.call("CNAPS4609Q", request(Map.of()))))).isEmpty();
        assertThat(records(page(client.call(
            "CNAPS4609Q",
            request(Map.of("INCLUDE_DELETED", "true"))
        )))).hasSize(1);
    }

    private TuxedoRequest request(Map<String, ?> overrides) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("REQ_ID", "REQ-POC");
        fields.put("OPERATOR_NO", "77210021");
        fields.put("BRANCH_NO", "772");
        fields.put("WORK_DATE", "2026-07-10");
        fields.put("BUSINESS_TYPE", "02102");
        fields.put("ACCOUNT_PART1", "404045");
        fields.put("ACCOUNT_PART2", "00772");
        fields.put("ACCOUNT_PART3", "000000000001");
        fields.put("PAYEE_ACCT", "622200000000000001");
        fields.put("PAYEE_NAME", "测试收款人");
        fields.put("PRIORITY", "NORM");
        fields.put("SYSTEM_TYPE", "CNAPS");
        fields.put("AMOUNT", "100.00");
        fields.putAll(overrides);
        return new TuxedoRequest(fields);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(TuxedoResponse response) {
        return (Map<String, Object>) response.fields().get("_DATA");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("RECORDS");
    }
}
```

Update `MockTuxedoClientWorkDateTest.request` with the same required create fields so existing work-date tests express a valid voucher.

- [ ] **Step 2: Run the mock contract tests and verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=MockTuxedoClientV03ContractTest,MockTuxedoClientWorkDateTest test`

Expected: FAIL because amount validation, same-operator review, several filters, and page slicing are missing.

- [ ] **Step 3: Add minimal create/update validation**

Add these helpers to `MockTuxedoClient` and call `validateCreate` before allocating a serial number:

```java
private static final Map<String, String> REQUIRED_CREATE_FIELDS = Map.ofEntries(
    Map.entry("WORK_DATE", "workDate"),
    Map.entry("BUSINESS_TYPE", "businessType"),
    Map.entry("ACCOUNT_PART1", "accountPart1"),
    Map.entry("ACCOUNT_PART2", "accountPart2"),
    Map.entry("ACCOUNT_PART3", "accountPart3"),
    Map.entry("PAYEE_ACCT", "payeeAccountNo"),
    Map.entry("PAYEE_NAME", "payeeName"),
    Map.entry("PRIORITY", "priority"),
    Map.entry("SYSTEM_TYPE", "systemType"),
    Map.entry("AMOUNT", "amount")
);

private TuxedoResponse validateCreate(TuxedoRequest request) {
    for (Map.Entry<String, String> required : REQUIRED_CREATE_FIELDS.entrySet()) {
        String value = text(request, required.getKey());
        if (value == null || value.isBlank()) {
            return TuxedoResponse.fail("2001", "必输字段为空：" + required.getValue());
        }
    }
    if (!validMoney(text(request, "AMOUNT"), false) || !validMoney(text(request, "FEE_AMOUNT", "0.00"), true)) {
        return TuxedoResponse.fail("2002", "金额格式错误");
    }
    return null;
}

private boolean validMoney(String value, boolean zeroAllowed) {
    try {
        java.math.BigDecimal amount = new java.math.BigDecimal(value);
        return amount.scale() <= 2 && (zeroAllowed ? amount.signum() >= 0 : amount.signum() > 0);
    } catch (NumberFormatException ex) {
        return false;
    }
}
```

For update, never overwrite `BILL_ID`, `SERIAL_NO`, original `OPERATOR_NO`, `BRANCH_NO`, `CREATED_AT`, `LAST_ACTION_*`, or response-only fields from client input. Validate `AMOUNT`, `FEE_AMOUNT`, and `WORK_DATE` only when supplied, merge other documented business fields, and preserve omitted values.

- [ ] **Step 4: Remove identity comparison and complete state checks**

Delete both branches that return `3005`. Keep these exact review guards:

```java
if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
    return TuxedoResponse.fail("3004", "单据状态已变化");
}
```

Keep `editable` restricted to pending and rejected states. After successful update, remove prior reject/checker fields and reset status to pending. Reject return requests with a blank reason before mutating the voucher.

- [ ] **Step 5: Complete query filtering and page slicing**

Build a stable filtered list ordered by `BILL_ID`, compute `total` before slicing, and return only the requested page:

```java
int pageNo = pageNo(request);
int pageSize = pageSize(request);
List<Map<String, Object>> matching = vouchers.values().stream()
    .filter(voucher -> matches(voucher, "STATUS", status, false))
    .filter(voucher -> matches(voucher, "WORK_DATE", text(request, "WORK_DATE"), false))
    .filter(voucher -> matches(voucher, "BRANCH_NO", text(request, "BRANCH_NO"), false))
    .filter(voucher -> matches(voucher, "SERIAL_NO", text(request, "SERIAL_NO"), false))
    .filter(voucher -> matches(voucher, "VOUCHER_NO", text(request, "VOUCHER_NO"), false))
    .filter(voucher -> matches(voucher, "PAYEE_ACCT", text(request, "PAYEE_ACCT"), false))
    .filter(voucher -> matches(voucher, "PAYEE_NAME", text(request, "PAYEE_NAME"), true))
    .filter(voucher -> Boolean.parseBoolean(text(request, "INCLUDE_DELETED", "false"))
        || !"40_DELETED".equals(voucher.get("STATUS")))
    .sorted(java.util.Comparator.comparing(voucher -> String.valueOf(voucher.get("BILL_ID"))))
    .map(voucher -> (Map<String, Object>) new LinkedHashMap<>(voucher))
    .toList();
int fromIndex = Math.min((pageNo - 1) * pageSize, matching.size());
int toIndex = Math.min(fromIndex + pageSize, matching.size());
return Map.of(
    "PAGE_NO", pageNo,
    "PAGE_SIZE", pageSize,
    "TOTAL", matching.size(),
    "RECORDS", matching.subList(fromIndex, toIndex)
);
```

Implement `matches` so blank criteria match all records, exact fields compare exactly, and only `PAYEE_NAME` uses substring matching.

- [ ] **Step 6: Complete reference-data behavior without new dependencies**

Represent the seven documented dictionary types as an immutable in-class map and return `2003` for an unknown type. Keep the one seeded CNAPS bank, but apply `bankNo`, `keyword`, `city`, `systemType`, `pageNo`, and `pageSize` filters before returning the page. Add focused assertions to the same contract test for one valid dictionary, one invalid dictionary, and a non-matching bank filter.

- [ ] **Step 7: Run mock tests and the full WebFE suite**

Run: `mvn -f web-fe/pom.xml -Dtest=MockTuxedoClientV03ContractTest,MockTuxedoClientWorkDateTest test`

Expected: PASS with 0 failures.

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS with 0 failures.

- [ ] **Step 8: Commit**

```bash
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientV03ContractTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java
git commit -m "feat(mock): implement v03 voucher contract"
```

---

### Task 3: Lock Down Headerless Mapping, Pagination Names, and the Create Page

**Files:**
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- Modify: `web-fe/src/main/webapp/cnaps-create.jsp`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`

**Interfaces:**
- Consumes: JSON/query keys documented by v0.3.
- Produces: trusted `REQ_ID`, `OPERATOR_NO`, and `BRANCH_NO`; endpoint-owned `WORK_DATE`.
- Produces: create form body containing `workDate`.

- [ ] **Step 1: Add failing mapping and page artifact assertions**

Extend `TuxedoRequestMapperTest`:

```java
@Test
void exposesOnlyV03PaginationNames() {
    TuxedoRequest v03Request = mapper.from(
        "SERVER-REQ",
        "SERVER-OP",
        "SERVER-BRANCH",
        Map.of("pageNo", "2", "pageSize", "5")
    );
    TuxedoRequest legacyRequest = mapper.from(
        "SERVER-REQ",
        "SERVER-OP",
        "SERVER-BRANCH",
        Map.of("page", "9", "size", "99")
    );

    assertThat(v03Request.fields())
        .containsEntry("PAGE_NO", "2")
        .containsEntry("PAGE_SIZE", "5");
    assertThat(legacyRequest.fields()).doesNotContainKeys("PAGE_NO", "PAGE_SIZE");
}
```

Extend `DeploymentArtifactTest`:

```java
@Test
void createPageSubmitsWorkDateWithoutBusinessHeaders() throws Exception {
    String page = Files.readString(root.resolve("web-fe/src/main/webapp/cnaps-create.jsp"));
    String script = Files.readString(root.resolve("web-fe/src/main/webapp/static/js/cnaps.js"));

    assertThat(page).contains("name=\"workDate\"");
    assertThat(script)
        .contains("Content-Type")
        .doesNotContain("requestId:", "operatorNo:", "branchNo:", "workDate:");
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest,BaseJsonServletTest,DeploymentArtifactTest test`

Expected: FAIL because the create JSP has no `workDate` input and old `page`/`size` aliases remain in the mapper.

- [ ] **Step 3: Remove legacy pagination aliases**

Delete only these two entries from `BODY_FIELD_NAMES`:

```java
Map.entry("page", "PAGE_NO"),
Map.entry("size", "PAGE_SIZE"),
```

Retain `pageNo`, `pageSize`, all voucher fields, and trusted-context overlay order. Extend `BaseJsonServletTest` to send all four legacy business headers and assert none reaches the Tuxedo request while body/query `workDate` still does.

- [ ] **Step 4: Add a usable work-date control to the create page**

Insert this control into the existing grid without changing the page layout:

```jsp
<label>工作日期
    <input type="date" name="workDate" required value="<%= java.time.LocalDate.now() %>">
</label>
```

Do not add account, operator, branch, or request-ID inputs. Keep `Content-Type` in `cnaps.js` because the body is JSON.

- [ ] **Step 5: Verify focused and full tests**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest,BaseJsonServletTest,DeploymentArtifactTest test`

Expected: PASS with 0 failures.

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS with 0 failures.

- [ ] **Step 6: Commit**

```bash
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java web-fe/src/main/webapp/cnaps-create.jsp web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "fix(web-fe): enforce headerless v03 inputs"
```

---

### Task 4: Complete Native Tuxedo Single-Voucher CRUD and Review

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- Modify: `tuxedo-server/include/cnaps_db.h`
- Modify: `tuxedo-server/src/common/db_helper.c`
- Modify: `tuxedo-server/src/common/fml_helper.c`
- Modify: `tuxedo-server/src/services/cnaps_create.c`
- Modify: `tuxedo-server/src/services/cnaps_update.c`
- Modify: `tuxedo-server/src/services/cnaps_delete.c`
- Modify: `tuxedo-server/src/services/cnaps_review.c`

**Interfaces:**
- Produces: `db_find_voucher` with every public detail field populated.
- Produces: `db_update_voucher` returning 0 for updated, 1 for missing/stale row, and -1 for OCI failure.
- Consumes: fixed server operator/branch FML32 fields without identity comparison.

- [ ] **Step 1: Add failing native source-contract assertions**

Add these tests to `TuxedoCSourceContractTest`:

```java
@Test
void nativeLifecycleChecksStateAndDoesNotRejectSameOperator() throws Exception {
    String update = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_update.c"));
    String delete = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_delete.c"));
    String review = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_review.c"));

    assertThat(update).contains("CNAPS_STATUS_PENDING_REVIEW", "CNAPS_STATUS_REJECTED", "3003");
    assertThat(delete).contains("CNAPS_STATUS_PENDING_REVIEW", "CNAPS_STATUS_REJECTED", "3003");
    assertThat(review)
        .contains("CNAPS_STATUS_PENDING_REVIEW", "3004")
        .doesNotContain("3005", "strcmp(row.operator_no, row.checker_no)");
}

@Test
void nativeDetailAndFmlOutputCoverTheV03VoucherFields() throws Exception {
    String db = Files.readString(root.resolve("tuxedo-server/src/common/db_helper.c"));
    String fml = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));

    assertThat(db).contains(
        "ACCOUNT_PART1", "ACCOUNT_PART2", "ACCOUNT_PART3", "ACCOUNT_NAME", "PAYER_NAME",
        "RECEIVE_BANK_NO", "RECEIVE_BANK_NAME", "FEE_AMOUNT", "DELETE_TIME", "VERSION_NO"
    );
    assertThat(fml).contains(
        "CNAPS_F_ACCOUNT_PART1", "CNAPS_F_RECEIVE_BANK_NO", "CNAPS_F_FEE_AMOUNT",
        "CNAPS_F_DELETE_TIME", "CNAPS_F_VERSION_NO"
    );
}
```

- [ ] **Step 2: Run source-contract tests and verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest test`

Expected: FAIL because native services do not load current state, detail output is partial, and several field constants are missing.

- [ ] **Step 3: Extend the native row and FML32 output contract**

Add these fields to `cnaps_voucher_row` using the database/API sizes:

```c
char checker_time[20];
char delete_time[20];
char last_action_time[20];
char created_at[20];
char updated_at[20];
long version_no;
```

Add matching `CNAPS_F_*` constants and FML32 definitions for `DELETE_TIME`, `LAST_ACTION_TIME`, `CREATED_AT`, `UPDATED_AT`, and `VERSION_NO`. Extend `cnaps_put_voucher` to write every field in the attachment, including the three account parts, names, bank fields, fee/send flags, audit fields, timestamps, and version.

- [ ] **Step 4: Make `db_find_voucher` hydrate the full row**

Replace the partial select with a single explicit select in attachment field order:

```sql
SELECT BILL_ID, TO_CHAR(WORK_DATE, 'YYYY-MM-DD'), BRANCH_NO, OPERATOR_NO, SERIAL_NO,
       BUSINESS_TYPE, ACCOUNT_PART1, ACCOUNT_PART2, ACCOUNT_PART3,
       NVL(ACCOUNT_NAME, ''), NVL(PAYER_NAME, ''), PAYEE_ACCOUNT_NO, PAYEE_NAME,
       NVL(PRIORITY, ''), NVL(RECEIVE_BANK_NO, ''), NVL(RECEIVE_BANK_NAME, ''),
       NVL(SYSTEM_TYPE, ''), TO_CHAR(AMOUNT, 'FM999999999999990D00'),
       NVL(DEBIT_MODE, ''), TO_CHAR(NVL(FEE_AMOUNT, 0), 'FM999999999999990D00'),
       NVL(FEE_CHARGE_MODE, ''), NVL(SEND_MODE, ''), NVL(FAX_FLAG, ''),
       NVL(VOUCHER_NO, ''), NVL(REMARK, ''), STATUS,
       NVL(CHECKER_NO, ''), NVL(TO_CHAR(CHECKER_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''),
       NVL(REVIEW_COMMENT, ''), NVL(REJECT_REASON, ''), NVL(DELETE_REASON, ''),
       NVL(DELETE_OPERATOR_NO, ''), NVL(TO_CHAR(DELETE_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''),
       NVL(LAST_ACTION, ''), NVL(LAST_OPERATOR_NO, ''), NVL(LAST_REQUEST_ID, ''),
       NVL(TO_CHAR(LAST_ACTION_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''),
       NVL(TO_CHAR(CREATED_AT, 'YYYY-MM-DD HH24:MI:SS'), ''),
       NVL(TO_CHAR(UPDATED_AT, 'YYYY-MM-DD HH24:MI:SS'), ''), NVL(VERSION_NO, 1)
  FROM T_CNAPS_BILL_POC
 WHERE BILL_ID=:bill_id
```

Bind each selected column to its exact `cnaps_voucher_row` member. Keep `OCI_NO_DATA` mapped to return value 1.

- [ ] **Step 5: Make native create validation match the attachment**

Validate all required strings after `row_from_create_request`. Add a C money validator that accepts digits plus at most one decimal point and two fractional digits, requires `amount > 0`, and permits `fee_amount == 0`. Return `2001` for a missing field and `2002` for invalid money before starting a transaction.

Keep the current serial/bill generation and POC defaults. Return the created row through `cnaps_put_voucher`.

- [ ] **Step 6: Implement load-check-merge-update for native update**

Use this control flow in `CNAPS5701U`:

```c
rc = db_find_voucher(bill_id, &current);
if (rc == 1) {
    cnaps_return_error(rqst, "3001", "单据不存在");
    return;
}
if (rc != 0) {
    cnaps_return_error(rqst, "4001", "database error");
    return;
}
if (strcmp(current.status, CNAPS_STATUS_PENDING_REVIEW) != 0
    && strcmp(current.status, CNAPS_STATUS_REJECTED) != 0) {
    cnaps_return_error(rqst, "3003", "当前状态不允许操作");
    return;
}
```

Overlay only supplied mutable business fields onto `current`, keep bill/serial/original operator/branch/created timestamp unchanged, set status pending, clear reject/checker data, set last action/operator/request, and persist all documented mutable columns in one `UPDATE ... WHERE BILL_ID=:bill_id`. Treat an affected-row count of zero as `3001` and OCI failures as `4001`.

- [ ] **Step 7: Implement native logical delete and review state checks**

For delete, load the row, return `3001` when absent, permit only pending/rejected, set delete fields and status deleted, then persist.

For review pass/return, load the row, return `3001` when absent, require pending status or return `3004`, and never compare checker/operator values. Return requires nonblank `REJECT_REASON`. Write the fixed POC operator into checker/last-operator fields only as stored metadata.

- [ ] **Step 8: Run native source-contract and full Java tests**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest test`

Expected: PASS with 0 failures.

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS with 0 failures.

- [ ] **Step 9: Commit**

```bash
git add tuxedo-server/include/cnaps_db.h tuxedo-server/include/cnaps_fields.h tuxedo-server/include/cnaps_service.h tuxedo-server/src/common/db_helper.c tuxedo-server/src/common/fml_helper.c tuxedo-server/src/services/cnaps_create.c tuxedo-server/src/services/cnaps_update.c tuxedo-server/src/services/cnaps_delete.c tuxedo-server/src/services/cnaps_review.c tuxedo-server/fml/cnaps_poc.fml32 web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java
git commit -m "feat(tuxedo): complete v03 voucher lifecycle"
```

---

### Task 5: Complete Native Queries, Reference Data, and Jolt Page Shaping

**Files:**
- Create: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltPagedResponseTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `tuxedo-server/include/cnaps_db.h`
- Modify: `tuxedo-server/include/cnaps_service.h`
- Modify: `tuxedo-server/src/common/db_helper.c`
- Modify: `tuxedo-server/src/common/fml_helper.c`
- Modify: `tuxedo-server/src/services/cnaps_query.c`
- Modify: `tuxedo-server/src/services/bank_query.c`
- Modify: `tuxedo-server/src/services/dict_query.c`
- Modify: `tuxedo-server/fml/cnaps_poc.fml32`
- Modify: `tuxedo/jolt/cnaps_services.bulk`

**Interfaces:**
- Produces: repeated FML32 occurrences for query records.
- Produces: Jolt `_DATA` page map with `PAGE_NO`, `PAGE_SIZE`, `TOTAL`, and `RECORDS`.
- Produces: attachment-compatible dictionary arrays and bank pages.

- [ ] **Step 1: Write the failing Jolt repeated-record page test**

Create `JoltPagedResponseTest` with a fake remote service exposing two occurrences:

```java
package com.ruisui.cnaps.web.tuxedo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JoltPagedResponseTest {
    @Test
    void shapesRepeatedVoucherFieldsIntoRecords() throws Exception {
        JoltTuxedoClient client = new JoltTuxedoClient(TuxedoRuntimeConfig.defaults("jolt"));
        Method method = JoltTuxedoClient.class.getDeclaredMethod(
            "readResponseFields",
            String.class,
            Class.class,
            Object.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) method.invoke(
            client,
            "CNAPS4609Q",
            FakePagedService.class,
            new FakePagedService()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) fields.get("_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("RECORDS");

        assertThat(data).containsEntry("PAGE_NO", 1).containsEntry("PAGE_SIZE", 10).containsEntry("TOTAL", 2);
        assertThat(records).extracting(record -> record.get("BILL_ID"))
            .containsExactly("BILL-1", "BILL-2");
    }

    public static final class FakePagedService {
        public int getIntDef(String name, int defaultValue) {
            return switch (name) {
                case "PAGE_NO" -> 1;
                case "PAGE_SIZE" -> 10;
                case "TOTAL_ELEMENTS" -> 2;
                default -> defaultValue;
            };
        }

        public String getStringItemDef(String name, int occurrence, String defaultValue) {
            if ("BILL_ID".equals(name) && occurrence < 2) {
                return "BILL-" + (occurrence + 1);
            }
            if ("STATUS".equals(name) && occurrence < 2) {
                return "10_PENDING_REVIEW";
            }
            return defaultValue;
        }
    }
}
```

- [ ] **Step 2: Add failing source/metadata assertions and verify RED**

Extend `DeploymentArtifactTest` to require general-query Jolt inputs `WORK_DATE`, `BRANCH_NO`, `SERIAL_NO`, `VOUCHER_NO`, `PAYEE_ACCT`, `INCLUDE_DELETED`, `PAGE_NO`, and `PAGE_SIZE`, plus all record output fields.

Run: `mvn -f web-fe/pom.xml -Dtest=JoltPagedResponseTest,JoltTuxedoClientTest,DeploymentArtifactTest test`

Expected: FAIL because `readResponseFields` is flat and metadata/query output is incomplete.

- [ ] **Step 3: Add occurrence-aware FML32 helpers**

Declare and implement:

```c
int cnaps_put_string_occurrence(
    FBFR32 *fbfr,
    const char *field_name,
    FLDOCC32 occurrence,
    const char *value
);
void cnaps_put_voucher_occurrence(FBFR32 *fbfr, const void *row, FLDOCC32 occurrence);
```

Use `Fchg32(fbfr, field_id, occurrence, ...)` and write each query row at the same occurrence index for every voucher output field. Keep `cnaps_put_voucher` as occurrence zero for detail/write responses.

- [ ] **Step 4: Replace count-only OCI query with filtered paged rows**

Change the DB interface to:

```c
int db_query_vouchers(
    const char *work_date,
    const char *branch_no,
    const char *status,
    const char *serial_no,
    const char *voucher_no,
    const char *payee_name,
    const char *payee_account_no,
    int include_deleted,
    int page_no,
    int page_size,
    cnaps_voucher_row *rows,
    int row_capacity,
    int *total
);
```

Use one `COUNT(1)` statement and one ordered page statement with these predicates:

```sql
WHERE (:work_date IS NULL OR WORK_DATE=TO_DATE(:work_date, 'YYYY-MM-DD'))
  AND (:branch_no IS NULL OR BRANCH_NO=:branch_no)
  AND (:status IS NULL OR STATUS=:status)
  AND (:serial_no IS NULL OR SERIAL_NO=:serial_no)
  AND (:voucher_no IS NULL OR VOUCHER_NO=:voucher_no)
  AND (:payee_name IS NULL OR PAYEE_NAME LIKE '%' || :payee_name || '%')
  AND (:payee_account_no IS NULL OR PAYEE_ACCOUNT_NO=:payee_account_no)
  AND (:include_deleted=1 OR STATUS<>'40_DELETED')
ORDER BY BILL_ID
OFFSET :offset_rows ROWS FETCH NEXT :page_size ROWS ONLY
```

Hydrate each page row with the same full-row define helper used by detail.

- [ ] **Step 5: Return native voucher pages through repeated FML32 fields**

In both query services, default page number to 1 and page size to 10, pass the forced pending status for `CNAPS5702Q`, and write:

```c
cnaps_put_long(fbfr, CNAPS_F_PAGE_NO, page_no);
cnaps_put_long(fbfr, CNAPS_F_PAGE_SIZE, page_size);
cnaps_put_long(fbfr, CNAPS_F_TOTAL_ELEMENTS, total);
for (int i = 0; i < row_count; ++i) {
    cnaps_put_voucher_occurrence(fbfr, &rows[i], (FLDOCC32)i);
}
```

Apply `serialNo` to the review-list query. Return `4001` only for OCI errors.

- [ ] **Step 6: Shape repeated Jolt fields into page data**

Change the private reader signature to include `serviceName`. For `BANKQRY`, `CNAPS4609Q`, and `CNAPS5702Q`, read page metadata once, then call `getStringItemDef(name, occurrence, null)` for each documented record field until `min(pageSize, total)` rows or a missing primary field. Store this canonical structure:

```java
Map<String, Object> page = new LinkedHashMap<>();
page.put("PAGE_NO", pageNo);
page.put("PAGE_SIZE", pageSize);
page.put("TOTAL", total);
page.put("RECORDS", records);
fields.put("_DATA", page);
```

For `DICTQRY`, build `_DATA` as a list of occurrences. Leave health, detail, and write responses as single maps. Update `TuxedoResponseMapper` to map `_DATA` recursively as it already does.

- [ ] **Step 7: Complete dictionary/bank native outputs and Jolt metadata**

Return the documented POC dictionary item for each supported type and `2003` for unknown types. For the single seeded bank, apply the documented exact/fuzzy filters and return an empty page when it does not match.

Update `cnaps_poc.fml32` and `cnaps_services.bulk` with `CITY`, all query inputs, `TOTAL_ELEMENTS`, and every repeated record field. Do not add business headers or account fields to the HTTP layer; these are internal FML32 parameters.

- [ ] **Step 8: Run focused tests and the full suite**

Run: `mvn -f web-fe/pom.xml -Dtest=JoltPagedResponseTest,JoltTuxedoClientTest,DeploymentArtifactTest,TuxedoCSourceContractTest test`

Expected: PASS with 0 failures.

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS with 0 failures.

- [ ] **Step 9: Commit**

```bash
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltPagedResponseTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java tuxedo-server/include/cnaps_db.h tuxedo-server/include/cnaps_service.h tuxedo-server/src/common/db_helper.c tuxedo-server/src/common/fml_helper.c tuxedo-server/src/services/cnaps_query.c tuxedo-server/src/services/bank_query.c tuxedo-server/src/services/dict_query.c tuxedo-server/fml/cnaps_poc.fml32 tuxedo/jolt/cnaps_services.bulk
git commit -m "feat(tuxedo): return v03 paged query data"
```

---

### Task 6: Publish the Exact API Contract and Run End-to-End Verification

**Files:**
- Modify: `docs/cnaps-frontend-api.md`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify only if required by a failing existing check: `scripts/smoke-test.sh`

**Interfaces:**
- Produces: one current public API document matching `poc-api.md` and the approved identity simplification.
- Produces: repeatable build and contract-verification evidence.

- [ ] **Step 1: Add failing documentation contract assertions**

Add a test that requires the three-field envelope, body/query `workDate`, `pageNo`/`pageSize`, and the simplified review rule, while rejecting stale public-contract text:

```java
@Test
void frontendApiDocumentsTheApprovedHeaderlessV03Contract() throws Exception {
    String api = Files.readString(root.resolve("docs/cnaps-frontend-api.md"));

    assertThat(api).contains(
        "`respCode`",
        "`respMsg`",
        "`data`",
        "创建时必填",
        "修改时可选",
        "`pageNo`",
        "`pageSize`",
        "不校验复核人与录入人是否相同"
    ).doesNotContain(
        "| `success`",
        "| `page` |",
        "| `size` |",
        "不能复核本人录入单据",
        "`3005`",
        "-H \"requestId:",
        "-H \"operatorNo:",
        "-H \"branchNo:",
        "-H \"workDate:"
    );
}
```

- [ ] **Step 2: Run the documentation contract and verify RED**

Run: `mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest test`

Expected: FAIL on stale response, pagination, or review-rule text.

- [ ] **Step 3: Rewrite the current frontend API document to the approved contract**

Keep the eleven endpoint sections and their request/response examples. Ensure every JSON-body curl example uses only the protocol `Content-Type` header, create includes body `workDate`, update describes optional body `workDate`, collection queries describe optional query `workDate`, and review sections explicitly state that the fixed POC operator is not identity-checked.

Document error codes `0000`, `2001`, `2002`, `2003`, `3001`, `3003`, `3004`, `4001`, `4002`, `4003`, and `9999`; omit active `3005` behavior. Use only `pageNo` and `pageSize` in public examples.

- [ ] **Step 4: Update the smoke script only when its existing requests violate the approved contract**

If `scripts/smoke-test.sh` still sends business headers, remove those flags. Ensure create JSON contains a generated or configured `workDate`, and preserve existing environment/deployment behavior. Do not add credentials, login steps, or account selection.

- [ ] **Step 5: Run stale-contract searches**

Run:

```bash
rg -n 'getHeader\("(requestId|X-Request-Id|operatorNo|branchNo|workDate)"\)|-H "(requestId|operatorNo|branchNo|workDate):|不能复核本人录入单据|"success"\s*:|\| `page` \||\| `size` \|' web-fe/src/main docs/cnaps-frontend-api.md scripts/smoke-test.sh
```

Expected: no matches. `Content-Type` matches are allowed and must remain for JSON requests.

- [ ] **Step 6: Run the complete Java verification**

Run: `mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest,V03TuxedoContractTest test`

Expected: PASS and explicitly retain all eleven mappings, including `GET /api/health` → `SYSHEALTH` and `GET /api/cnaps/vouchers/{billId}` → `CNAPS5702I`.

Run: `mvn -f web-fe/pom.xml clean test package`

Expected: BUILD SUCCESS, 0 test failures, and `web-fe/target/ruisui-bank-sim.war` exists.

- [ ] **Step 7: Run repository script contracts**

Run: `bash scripts/tests/cnapsctl-test.sh`

Expected: PASS.

Run: `bash scripts/tests/install-systemd-test.sh`

Expected: PASS.

If Bash is unavailable on the current Windows host, record that limitation and run these two commands in the configured Linux/Tuxedo environment before deployment; do not claim they passed locally.

- [ ] **Step 8: Review the final diff against the approved design**

Run: `git diff --check HEAD~5..HEAD`

Expected: no whitespace errors.

Run: `git status --short`

Expected: only the intended documentation/test changes for this task before commit.

Verify line by line that every requirement in `docs/superpowers/specs/2026-07-10-headerless-v03-poc-api-design.md` has a corresponding passing test or documented environment-only check.

- [ ] **Step 9: Commit**

```bash
git add docs/cnaps-frontend-api.md web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java scripts/smoke-test.sh
git commit -m "docs: publish headerless v03 poc api"
```

---

## Final Verification Checklist

- [ ] `mvn -f web-fe/pom.xml clean test package` reports BUILD SUCCESS.
- [ ] The generated WAR exists at `web-fe/target/ruisui-bank-sim.war`.
- [ ] No HTTP business-header parsing or examples remain in active code/documentation.
- [ ] HTTP response JSON has exactly `respCode`, `respMsg`, and `data`.
- [ ] Mock and native source contracts cover the same create/query/detail/update/delete/review behavior.
- [ ] All eleven HTTP routes still map to `SYSHEALTH`, `DICTQRY`, `BANKQRY`, `CNAPS5701E`, `CNAPS5701U`, `CNAPS5701D`, `CNAPS4609Q`, `CNAPS5702Q`, `CNAPS5702I`, `CNAPS5702A`, and `CNAPS5702R`.
- [ ] The same fixed POC operator can complete create and review.
- [ ] Query pagination uses only `pageNo` and `pageSize` publicly.
- [ ] `workDate` is create-body required, update-body optional, and collection-query optional.
- [ ] No account, login, role, or permission subsystem was added.
- [ ] Bash script test status is reported accurately for the environment in which it was run.
