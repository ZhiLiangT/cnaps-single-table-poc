# CNAPS Single-Table POC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Linux single-table POC for the CNAPS old-bank Tuxedo backend simulation described in `ruisui-bank-tuxedo-light-prd-linux-v0.3-single-table-poc.docx`.

**Architecture:** A Spring Boot WebFE POC exposes the PRD HTTP APIs and delegates business rules to a service layer that represents the Tuxedo transaction services. The runtime persistence model is a single JPA entity aligned to Oracle table `T_CNAPS_BILL_POC`, with H2 in Oracle mode for local tests and generated Linux/Tuxedo/Oracle deployment skeletons for target runtime.

**Tech Stack:** Java 17, Maven, Spring Boot 3.3.x, Spring Web, Spring Data JPA, H2 test database, JUnit 5, AssertJ, MockMvc, Oracle-compatible SQL artifacts, Tuxedo UBBCONFIG/FML32 text artifacts.

## Global Constraints

- Implementation target is v0.3 single-table POC, not the larger v1.1 production-style design.
- Current workspace has no existing source modules; scaffold from scratch in `D:\Project\ruisui`.
- No login, logout, session expiry, password, captcha, user-role-permission, menu, or button authorization.
- No real CNAPS, CIPS, TIPS, core banking, account debit, fee posting, certificate, hardware, AIX, HA, or external system integration.
- No multi-table audit, dictionary, bank-info, serial, request-log, or bill-flow schema in the POC runtime.
- Runtime business table is equivalent to `T_CNAPS_BILL_POC`; Oracle DDL is authoritative for target deployment.
- Local automated verification uses H2 in Oracle compatibility mode because this workspace cannot run Oracle Tuxedo or Oracle Database.
- Every business API uses headers `requestId`, `operatorNo`, `branchNo`, `workDate`, and optional `channel`.
- Supported status values are `10_PENDING_REVIEW`, `20_REVIEW_APPROVED`, `30_REVIEW_REJECTED`, and `40_DELETED`.
- Supported dictionary values are `BUSINESS_TYPE=02102`, `PRIORITY=NORM`, `FEE_CHARGE_MODE=1`, `SEND_MODE=0`, `DEBIT_MODE=1`, `FAX_FLAG=0/1`, `SYSTEM_TYPE=CNAPS`.
- Serial numbers start from `0002000` for the first `(workDate, branchNo)` voucher in the local POC.
- Keep pre-existing untracked PRD files out of implementation commits unless the user asks to track them.

---

## File Structure

- Create `web-war/pom.xml`: Maven project and dependency manifest.
- Create `web-war/src/main/java/com/ruisui/bank/sim/RuisuiBankSimApplication.java`: Spring Boot entry point.
- Create `web-war/src/main/java/com/ruisui/bank/sim/api/ApiResponse.java`: common response envelope.
- Create `web-war/src/main/java/com/ruisui/bank/sim/api/ReferenceController.java`: health, dictionary, and bank lookup endpoints.
- Create `web-war/src/main/java/com/ruisui/bank/sim/api/CnapsVoucherController.java`: CNAPS voucher HTTP endpoints.
- Create `web-war/src/main/java/com/ruisui/bank/sim/api/dto/*.java`: request/response DTOs.
- Create `web-war/src/main/java/com/ruisui/bank/sim/domain/*.java`: error codes, statuses, actions, and business exceptions.
- Create `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPoc.java`: JPA entity for `T_CNAPS_BILL_POC`.
- Create `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPocRepository.java`: repository query methods.
- Create `web-war/src/main/java/com/ruisui/bank/sim/service/*.java`: request header parsing, dictionary/bank data, validation, serial generation, voucher lifecycle.
- Create `web-war/src/main/resources/application.yml`: local H2 datasource and JPA settings.
- Create `web-war/src/test/java/com/ruisui/bank/sim/*Test.java`: HTTP integration tests.
- Create `sql/schema.sql`: Oracle single-table DDL and indexes.
- Create `tuxedo-server/fml/cnaps_poc.fml32`: PRD FML32 field table.
- Create `tuxedo/UBBCONFIG`: single-server Tuxedo domain skeleton.
- Create `conf/env.linux.sh`, `conf/dicts.properties`, `conf/banks.properties`: Linux POC config templates.
- Create `scripts/start.sh`, `scripts/stop.sh`, `scripts/status.sh`: runtime helper scripts.

---

### Task 1: Project Scaffold And Reference Endpoints

**Files:**
- Create: `web-war/pom.xml`
- Create: `web-war/src/test/java/com/ruisui/bank/sim/HealthAndReferenceApiTest.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/RuisuiBankSimApplication.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/ApiResponse.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/ReferenceController.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/domain/ErrorCode.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/domain/BusinessException.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/GlobalExceptionHandler.java`
- Create: `web-war/src/main/resources/application.yml`

**Interfaces:**
- Consumes: approved design spec only.
- Produces: `GET /api/health`, `GET /api/dicts/{dictType}`, `GET /api/banks`, `ApiResponse<T>`, `ErrorCode`, `BusinessException`.

- [ ] **Step 1: Write the failing health/reference API test**

Create `web-war/pom.xml` with Java 17, Spring Boot Web, Data JPA, H2, and test dependencies. Then create `web-war/src/test/java/com/ruisui/bank/sim/HealthAndReferenceApiTest.java`:

```java
package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthAndReferenceApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsSuccessAndPocServiceList() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.respCode").value("0000"))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.services[0]").value("SYSHEALTH"));
    }

    @Test
    void dictionaryQueryReturnsConfiguredBusinessType() throws Exception {
        mockMvc.perform(get("/api/dicts/BUSINESS_TYPE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].code").value("02102"))
            .andExpect(jsonPath("$.data[0].name").value("普通汇兑"));
    }

    @Test
    void bankQueryReturnsConfiguredReceiveBank() throws Exception {
        mockMvc.perform(get("/api/banks").queryParam("bankNo", "102290000002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].bankNo").value("102290000002"))
            .andExpect(jsonPath("$.data[0].bankName").value("接收行名称"));
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run: `mvn -f web-war/pom.xml test -Dtest=HealthAndReferenceApiTest`

Expected: FAIL during compilation because `com.ruisui.bank.sim.RuisuiBankSimApplication` and the endpoint classes do not exist.

- [ ] **Step 3: Implement minimal application and reference endpoints**

Create `RuisuiBankSimApplication`, `ApiResponse`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `ReferenceController`, and `application.yml`.

Required response model:

```java
package com.ruisui.bank.sim.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
    boolean success,
    String respCode,
    String respMsg,
    String requestId,
    OffsetDateTime serverTime,
    T data
) {
    public static <T> ApiResponse<T> ok(String requestId, String message, T data) {
        return new ApiResponse<>(true, "0000", message, requestId, OffsetDateTime.now(), data);
    }

    public static <T> ApiResponse<T> fail(String requestId, String code, String message) {
        return new ApiResponse<>(false, code, message, requestId, OffsetDateTime.now(), null);
    }
}
```

Required reference data:

```java
Map.of(
    "BUSINESS_TYPE", List.of(new DictItem("02102", "普通汇兑")),
    "PRIORITY", List.of(new DictItem("NORM", "普通")),
    "FEE_CHARGE_MODE", List.of(new DictItem("1", "同城收费")),
    "SEND_MODE", List.of(new DictItem("0", "柜面")),
    "DEBIT_MODE", List.of(new DictItem("1", "扣收")),
    "FAX_FLAG", List.of(new DictItem("0", "否"), new DictItem("1", "是")),
    "SYSTEM_TYPE", List.of(new DictItem("CNAPS", "CNAPS"))
);
```

Required service list in health response:

```java
List.of(
    "SYSHEALTH", "DICTQRY", "BANKQRY", "CNAPS5701E", "CNAPS5701U",
    "CNAPS5701D", "CNAPS4609Q", "CNAPS5702Q", "CNAPS5702I",
    "CNAPS5702A", "CNAPS5702R"
);
```

- [ ] **Step 4: Run the test to verify GREEN**

Run: `mvn -f web-war/pom.xml test -Dtest=HealthAndReferenceApiTest`

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit Task 1**

```bash
git add web-war/pom.xml web-war/src/main/java web-war/src/main/resources web-war/src/test/java
git commit -m "feat: scaffold poc web gateway"
```

---

### Task 2: Voucher Create, Validation, Persistence, Query, And Detail

**Files:**
- Create: `web-war/src/test/java/com/ruisui/bank/sim/CnapsVoucherCreateQueryApiTest.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/CnapsVoucherController.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/BankInfo.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/DictItem.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/HeaderContext.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/VoucherCreateRequest.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/VoucherResponse.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/domain/LastAction.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/domain/VoucherStatus.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPoc.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPocRepository.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/service/HeaderContextResolver.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/service/CnapsVoucherService.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/service/VoucherMapper.java`
- Modify: `web-war/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `ApiResponse<T>`, `BusinessException`, `ErrorCode`.
- Produces: `POST /api/cnaps/vouchers`, `GET /api/cnaps/vouchers`, `GET /api/cnaps/vouchers/review-list`, `GET /api/cnaps/vouchers/{billId}`.

- [ ] **Step 1: Write failing create/query/detail integration tests**

Create `CnapsVoucherCreateQueryApiTest`:

```java
package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CnapsVoucherCreateQueryApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullCreateReturnsPendingReviewAndCanBeQueriedAndDetailed() throws Exception {
        String billId = createVoucher("REQ-CREATE-001", "5600.00");

        mockMvc.perform(get("/api/cnaps/vouchers")
                .header("requestId", "REQ-QRY-001")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .queryParam("status", "10_PENDING_REVIEW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].billId").value(billId))
            .andExpect(jsonPath("$.data.content[0].serialNo").value("0002000"));

        mockMvc.perform(get("/api/cnaps/vouchers/{billId}", billId)
                .header("requestId", "REQ-DETAIL-001")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.billId").value(billId))
            .andExpect(jsonPath("$.data.status").value("10_PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.lastAction").value("CREATE"))
            .andExpect(jsonPath("$.data.payeeAccountNo").value("622200000000000001"));
    }

    @Test
    void missingPayeeAccountReturnsRequiredFieldError() throws Exception {
        String body = validCreateBody("5600.00").replace("\"payeeAccountNo\":\"622200000000000001\",", "");

        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-002")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2001"));
    }

    @Test
    void zeroAmountReturnsFormatError() throws Exception {
        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-003")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody("0.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2002"));
    }

    @Test
    void unsupportedBusinessTypeReturnsDictionaryError() throws Exception {
        String body = validCreateBody("5600.00").replace("\"businessType\":\"02102\"", "\"businessType\":\"99999\"");

        mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", "REQ-CREATE-004")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("2003"));
    }

    private String createVoucher(String requestId, String amount) throws Exception {
        String response = mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", requestId)
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody(amount)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.respCode").value("0000"))
            .andExpect(jsonPath("$.data.billId", startsWith("B20260707")))
            .andExpect(jsonPath("$.data.serialNo").value("0002000"))
            .andExpect(jsonPath("$.data.status").value("10_PENDING_REVIEW"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        int marker = response.indexOf("\"billId\":\"") + 10;
        return response.substring(marker, response.indexOf('"', marker));
    }

    static String validCreateBody(String amount) {
        return """
            {
              "businessType":"02102",
              "accountPart1":"404045",
              "accountPart2":"00772",
              "accountPart3":"000000000001",
              "accountName":"付款账户户名",
              "payerName":"付款人名称",
              "payeeAccountNo":"622200000000000001",
              "payeeName":"收款人名称",
              "priority":"NORM",
              "receiveBankNo":"102290000002",
              "receiveBankName":"接收行名称",
              "systemType":"CNAPS",
              "amount":"%s",
              "debitMode":"1",
              "feeAmount":"0.00",
              "feeChargeMode":"1",
              "sendMode":"0",
              "faxFlag":"0",
              "voucherNo":"PZ202607070001",
              "remark":"验证录入"
            }
            """.formatted(amount);
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -f web-war/pom.xml test -Dtest=CnapsVoucherCreateQueryApiTest`

Expected: FAIL with 404 for `/api/cnaps/vouchers` because the voucher controller is not implemented.

- [ ] **Step 3: Implement voucher persistence and read APIs**

Create the entity `CnapsBillPoc` with table name `T_CNAPS_BILL_POC` and fields from the spec. Use `BigDecimal` for `amount` and `feeAmount`, `LocalDate` for `workDate`, and `OffsetDateTime` for action timestamps.

Create repository methods:

```java
Optional<CnapsBillPoc> findByBillId(String billId);

@Query("""
    select coalesce(max(v.serialNo), '0001999')
    from CnapsBillPoc v
    where v.workDate = :workDate and v.branchNo = :branchNo
    """)
String findMaxSerialNo(LocalDate workDate, String branchNo);

Page<CnapsBillPoc> findByWorkDateAndBranchNoAndStatus(
    LocalDate workDate,
    String branchNo,
    String status,
    Pageable pageable
);

Page<CnapsBillPoc> findByWorkDateAndBranchNo(
    LocalDate workDate,
    String branchNo,
    Pageable pageable
);
```

Create `CnapsVoucherService` methods:

```java
public VoucherResponse create(HeaderContext context, VoucherCreateRequest request);
public Page<VoucherResponse> query(HeaderContext context, String status, String operatorNo, String serialNo, int page, int size);
public Page<VoucherResponse> reviewList(HeaderContext context, int page, int size);
public VoucherResponse detail(HeaderContext context, String billId);
```

Validation must throw:

- `new BusinessException(ErrorCode.REQUIRED_FIELD_EMPTY, "payeeAccountNo is required")` for missing payee account.
- `new BusinessException(ErrorCode.FIELD_FORMAT_ERROR, "amount must be greater than 0")` for zero amount.
- `new BusinessException(ErrorCode.DICT_VALUE_INVALID, "businessType is invalid")` for unsupported business type.

Create `CnapsVoucherController` methods for the four endpoints and cap page size to `50`.

- [ ] **Step 4: Run task tests to verify GREEN**

Run: `mvn -f web-war/pom.xml test -Dtest=CnapsVoucherCreateQueryApiTest`

Expected: PASS, 4 tests.

- [ ] **Step 5: Run regression tests**

Run: `mvn -f web-war/pom.xml test`

Expected: PASS, 7 tests.

- [ ] **Step 6: Commit Task 2**

```bash
git add web-war/src/main/java web-war/src/main/resources web-war/src/test/java
git commit -m "feat: add cnaps voucher create and query"
```

---

### Task 3: Voucher Update, Delete, Review Pass, And Review Return

**Files:**
- Create: `web-war/src/test/java/com/ruisui/bank/sim/CnapsVoucherLifecycleApiTest.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/DeleteRequest.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/ReviewPassRequest.java`
- Create: `web-war/src/main/java/com/ruisui/bank/sim/api/dto/ReviewReturnRequest.java`
- Modify: `web-war/src/main/java/com/ruisui/bank/sim/api/CnapsVoucherController.java`
- Modify: `web-war/src/main/java/com/ruisui/bank/sim/service/CnapsVoucherService.java`
- Modify: `web-war/src/main/java/com/ruisui/bank/sim/service/VoucherMapper.java`

**Interfaces:**
- Consumes: create/query/detail implementation from Task 2.
- Produces: `PUT /api/cnaps/vouchers/{billId}`, `POST /api/cnaps/vouchers/{billId}/delete`, `POST /api/cnaps/vouchers/{billId}/review-pass`, `POST /api/cnaps/vouchers/{billId}/review-return`.

- [ ] **Step 1: Write failing lifecycle integration tests**

Create `CnapsVoucherLifecycleApiTest`:

```java
package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CnapsVoucherLifecycleApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void reviewPassChangesStatusAndRepeatedReviewReturnsStatusChanged() throws Exception {
        String billId = createVoucher("REQ-LIFE-001", "77210021");

        reviewPass(billId, "REQ-LIFE-002", "77210022")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.respMsg").value("操作已成功"))
            .andExpect(jsonPath("$.data.checkerNo").value("77210022"))
            .andExpect(jsonPath("$.data.status").value("20_REVIEW_APPROVED"));

        reviewPass(billId, "REQ-LIFE-003", "77210022")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3004"));
    }

    @Test
    void inputOperatorCannotReviewOwnVoucher() throws Exception {
        String billId = createVoucher("REQ-LIFE-004", "77210021");

        reviewPass(billId, "REQ-LIFE-005", "77210021")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3005"));
    }

    @Test
    void reviewReturnStoresReasonAndUpdateResubmitsPendingReview() throws Exception {
        String billId = createVoucher("REQ-LIFE-006", "77210021");

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/review-return", billId)
                .header("requestId", "REQ-LIFE-007")
                .header("operatorNo", "77210022")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectReason\":\"收款人信息需修正\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("30_REVIEW_REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").value("收款人信息需修正"));

        mockMvc.perform(put("/api/cnaps/vouchers/{billId}", billId)
                .header("requestId", "REQ-LIFE-008")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CnapsVoucherCreateQueryApiTest.validCreateBody("6600.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("10_PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.rejectReason").doesNotExist())
            .andExpect(jsonPath("$.data.versionNo").value(3));
    }

    @Test
    void deletePendingVoucherAndRejectApprovedVoucherDelete() throws Exception {
        String pendingBillId = createVoucher("REQ-LIFE-009", "77210021");

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/delete", pendingBillId)
                .header("requestId", "REQ-LIFE-010")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deleteReason\":\"录入有误\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("40_DELETED"))
            .andExpect(jsonPath("$.data.deleteOperatorNo").value("77210021"));

        String approvedBillId = createVoucher("REQ-LIFE-011", "77210021");
        reviewPass(approvedBillId, "REQ-LIFE-012", "77210022").andExpect(status().isOk());

        mockMvc.perform(post("/api/cnaps/vouchers/{billId}/delete", approvedBillId)
                .header("requestId", "REQ-LIFE-013")
                .header("operatorNo", "77210021")
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deleteReason\":\"尝试删除已复核\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.respCode").value("3003"));
    }

    private String createVoucher(String requestId, String operatorNo) throws Exception {
        String response = mockMvc.perform(post("/api/cnaps/vouchers")
                .header("requestId", requestId)
                .header("operatorNo", operatorNo)
                .header("branchNo", "772")
                .header("workDate", "2026-07-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CnapsVoucherCreateQueryApiTest.validCreateBody("5600.00")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        int marker = response.indexOf("\"billId\":\"") + 10;
        return response.substring(marker, response.indexOf('"', marker));
    }

    private org.springframework.test.web.servlet.ResultActions reviewPass(String billId, String requestId, String operatorNo) throws Exception {
        return mockMvc.perform(post("/api/cnaps/vouchers/{billId}/review-pass", billId)
            .header("requestId", requestId)
            .header("operatorNo", operatorNo)
            .header("branchNo", "772")
            .header("workDate", "2026-07-07")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reviewComment\":\"复核通过\"}"));
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -f web-war/pom.xml test -Dtest=CnapsVoucherLifecycleApiTest`

Expected: FAIL with 404 for lifecycle endpoints or missing DTO classes.

- [ ] **Step 3: Implement lifecycle endpoints**

Add DTOs:

```java
public record DeleteRequest(String deleteReason) {}
public record ReviewPassRequest(String reviewComment) {}
public record ReviewReturnRequest(String rejectReason) {}
```

Add service methods:

```java
public VoucherResponse update(HeaderContext context, String billId, VoucherCreateRequest request);
public VoucherResponse delete(HeaderContext context, String billId, DeleteRequest request);
public VoucherResponse reviewPass(HeaderContext context, String billId, ReviewPassRequest request);
public VoucherResponse reviewReturn(HeaderContext context, String billId, ReviewReturnRequest request);
```

Rules:

- `update`: allow only `10_PENDING_REVIEW` or `30_REVIEW_REJECTED`, rerun create validation, set `status=10_PENDING_REVIEW`, clear `rejectReason`, increment `versionNo`, set `LAST_ACTION=UPDATE`.
- `delete`: allow only `10_PENDING_REVIEW` or `30_REVIEW_REJECTED`, set `status=40_DELETED`, `deleteOperatorNo=context.operatorNo()`, `deleteTime=now`, `LAST_ACTION=DELETE`, increment `versionNo`.
- `reviewPass`: if `context.operatorNo().equals(entity.operatorNo)` throw `3005`; if status is not `10_PENDING_REVIEW` throw `3004`; set `status=20_REVIEW_APPROVED`, `checkerNo`, `checkerTime`, `reviewComment`, `LAST_ACTION=REVIEW_PASS`, increment `versionNo`.
- `reviewReturn`: require nonblank `rejectReason`; reject self-review with `3005`; require status `10_PENDING_REVIEW` else `3004`; set `status=30_REVIEW_REJECTED`, `checkerNo`, `checkerTime`, `rejectReason`, `LAST_ACTION=REVIEW_RETURN`, increment `versionNo`.

- [ ] **Step 4: Run task tests to verify GREEN**

Run: `mvn -f web-war/pom.xml test -Dtest=CnapsVoucherLifecycleApiTest`

Expected: PASS, 4 tests.

- [ ] **Step 5: Run regression tests**

Run: `mvn -f web-war/pom.xml test`

Expected: PASS, 11 tests.

- [ ] **Step 6: Commit Task 3**

```bash
git add web-war/src/main/java web-war/src/test/java
git commit -m "feat: add cnaps voucher lifecycle"
```

---

### Task 4: Oracle, Tuxedo, And Linux Deployment Artifacts

**Files:**
- Create: `web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java`
- Create: `sql/schema.sql`
- Create: `tuxedo-server/fml/cnaps_poc.fml32`
- Create: `tuxedo/UBBCONFIG`
- Create: `conf/env.linux.sh`
- Create: `conf/dicts.properties`
- Create: `conf/banks.properties`
- Create: `scripts/start.sh`
- Create: `scripts/stop.sh`
- Create: `scripts/status.sh`

**Interfaces:**
- Consumes: PRD artifact requirements from approved spec.
- Produces: verifiable Oracle DDL, FML32 field table, UBBCONFIG service registration, Linux config templates, helper scripts.

- [ ] **Step 1: Write failing artifact verification test**

Create `DeploymentArtifactTest`:

```java
package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactTest {
    private final Path root = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void oracleSchemaContainsOnlyPocBusinessTableAndIndexes() throws Exception {
        String schema = Files.readString(root.resolve("sql/schema.sql"));

        assertThat(schema).contains("CREATE TABLE T_CNAPS_BILL_POC");
        assertThat(schema).contains("CREATE UNIQUE INDEX UK_CNAPS_BILL_POC_SERIAL");
        assertThat(schema).contains("CREATE INDEX IDX_CNAPS_BILL_POC_QRY");
        assertThat(schema).doesNotContain("CREATE TABLE T_CNAPS_BILL_FLOW");
        assertThat(schema).doesNotContain("CREATE TABLE T_SYS_DICT");
        assertThat(schema).doesNotContain("CREATE TABLE T_BANK_INFO");
    }

    @Test
    void tuxedoArtifactsExposeRequiredServicesAndFields() throws Exception {
        String ubb = Files.readString(root.resolve("tuxedo/UBBCONFIG"));
        String fml = Files.readString(root.resolve("tuxedo-server/fml/cnaps_poc.fml32"));

        assertThat(ubb).contains("cnapspocsvr");
        assertThat(ubb).contains("SYSHEALTH", "DICTQRY", "BANKQRY", "CNAPS5701E", "CNAPS5701U", "CNAPS5701D");
        assertThat(ubb).contains("CNAPS4609Q", "CNAPS5702Q", "CNAPS5702I", "CNAPS5702A", "CNAPS5702R");
        assertThat(fml).contains("SYS_ID", "TXN_CODE", "REQ_ID", "OPERATOR_NO", "BRANCH_NO", "WORK_DATE");
        assertThat(fml).contains("BILL_ID", "SERIAL_NO", "BUSINESS_TYPE", "AMOUNT", "STATUS", "PAYEE_ACCT");
    }

    @Test
    void linuxConfigAndScriptsTargetPocAppHome() throws Exception {
        String env = Files.readString(root.resolve("conf/env.linux.sh"));
        String start = Files.readString(root.resolve("scripts/start.sh"));
        String stop = Files.readString(root.resolve("scripts/stop.sh"));
        String status = Files.readString(root.resolve("scripts/status.sh"));

        assertThat(env).contains("APP_HOME=/opt/ruisui-bank-sim");
        assertThat(env).contains("LD_LIBRARY_PATH");
        assertThat(start).contains("tmboot -y");
        assertThat(stop).contains("tmshutdown -y");
        assertThat(status).contains("tmadmin");
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -f web-war/pom.xml test -Dtest=DeploymentArtifactTest`

Expected: FAIL with missing files under `sql`, `tuxedo`, `tuxedo-server/fml`, `conf`, and `scripts`.

- [ ] **Step 3: Create deployment artifacts**

Create `sql/schema.sql` with one `CREATE TABLE T_CNAPS_BILL_POC` statement and two indexes:

```sql
CREATE TABLE T_CNAPS_BILL_POC (
  BILL_ID VARCHAR2(32) PRIMARY KEY,
  WORK_DATE DATE NOT NULL,
  BRANCH_NO VARCHAR2(12) NOT NULL,
  OPERATOR_NO VARCHAR2(16) NOT NULL,
  SERIAL_NO VARCHAR2(16) NOT NULL,
  BUSINESS_TYPE VARCHAR2(12) NOT NULL,
  ACCOUNT_PART1 VARCHAR2(32),
  ACCOUNT_PART2 VARCHAR2(32),
  ACCOUNT_PART3 VARCHAR2(64),
  ACCOUNT_NAME VARCHAR2(128),
  PAYER_NAME VARCHAR2(128),
  PAYEE_ACCOUNT_NO VARCHAR2(64) NOT NULL,
  PAYEE_NAME VARCHAR2(128) NOT NULL,
  PRIORITY VARCHAR2(12),
  RECEIVE_BANK_NO VARCHAR2(32),
  RECEIVE_BANK_NAME VARCHAR2(128),
  SYSTEM_TYPE VARCHAR2(16),
  AMOUNT NUMBER(18,2) NOT NULL,
  DEBIT_MODE VARCHAR2(8),
  FEE_AMOUNT NUMBER(18,2) DEFAULT 0,
  FEE_CHARGE_MODE VARCHAR2(8),
  SEND_MODE VARCHAR2(8),
  FAX_FLAG VARCHAR2(1),
  VOUCHER_NO VARCHAR2(64),
  REMARK VARCHAR2(512),
  STATUS VARCHAR2(32) NOT NULL,
  CHECKER_NO VARCHAR2(16),
  CHECKER_TIME TIMESTAMP,
  REVIEW_COMMENT VARCHAR2(512),
  REJECT_REASON VARCHAR2(200),
  DELETE_REASON VARCHAR2(200),
  DELETE_OPERATOR_NO VARCHAR2(16),
  DELETE_TIME TIMESTAMP,
  LAST_ACTION VARCHAR2(32),
  LAST_OPERATOR_NO VARCHAR2(16),
  LAST_REQUEST_ID VARCHAR2(32),
  LAST_ACTION_TIME TIMESTAMP,
  CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP,
  UPDATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP,
  VERSION_NO NUMBER(10) DEFAULT 1
);

CREATE UNIQUE INDEX UK_CNAPS_BILL_POC_SERIAL
  ON T_CNAPS_BILL_POC(WORK_DATE, BRANCH_NO, SERIAL_NO);

CREATE INDEX IDX_CNAPS_BILL_POC_QRY
  ON T_CNAPS_BILL_POC(WORK_DATE, BRANCH_NO, STATUS, OPERATOR_NO, SERIAL_NO);
```

Create `tuxedo-server/fml/cnaps_poc.fml32` with the common and voucher fields:

```text
# cnaps_poc.fml32
SYS_ID          10001   string
TXN_CODE        10002   string
REQ_ID          10003   string
OPERATOR_NO     10004   string
BRANCH_NO       10005   string
WORK_DATE       10006   string
RESP_CODE       10007   string
RESP_MSG        10008   string
BILL_ID         11001   string
SERIAL_NO       11002   string
BUSINESS_TYPE   11003   string
AMOUNT          11004   string
STATUS          11005   string
PAYEE_ACCT      11006   string
PAYEE_NAME      11007   string
REJECT_REASON   11008   string
REVIEW_COMMENT  11009   string
LAST_ACTION     11010   string
```

Create `tuxedo/UBBCONFIG` with `cnapspocsvr` and the required services. Create `conf/env.linux.sh` using `/opt/ruisui-bank-sim`, `LD_LIBRARY_PATH`, `TUXCONFIG`, `FLDTBLDIR32`, and `FIELDTBLS32`. Create `conf/dicts.properties` and `conf/banks.properties` with the dictionary and bank values from the PRD. Create scripts that source `conf/env.linux.sh` and run `tmboot -y`, `tmshutdown -y`, or `tmadmin`.

- [ ] **Step 4: Run artifact tests to verify GREEN**

Run: `mvn -f web-war/pom.xml test -Dtest=DeploymentArtifactTest`

Expected: PASS, 3 tests.

- [ ] **Step 5: Run all tests**

Run: `mvn -f web-war/pom.xml test`

Expected: PASS, 14 tests.

- [ ] **Step 6: Commit Task 4**

```bash
git add sql tuxedo-server tuxedo conf scripts web-war/src/test/java
git commit -m "chore: add linux tuxedo oracle poc artifacts"
```

---

### Task 5: Final Verification And Completion Audit

**Files:**
- Modify if needed: `README.md`
- Read: `docs/superpowers/specs/2026-07-07-cnaps-single-table-poc-design.md`
- Read: `docs/superpowers/plans/2026-07-07-cnaps-single-table-poc.md`

**Interfaces:**
- Consumes: all implemented artifacts.
- Produces: verified build/test evidence and a concise user-facing completion summary.

- [ ] **Step 1: Run full Maven verification**

Run: `mvn -f web-war/pom.xml test`

Expected: PASS, 14 tests.

- [ ] **Step 2: Verify service names and deployment artifacts**

Run:

```powershell
rg -n "SYSHEALTH|DICTQRY|BANKQRY|CNAPS5701E|CNAPS5701U|CNAPS5701D|CNAPS4609Q|CNAPS5702Q|CNAPS5702I|CNAPS5702A|CNAPS5702R" tuxedo tuxedo-server web-war
rg -n "CREATE TABLE T_CNAPS_BILL_POC|T_CNAPS_BILL_FLOW|T_SYS_DICT|T_BANK_INFO" sql/schema.sql
```

Expected:

- First command finds all required service names.
- Second command finds `CREATE TABLE T_CNAPS_BILL_POC`.
- Second command does not find `CREATE TABLE T_CNAPS_BILL_FLOW`, `CREATE TABLE T_SYS_DICT`, or `CREATE TABLE T_BANK_INFO`.

- [ ] **Step 3: Check Git status**

Run: `git status --short --branch`

Expected: only the pre-existing PRD docs remain untracked unless the user asks to track them.

- [ ] **Step 4: Commit final README if added**

If `README.md` is created during final verification, run:

```bash
git add README.md
git commit -m "docs: add poc usage notes"
```

If `README.md` is not created, skip this commit.

- [ ] **Step 5: Completion audit**

Check each accepted spec requirement against evidence:

- Runnable HTTP APIs: proven by MockMvc tests.
- Single-table runtime model: proven by entity, schema test, and `schema.sql`.
- Lifecycle create/update/delete/query/detail/review-pass/review-return: proven by `CnapsVoucherCreateQueryApiTest` and `CnapsVoucherLifecycleApiTest`.
- v0.3 validation and state transitions: proven by tests for `2001`, `2002`, `2003`, `3003`, `3004`, and `3005`.
- Deployment artifacts: proven by `DeploymentArtifactTest` and `rg` output.
- External runtime caveat: no claim that real Oracle Tuxedo or Oracle Database was executed in this Windows workspace.

- [ ] **Step 6: Mark goal complete only if all evidence passes**

If every item above passes, call `update_goal` with `status="complete"` and report the final state to the user.
