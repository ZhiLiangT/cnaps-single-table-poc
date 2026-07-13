# Paged FML32 Response Buffer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure `BANKQRY`, `CNAPS4609Q`, and `CNAPS5702Q` return complete paged FML32 responses instead of HTTP 504 errors caused by an undersized Tuxedo request buffer.

**Architecture:** Add one shared native helper that grows `TPSVCINFO.data` with `tprealloc` only when `Fsizeof32` reports insufficient capacity. Each paged service reserves a bounded response capacity before writing page metadata and repeated records, aborting with `4002` if allocation fails.

**Tech Stack:** Oracle Tuxedo 22.1.1 C APIs (`Fsizeof32`, `tprealloc`), Java 17, JUnit 5, AssertJ, Maven, Tomcat/Jolt, Oracle XE

## Global Constraints

- Preserve all existing HTTP paths, request fields, response fields, pagination defaults, and the maximum page size of 100.
- Cover `BANKQRY`, `CNAPS4609Q`, and `CNAPS5702Q`.
- Do not modify database schema or data, Jolt service contracts, or `conf/db.env`.
- Use 16 KiB for the single-row bank response and 1 MiB for voucher page responses.
- A failed allocation must return `4002` and must not continue constructing a partial success response.

---

### Task 1: Add the failing native source contract

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- Test: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`

**Interfaces:**
- Consumes: existing repository source files under `tuxedo-server/`
- Produces: a regression test requiring `FBFR32 *cnaps_reserve_response_buffer(TPSVCINFO *rqst, long minimum_size)` and its use by all three paged services

- [ ] **Step 1: Write the failing test**

Add this test to `TuxedoCSourceContractTest`:

```java
@Test
void nativePagedQueriesReserveEnoughFmlResponseCapacityBeforeWritingRows() throws Exception {
    String header = Files.readString(root.resolve("tuxedo-server/include/cnaps_service.h"));
    String fml = Files.readString(root.resolve("tuxedo-server/src/common/fml_helper.c"));
    String bank = Files.readString(root.resolve("tuxedo-server/src/services/bank_query.c"));
    String query = Files.readString(root.resolve("tuxedo-server/src/services/cnaps_query.c"));

    assertThat(header).contains(
        "FBFR32 *cnaps_reserve_response_buffer(TPSVCINFO *rqst, long minimum_size);"
    );
    assertThat(fml).contains(
        "Fsizeof32(fbfr)",
        "tprealloc(rqst->data, minimum_size)",
        "rqst->data = (char *)resized"
    );
    assertThat(bank).contains(
        "BANK_QUERY_RESPONSE_BUFFER_SIZE (16L * 1024L)",
        "cnaps_reserve_response_buffer(rqst, BANK_QUERY_RESPONSE_BUFFER_SIZE)",
        "response buffer allocation failed"
    );
    assertThat(query).contains(
        "CNAPS_QUERY_RESPONSE_BUFFER_SIZE (1024L * 1024L)",
        "cnaps_reserve_response_buffer(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE)",
        "response buffer allocation failed"
    );
    assertThat(query.split(
        "cnaps_reserve_response_buffer\\(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE\\)",
        -1
    )).hasSize(3);
    assertThat(bank.indexOf("cnaps_reserve_response_buffer"))
        .isLessThan(bank.indexOf("cnaps_put_long(fbfr, CNAPS_F_PAGE_NO"));
    assertThat(query.indexOf("cnaps_reserve_response_buffer"))
        .isLessThan(query.indexOf("cnaps_put_long(fbfr, CNAPS_F_PAGE_NO"));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest#nativePagedQueriesReserveEnoughFmlResponseCapacityBeforeWritingRows test
```

Expected: FAIL because the helper declaration, `Fsizeof32`/`tprealloc` implementation, and service calls do not exist.

### Task 2: Implement bounded FML32 response growth

**Files:**
- Modify: `tuxedo-server/include/cnaps_service.h`
- Modify: `tuxedo-server/src/common/fml_helper.c`
- Modify: `tuxedo-server/src/services/bank_query.c`
- Modify: `tuxedo-server/src/services/cnaps_query.c`
- Test: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`

**Interfaces:**
- Consumes: `TPSVCINFO.data`, `Fsizeof32`, and `tprealloc`
- Produces: `FBFR32 *cnaps_reserve_response_buffer(TPSVCINFO *rqst, long minimum_size)`; returns the active buffer or `NULL` on invalid input/allocation failure, and updates `rqst->data` after a successful reallocation

- [ ] **Step 1: Declare the shared helper**

Add to `tuxedo-server/include/cnaps_service.h`:

```c
FBFR32 *cnaps_reserve_response_buffer(TPSVCINFO *rqst, long minimum_size);
```

- [ ] **Step 2: Implement the minimal helper**

Add to `tuxedo-server/src/common/fml_helper.c` before the field access helpers:

```c
FBFR32 *cnaps_reserve_response_buffer(TPSVCINFO *rqst, long minimum_size)
{
    FBFR32 *fbfr;
    FBFR32 *resized;
    long current_size;

    if (rqst == NULL || rqst->data == NULL || minimum_size <= 0) {
        return NULL;
    }
    fbfr = (FBFR32 *)rqst->data;
    current_size = Fsizeof32(fbfr);
    if (current_size < 0) {
        userlog("failed to inspect FML32 response buffer: %s", Fstrerror32(Ferror32));
        return NULL;
    }
    if (current_size >= minimum_size) {
        return fbfr;
    }
    resized = (FBFR32 *)tprealloc(rqst->data, minimum_size);
    if (resized == NULL) {
        userlog("failed to grow FML32 response buffer to %ld bytes: %s", minimum_size, tpstrerror(tperrno));
        return NULL;
    }
    rqst->data = (char *)resized;
    return resized;
}
```

- [ ] **Step 3: Reserve the bank response buffer**

In `bank_query.c`, add:

```c
#define BANK_QUERY_RESPONSE_BUFFER_SIZE (16L * 1024L)
```

Immediately before the first `cnaps_put_long` call, add:

```c
fbfr = cnaps_reserve_response_buffer(rqst, BANK_QUERY_RESPONSE_BUFFER_SIZE);
if (fbfr == NULL) {
    cnaps_return_error(rqst, "4002", "response buffer allocation failed");
    return;
}
```

- [ ] **Step 4: Reserve voucher page response buffers**

In `cnaps_query.c`, add:

```c
#define CNAPS_QUERY_RESPONSE_BUFFER_SIZE (1024L * 1024L)
```

In both `CNAPS4609Q` and `CNAPS5702Q`, immediately after the database-error branch and before the first `cnaps_put_long` call, add:

```c
fbfr = cnaps_reserve_response_buffer(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE);
if (fbfr == NULL) {
    cnaps_return_error(rqst, "4002", "response buffer allocation failed");
    return;
}
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```bash
mvn -f web-fe/pom.xml -Dtest=TuxedoCSourceContractTest#nativePagedQueriesReserveEnoughFmlResponseCapacityBeforeWritingRows test
```

Expected: PASS.

- [ ] **Step 6: Run the complete local test suite**

Run:

```bash
mvn -f web-fe/pom.xml test
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 7: Commit the implementation**

```bash
git add tuxedo-server/include/cnaps_service.h \
  tuxedo-server/src/common/fml_helper.c \
  tuxedo-server/src/services/bank_query.c \
  tuxedo-server/src/services/cnaps_query.c \
  web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java
git commit -m "fix(tuxedo): grow paged FML response buffers"
```

### Task 3: Publish, deploy, and verify the real Jolt path

**Files:**
- No source changes expected
- Preserve on VM: `/home/tian/cnaps-single-table-poc/conf/db.env`

**Interfaces:**
- Consumes: committed branch on `origin`, VM deployment script, HTTP endpoints, and Tuxedo ULOG
- Produces: deployed services with successful real pagination responses and no new FML capacity errors

- [ ] **Step 1: Push the branch and fast-forward the VM**

Run locally:

```bash
git push origin feature/cnaps-single-table-poc
```

On the VM, verify only `conf/db.env` is modified, then run:

```bash
cd /home/tian/cnaps-single-table-poc
git pull --ff-only origin feature/cnaps-single-table-poc
```

Expected: VM HEAD equals local HEAD and `git status --short` still reports only `M conf/db.env`.

- [ ] **Step 2: Rebuild and deploy**

On the VM run:

```bash
cd /home/tian/cnaps-single-table-poc
./scripts/rebuild-deploy.sh
```

Expected: C build succeeds, all Maven tests pass, WAR deploys, and Oracle/Tuxedo/WebFE health reports `UP`.

- [ ] **Step 3: Verify all three paged APIs**

From the local machine run:

```bash
curl -fsS "http://192.168.84.134:8080/ruisui-bank-sim/api/banks?pageNo=1&pageSize=1"
curl -fsS "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers?pageNo=1&pageSize=1"
curl -fsS "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers/review-list?pageNo=1&pageSize=1"
```

Expected: each response has HTTP 200 and `"respCode":"0000"` with page metadata and a `records` array.

- [ ] **Step 4: Verify health, services, and logs**

On the VM run:

```bash
./scripts/cnapsctl.sh status
```

Record the newest ULOG path before the API calls, then inspect only the new request window:

```bash
tail -n 200 logs/ULOG*
```

Expected: Oracle, Tomcat, Tuxedo services, and ports 1521/8000/8080 are active; health has `respCode=0000`; the new pagination requests do not log `No space in fielded buffer`.
