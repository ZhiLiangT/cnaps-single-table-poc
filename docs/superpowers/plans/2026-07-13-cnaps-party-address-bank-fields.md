# CNAPS Party Address and Payer Bank Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional payer address, payee address, and payer bank name fields to voucher create/update persistence and detail responses without adding headers, accounts, query filters, or list fields.

**Architecture:** Keep the existing Servlet → request mapper → Mock/Jolt client → FML32/Tuxedo → OCI/Oracle flow. Store the three fields in nullable Oracle columns, treat omitted update fields as unchanged and explicit empty strings as clears, and use detail-only output helpers so paged lists keep their current shape.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Maven, Oracle SQL/PLSQL, C, Oracle OCI, Tuxedo FML32, Jolt bulk metadata.

## Global Constraints

- JSON names are exactly `payerAddress`, `payeeAddress`, and `payerBankName`.
- Internal and database names are exactly `PAYER_ADDRESS`, `PAYEE_ADDRESS`, and `PAYER_BANK_NAME`.
- `payerAddress` and `payeeAddress` are optional and limited to 256 Unicode characters.
- `payerBankName` is optional and limited to 128 Unicode characters.
- Create stores supplied values; update omits = preserve, non-empty = replace, empty string = clear.
- Detail returns all three fields; general and review-list records do not return them.
- Do not add business headers, account/authentication logic, query filters, tables, status rules, or error codes.
- Preserve the untracked `.idea/` directory and unrelated working-tree changes.

---

## File Map

- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`: JSON-to-FML32 names.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`: detail FML32-to-JSON names.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`: fields read from single-record Jolt responses.
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`: Mock validation, storage, update semantics, and list projection.
- `tuxedo-server/include/cnaps_db.h`: OCI row buffers.
- `tuxedo-server/include/cnaps_fields.h`: new symbolic FML32 names.
- `tuxedo-server/include/cnaps_service.h`: shared text-length and detail-output interfaces.
- `tuxedo-server/src/common/validation_helper.c`: optional UTF-8 text length validation.
- `tuxedo-server/src/common/fml_helper.c`: detail-only FML32 output.
- `tuxedo-server/src/common/db_helper.c`: OCI binds, DML, select/define, and null clearing.
- `tuxedo-server/src/services/cnaps_create.c`: create request extraction and validation.
- `tuxedo-server/src/services/cnaps_update.c`: update overlay, clear, and validation semantics.
- `tuxedo-server/src/services/cnaps_query.c`: detail-only field output call.
- `tuxedo-server/fml/cnaps_poc.fml32`: physical FML32 field IDs.
- `tuxedo/jolt/cnaps_services.bulk`: create/update input and detail output metadata.
- `sql/010_create_tables.sql`, `sql/schema.sql`: fresh database schema.
- `sql/040_add_party_address_bank_fields.sql`: repeatable existing-database migration.
- `scripts/init-db.sh`: include the repeatable migration in fresh initialization.
- `docs/cnaps-frontend-api.md`, `docs/cnaps-api-database.md`: public contract and database documentation.

### Task 1: WebFE and Mock Contract

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/V03TuxedoContractTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientV03ContractTest.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`

**Interfaces:**
- Consumes: the approved JSON/FML names and update semantics in Global Constraints.
- Produces: Mock CRUD behavior and canonical FML fields used by Tasks 2 and 3.

- [ ] **Step 1: Write failing mapper and Mock lifecycle tests**

Extend `TuxedoRequestMapperTest.mapsHeadersAndJsonBodyToCanonicalFmlFieldNames()` with:

```java
"payerAddress", "上海市浦东新区",
"payeeAddress", "北京市朝阳区",
"payerBankName", "中国示例银行上海分行"
```

and assert:

```java
.containsEntry("PAYER_ADDRESS", "上海市浦东新区")
.containsEntry("PAYEE_ADDRESS", "北京市朝阳区")
.containsEntry("PAYER_BANK_NAME", "中国示例银行上海分行");
```

Add to `V03TuxedoContractTest`:

```java
@Test
void mapsPartyAddressAndPayerBankDetailFieldsToJsonNames() {
    ApiResponse<Object> response = responseMapper.toApiResponse(
        "REQ-FIELDS-1",
        TuxedoResponse.ok("查询成功", Map.of(
            "PAYER_ADDRESS", "上海市浦东新区",
            "PAYEE_ADDRESS", "北京市朝阳区",
            "PAYER_BANK_NAME", "中国示例银行上海分行"
        ))
    );

    assertThat((Map<?, ?>) response.data())
        .containsEntry("payerAddress", "上海市浦东新区")
        .containsEntry("payeeAddress", "北京市朝阳区")
        .containsEntry("payerBankName", "中国示例银行上海分行");
}
```

Add to `MockTuxedoClientV03ContractTest` lifecycle coverage that creates a voucher with all three fields, verifies detail, updates one value while omitting another, clears the third with `""`, and verifies both paged endpoints omit all three keys:

```java
@Test
void partyAddressFieldsRoundTripUpdateClearAndStayOutOfLists() {
    TuxedoResponse created = client.call("CNAPS5701E", request(Map.of(
        "PAYER_ADDRESS", "付款地址-原值",
        "PAYEE_ADDRESS", "收款地址-原值",
        "PAYER_BANK_NAME", "开户行-原值"
    )));
    Object billId = created.fields().get("BILL_ID");

    TuxedoResponse original = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));
    assertThat(original.fields())
        .containsEntry("PAYER_ADDRESS", "付款地址-原值")
        .containsEntry("PAYEE_ADDRESS", "收款地址-原值")
        .containsEntry("PAYER_BANK_NAME", "开户行-原值");

    client.call("CNAPS5701U", request(Map.of(
        "BILL_ID", billId,
        "PAYER_ADDRESS", "付款地址-新值",
        "PAYER_BANK_NAME", ""
    )));
    TuxedoResponse updated = client.call("CNAPS5702I", request(Map.of("BILL_ID", billId)));
    assertThat(updated.fields())
        .containsEntry("PAYER_ADDRESS", "付款地址-新值")
        .containsEntry("PAYEE_ADDRESS", "收款地址-原值")
        .containsEntry("PAYER_BANK_NAME", "");

    for (String service : List.of("CNAPS4609Q", "CNAPS5702Q")) {
        Map<String, Object> record = records(page(client.call(service, request(Map.of())))).get(0);
        assertThat(record).doesNotContainKeys("PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME");
    }
}
```

Add a boundary test using `"地".repeat(257)` and `"行".repeat(129)` that expects `respCode=2002` on both create and update, while 256/128-character values succeed.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
mvn -f web-fe/pom.xml -Dtest=TuxedoRequestMapperTest,V03TuxedoContractTest,MockTuxedoClientV03ContractTest test
```

Expected: FAIL because Mock does not persist/validate the fields and the explicit response mapping is absent.

- [ ] **Step 3: Implement the canonical mappings and Mock semantics**

Add these entries to the request and response maps:

```java
Map.entry("payerAddress", "PAYER_ADDRESS"),
Map.entry("payeeAddress", "PAYEE_ADDRESS"),
Map.entry("payerBankName", "PAYER_BANK_NAME")
```

```java
Map.entry("PAYER_ADDRESS", "payerAddress"),
Map.entry("PAYEE_ADDRESS", "payeeAddress"),
Map.entry("PAYER_BANK_NAME", "payerBankName")
```

In `MockTuxedoClient`, add the fields to `BUSINESS_FIELDS`, add a detail-only set, and validate by Unicode code point count:

```java
private static final Set<String> DETAIL_ONLY_FIELDS = Set.of(
    "PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME"
);

private TuxedoResponse validatePartyFields(TuxedoRequest request) {
    if (tooLong(request, "PAYER_ADDRESS", 256)
        || tooLong(request, "PAYEE_ADDRESS", 256)
        || tooLong(request, "PAYER_BANK_NAME", 128)) {
        return TuxedoResponse.fail("2002", "字段长度超限");
    }
    return null;
}

private boolean tooLong(TuxedoRequest request, String field, int maximum) {
    if (!request.fields().containsKey(field)) {
        return false;
    }
    String value = text(request, field, "");
    return value.codePointCount(0, value.length()) > maximum;
}
```

Call `validatePartyFields` from create/update validation before mutation. Keep the existing `BUSINESS_FIELDS` loop so omitted update values remain unchanged and present empty strings overwrite stored values.

Before returning paged records, create a copy and remove `DETAIL_ONLY_FIELDS`:

```java
private Map<String, Object> listRecord(Map<String, Object> voucher) {
    Map<String, Object> record = new LinkedHashMap<>(voucher);
    DETAIL_ONLY_FIELDS.forEach(record::remove);
    return record;
}
```

Use `listRecord` in `voucherPage`; leave `detail()` returning the complete stored voucher.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: all selected tests PASS.

- [ ] **Step 5: Commit WebFE and Mock behavior**

```powershell
git add web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/V03TuxedoContractTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientV03ContractTest.java
git commit -m "feat(webfe): add voucher party address fields"
```

### Task 2: Oracle and Native Tuxedo Persistence

**Files:**
- Create: `sql/040_add_party_address_bank_fields.sql`
- Modify: `sql/010_create_tables.sql`
- Modify: `sql/schema.sql`
- Modify: `scripts/init-db.sh`
- Modify: `tuxedo-server/include/cnaps_db.h`
- Modify: `tuxedo-server/include/cnaps_fields.h`
- Modify: `tuxedo-server/include/cnaps_service.h`
- Modify: `tuxedo-server/src/common/validation_helper.c`
- Modify: `tuxedo-server/src/common/db_helper.c`
- Modify: `tuxedo-server/src/common/fml_helper.c`
- Modify: `tuxedo-server/src/services/cnaps_create.c`
- Modify: `tuxedo-server/src/services/cnaps_update.c`
- Modify: `tuxedo-server/src/services/cnaps_query.c`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`

**Interfaces:**
- Consumes: `PAYER_ADDRESS`, `PAYEE_ADDRESS`, `PAYER_BANK_NAME` from Task 1.
- Produces: persisted row members `payer_address`, `payee_address`, `payer_bank_name` and detail-only FML output used by Task 3.

- [ ] **Step 1: Write failing SQL and C source-contract tests**

Add source assertions requiring:

```java
assertThat(header).contains(
    "char payer_address[1025]", "char payee_address[1025]", "char payer_bank_name[513]"
);
assertThat(create).contains(
    "CNAPS_F_PAYER_ADDRESS", "CNAPS_F_PAYEE_ADDRESS", "CNAPS_F_PAYER_BANK_NAME",
    "cnaps_valid_optional_text"
);
assertThat(update).contains(
    "overlay_field(fbfr, CNAPS_F_PAYER_ADDRESS",
    "overlay_field(fbfr, CNAPS_F_PAYEE_ADDRESS",
    "overlay_field(fbfr, CNAPS_F_PAYER_BANK_NAME",
    "cnaps_valid_optional_text"
);
assertThat(db).contains(
    "PAYER_ADDRESS", "PAYEE_ADDRESS", "PAYER_BANK_NAME",
    ":payer_address", ":payee_address", ":payer_bank_name"
);
assertThat(query).contains("cnaps_put_voucher_detail_fields(fbfr, &row)");
```

In `DeploymentArtifactTest`, assert both DDL files contain the three `VARCHAR2(... CHAR)` columns, `scripts/init-db.sh` invokes `040_add_party_address_bank_fields.sql`, and the migration contains three `USER_TAB_COLUMNS` guards plus three `ALTER TABLE` statements.

- [ ] **Step 2: Run source-contract tests and verify RED**

```powershell
mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest,DeploymentArtifactTest test
```

Expected: FAIL because the SQL columns, migration, C row members, DML, validation, and detail helper do not exist.

- [ ] **Step 3: Add fresh DDL and repeatable migration**

Add to both create-table definitions:

```sql
  PAYER_ADDRESS VARCHAR2(256 CHAR),
  PAYEE_ADDRESS VARCHAR2(256 CHAR),
  PAYER_BANK_NAME VARCHAR2(128 CHAR),
```

Create `sql/040_add_party_address_bank_fields.sql` with one guarded block per column:

```sql
DECLARE
  V_COLUMN_COUNT NUMBER;
BEGIN
  SELECT COUNT(*) INTO V_COLUMN_COUNT
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'T_CNAPS_BILL_POC' AND COLUMN_NAME = 'PAYER_ADDRESS';
  IF V_COLUMN_COUNT = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE T_CNAPS_BILL_POC ADD (PAYER_ADDRESS VARCHAR2(256 CHAR))';
  END IF;

  SELECT COUNT(*) INTO V_COLUMN_COUNT
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'T_CNAPS_BILL_POC' AND COLUMN_NAME = 'PAYEE_ADDRESS';
  IF V_COLUMN_COUNT = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE T_CNAPS_BILL_POC ADD (PAYEE_ADDRESS VARCHAR2(256 CHAR))';
  END IF;

  SELECT COUNT(*) INTO V_COLUMN_COUNT
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'T_CNAPS_BILL_POC' AND COLUMN_NAME = 'PAYER_BANK_NAME';
  IF V_COLUMN_COUNT = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE T_CNAPS_BILL_POC ADD (PAYER_BANK_NAME VARCHAR2(128 CHAR))';
  END IF;
END;
/
```

Append this file to `scripts/init-db.sh` after `010_create_tables.sql`.

- [ ] **Step 4: Add native row fields, validation, DML, and detail-only output**

Add row buffers sized for four-byte UTF-8 plus terminator:

```c
char payer_address[1025];
char payee_address[1025];
char payer_bank_name[513];
```

Expose and implement:

```c
int cnaps_valid_optional_text(const char *value, size_t max_characters);
void cnaps_put_voucher_detail_fields(FBFR32 *fbfr, const void *row);
```

Count UTF-8 code points by counting bytes that are not continuation bytes; reject malformed continuation-only input only through the existing input path and return false once the count exceeds the limit:

```c
int cnaps_valid_optional_text(const char *value, size_t max_characters)
{
    size_t characters = 0;
    const unsigned char *cursor = (const unsigned char *)value;
    if (value == NULL) return 1;
    while (*cursor != '\0') {
        if ((*cursor & 0xC0U) != 0x80U && ++characters > max_characters) return 0;
        ++cursor;
    }
    return 1;
}
```

Add three field constants and copy/overlay calls. Validate create after extraction and update after overlay:

```c
if (!cnaps_valid_optional_text(row.payer_address, 256)
    || !cnaps_valid_optional_text(row.payee_address, 256)
    || !cnaps_valid_optional_text(row.payer_bank_name, 128)) {
    cnaps_return_error(rqst, "2002", "field too long");
    return;
}
```

Bind the three members in `execute_dml`, add them to INSERT/UPDATE, and insert `NVL(PAYER_ADDRESS, '')`, `NVL(PAYEE_ADDRESS, '')`, `NVL(PAYER_BANK_NAME, '')` into `CNAPS_VOUCHER_SELECT_COLUMNS`. Increase define/indicator counts from 40 to 43, shift following positions consistently, add the fields to `clear_voucher_nulls`, and keep `VERSION_NO` last.

Implement detail-only output:

```c
void cnaps_put_voucher_detail_fields(FBFR32 *fbfr, const void *value)
{
    const cnaps_voucher_row *row = (const cnaps_voucher_row *)value;
    cnaps_put_string(fbfr, CNAPS_F_PAYER_ADDRESS, row->payer_address);
    cnaps_put_string(fbfr, CNAPS_F_PAYEE_ADDRESS, row->payee_address);
    cnaps_put_string(fbfr, CNAPS_F_PAYER_BANK_NAME, row->payer_bank_name);
}
```

Call it only in `CNAPS5702I` after `cnaps_put_voucher`; do not add these fields to `cnaps_put_voucher_occurrence`.

- [ ] **Step 5: Run source-contract tests and verify GREEN**

Run the command from Step 2.

Expected: selected tests PASS.

- [ ] **Step 6: Commit database and native persistence**

```powershell
git add sql/010_create_tables.sql sql/schema.sql sql/040_add_party_address_bank_fields.sql scripts/init-db.sh tuxedo-server/include/cnaps_db.h tuxedo-server/include/cnaps_fields.h tuxedo-server/include/cnaps_service.h tuxedo-server/src/common/validation_helper.c tuxedo-server/src/common/db_helper.c tuxedo-server/src/common/fml_helper.c tuxedo-server/src/services/cnaps_create.c tuxedo-server/src/services/cnaps_update.c tuxedo-server/src/services/cnaps_query.c web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "feat(tuxedo): persist voucher party address fields"
```

### Task 3: FML32, Jolt, and Public Documentation

**Files:**
- Modify: `tuxedo-server/fml/cnaps_poc.fml32`
- Modify: `tuxedo/jolt/cnaps_services.bulk`
- Modify: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `docs/cnaps-frontend-api.md`
- Modify: `docs/cnaps-api-database.md`

**Interfaces:**
- Consumes: native FML names and detail-only output from Task 2.
- Produces: deployable Jolt contract and documented JSON API.

- [ ] **Step 1: Write failing Jolt and documentation contract tests**

Extend `JoltTuxedoClientTest.readsAndMapsEveryVoucherFieldFromSingleRecordService()` to require these three fields in the single-record response list and mapped output. Extend `DeploymentArtifactTest` to require:

```java
assertThat(fml).contains(
    "PAYER_ADDRESS   12042   string",
    "PAYEE_ADDRESS   12043   string",
    "PAYER_BANK_NAME 12044   string"
);
assertScalarParam(metadata, "CNAPS5701E", "PAYER_ADDRESS", "string", "in");
assertScalarParam(metadata, "CNAPS5701E", "PAYEE_ADDRESS", "string", "in");
assertScalarParam(metadata, "CNAPS5701E", "PAYER_BANK_NAME", "string", "in");
assertScalarParam(metadata, "CNAPS5701U", "PAYER_ADDRESS", "string", "in");
assertScalarParam(metadata, "CNAPS5701U", "PAYEE_ADDRESS", "string", "in");
assertScalarParam(metadata, "CNAPS5701U", "PAYER_BANK_NAME", "string", "in");
assertScalarParam(metadata, "CNAPS5702I", "PAYER_ADDRESS", "string", "out");
assertScalarParam(metadata, "CNAPS5702I", "PAYEE_ADDRESS", "string", "out");
assertScalarParam(metadata, "CNAPS5702I", "PAYER_BANK_NAME", "string", "out");
```

Also assert the two public docs contain all three JSON names and the migration filename.

- [ ] **Step 2: Run metadata tests and verify RED**

```powershell
mvn -f web-fe/pom.xml -Dtest=JoltTuxedoClientTest,DeploymentArtifactTest test
```

Expected: FAIL because FML IDs, Jolt parameters, response extraction, and docs are missing.

- [ ] **Step 3: Implement FML/Jolt metadata and response extraction**

Append unique FML32 string IDs:

```text
PAYER_ADDRESS   12042   string
PAYEE_ADDRESS   12043   string
PAYER_BANK_NAME 12044   string
```

Add all three parameters as scalar `access=in` to `CNAPS5701E` and `CNAPS5701U`, and scalar `access=out` to `CNAPS5702I`. Do not add them to `CNAPS4609Q` or `CNAPS5702Q`.

Add the three fields to `JoltTuxedoClient.RESPONSE_FIELDS`, not `VOUCHER_FIELDS`, so detail reads them while paged list extraction remains unchanged.

- [ ] **Step 4: Update public API and database docs**

Add the three optional fields and exact limits to create/update request tables and examples. Add them to the detail response field table/example, explicitly state omitted/present-empty update semantics, and leave list record tables/examples unchanged.

Add the three Oracle columns to the database dictionary and DDL example, document `sql/040_add_party_address_bank_fields.sql` as the repeatable upgrade path, and state that migration precedes native service deployment.

- [ ] **Step 5: Run metadata tests and verify GREEN**

Run the command from Step 2.

Expected: selected tests PASS.

- [ ] **Step 6: Commit metadata and documentation**

```powershell
git add tuxedo-server/fml/cnaps_poc.fml32 tuxedo/jolt/cnaps_services.bulk web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java docs/cnaps-frontend-api.md docs/cnaps-api-database.md
git commit -m "docs(api): publish voucher party address fields"
```

### Task 4: Full Verification

**Files:**
- Verify only: all files changed in Tasks 1–3.

**Interfaces:**
- Consumes: completed Java, Mock, SQL, C, FML32, Jolt, and documentation changes.
- Produces: evidence that the branch is ready for push/deployment without unrelated files.

- [ ] **Step 1: Run the complete Maven test suite**

```powershell
mvn -f web-fe/pom.xml test
```

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 2: Verify schema, metadata scope, and whitespace**

```powershell
rg -n "PAYER_ADDRESS|PAYEE_ADDRESS|PAYER_BANK_NAME" sql tuxedo-server tuxedo web-fe docs/cnaps-frontend-api.md docs/cnaps-api-database.md
git diff HEAD~3 --check
```

Expected: fields appear in create/update/detail paths and persistence; no `param=` entries for the three fields occur inside `CNAPS4609Q` or `CNAPS5702Q`; `git diff --check` prints nothing.

- [ ] **Step 3: Review branch status and commits**

```powershell
git status --short --branch
git log --oneline -5
```

Expected: only the pre-existing untracked `.idea/` remains; implementation commits are present and the design/plan commits precede them.

- [ ] **Step 4: Record native-build limitation without claiming deployment**

The Windows workspace does not contain the Linux Tuxedo/OCI compiler runtime. Do not claim native C compilation or VM deployment until `./scripts/rebuild-deploy.sh` is run in the authorized VM environment after applying `sql/040_add_party_address_bank_fields.sql`.
