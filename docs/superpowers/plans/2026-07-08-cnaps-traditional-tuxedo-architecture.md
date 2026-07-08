# CNAPS Traditional Tuxedo Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the existing CNAPS Spring Boot single-table POC into a phase-one traditional JSP/Servlet, Tuxedo C, Oracle SQL*Plus, and Linux deployment architecture while preserving the proven HTTP and business contract.

**Architecture:** Keep `web-war` as the executable Spring Boot contract reference. Add `web-fe` as the new external Tomcat WAR with JSP/Servlet endpoints and a hidden `TuxedoClient` transport boundary, expand `tuxedo-server` into ATMI C service skeletons, and split deployment assets into SQL*Plus and Linux operational scripts.

**Tech Stack:** Java 17, Maven WAR, Servlet 4 / JSP 2.3, Jackson, JUnit 5, Oracle Tuxedo ATMI/FML32 C skeletons, OCI DAO interfaces, SQL*Plus scripts, POSIX shell, gcc/make/buildserver.

## Global Constraints

- Java WebFE to Tuxedo integration: Jolt first.
- Business table model: keep `T_CNAPS_BILL_POC`.
- Wire format: FML32 only for phase one.
- TLV support: defer until a deployment or PRD test case requires it.
- Reference data: keep file-backed dictionaries and bank data in phase one to preserve the single-business-table boundary.
- Web runtime: external Tomcat 9 with classic JSP/Servlet APIs.
- Java runtime: Java 17 for WebFE build and runtime.
- C compiler path: system gcc plus Oracle Tuxedo `buildserver`.
- Tuxedo version: use the Oracle Tuxedo installed on the Linux VM, with scripts parameterized through `conf/env.linux.sh`.
- Preserve existing response codes, service names, voucher statuses, and single-table POC behavior.
- Local verification cannot run Oracle Tuxedo or Oracle Database; local tests verify contract, buildability of Java modules, and static deployment artifacts.

---

## File Structure

- Modify `web-war/src/test/java/com/ruisui/bank/sim/TraditionalArchitectureArtifactTest.java`: static architecture contract tests.
- Create `web-fe/pom.xml`: external Tomcat WAR build, no Spring Boot dependency.
- Create `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/*.java`: Servlet JSON facade.
- Create `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/*.java`: `TuxedoClient`, Jolt adapter boundary, request/response mappers.
- Create `web-fe/src/main/java/com/ruisui/cnaps/web/dto/*.java`: simple response DTOs.
- Create `web-fe/src/main/java/com/ruisui/cnaps/web/support/*.java`: JSON and request helpers.
- Create `web-fe/src/main/webapp/*.jsp`, `WEB-INF/web.xml`, `static/js/cnaps.js`, `static/css/app.css`: operator UI shell.
- Modify `tuxedo-server/fml/cnaps_poc.fml32`: expand canonical FML32 fields while keeping existing names.
- Create `tuxedo-server/src/cnapspocsvr.c`, `src/services/*.c`, `src/common/*.c`, `include/*.h`, `Makefile`, `build.sh`: C service and buildserver skeleton.
- Create `sql/001_create_user.sql`, `010_create_tables.sql`, `020_create_indexes.sql`, `030_seed_reference_data.sql`, `090_drop_all.sql`: SQL*Plus lifecycle.
- Create `conf/db.env`, `conf/tuxedo.env`, `conf/app.properties`: runtime templates.
- Create `scripts/init-db.sh`, `build-c.sh`, `build-web.sh`, `load-tuxconfig.sh`, `start-tuxedo.sh`, `stop-tuxedo.sh`, `status-tuxedo.sh`, `deploy-web.sh`, `smoke-test.sh`: Linux operations.

---

### Task 1: Architecture Contract Tests

**Files:**
- Create: `web-war/src/test/java/com/ruisui/bank/sim/TraditionalArchitectureArtifactTest.java`

**Interfaces:**
- Consumes: architecture design at `docs/superpowers/specs/2026-07-08-cnaps-traditional-tuxedo-architecture-design.md`.
- Produces: failing tests for the target repository layout and key artifact contracts.

- [ ] **Step 1: Write the failing architecture tests**

Create JUnit tests that read the repository root from `System.getProperty("user.dir")).getParent()` and assert:

```java
assertThat(root.resolve("web-fe/pom.xml")).exists();
assertThat(Files.readString(root.resolve("web-fe/pom.xml"))).contains("<packaging>war</packaging>");
assertThat(Files.readString(root.resolve("web-fe/src/main/webapp/WEB-INF/web.xml")))
    .contains("/api/health", "/api/dicts/*", "/api/banks", "/api/cnaps/vouchers/*");
assertThat(root.resolve("tuxedo-server/src/cnapspocsvr.c")).exists();
assertThat(Files.readString(root.resolve("tuxedo-server/Makefile"))).contains("buildserver");
assertThat(Files.readString(root.resolve("scripts/build-c.sh"))).contains("set -eu", "make -C");
```

- [ ] **Step 2: Run RED**

Run: `mvn -f web-war/pom.xml test -Dtest=TraditionalArchitectureArtifactTest`

Expected: FAIL because `web-fe`, Tuxedo C sources, SQL split scripts, and new Linux scripts do not exist yet.

---

### Task 2: Java WebFE WAR And Tuxedo Adapter Boundary

**Files:**
- Create: `web-fe/pom.xml`
- Create: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoClient.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequest.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponse.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoClientProvider.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/*.java`
- Create: `web-fe/src/main/java/com/ruisui/cnaps/web/support/*.java`
- Create: `web-fe/src/main/webapp/index.jsp`, `cnaps-create.jsp`, `cnaps-query.jsp`, `cnaps-review.jsp`, `WEB-INF/web.xml`, `static/js/cnaps.js`, `static/css/app.css`

**Interfaces:**
- Consumes: existing HTTP paths and Tuxedo service names.
- Produces: classic Tomcat WAR, servlet endpoint routing, and `TuxedoClient.call(String serviceName, TuxedoRequest request)`.

- [ ] **Step 1: Write mapper tests**

Create `TuxedoRequestMapperTest` with assertions:

```java
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers")).isEqualTo("CNAPS5701E");
assertThat(mapper.serviceName("GET", "/api/cnaps/vouchers/B202607087720002000")).isEqualTo("CNAPS5702I");
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/B202607087720002000/review-pass")).isEqualTo("CNAPS5702A");
assertThat(mapper.from("REQ-1", "77210021", "772", "2026-07-08", Map.of("amount", "1.00")).fields())
    .containsEntry("REQUEST_ID", "REQ-1")
    .containsEntry("OPERATOR_NO", "77210021");
```

- [ ] **Step 2: Run RED**

Run: `mvn -f web-fe/pom.xml test -Dtest=TuxedoRequestMapperTest`

Expected: FAIL because the `web-fe` module does not exist.

- [ ] **Step 3: Implement minimal WebFE**

Implement the WAR module with Servlet 4 APIs, Jackson JSON support, JSP pages, a mock client for local tests, and a Jolt adapter class that preserves the transport boundary without linking unavailable Jolt libraries in local builds.

- [ ] **Step 4: Run GREEN**

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS for WebFE mapper tests.

---

### Task 3: Tuxedo C, SQLPlus, And Linux Runtime Skeleton

**Files:**
- Modify: `tuxedo-server/fml/cnaps_poc.fml32`
- Modify: `tuxedo/UBBCONFIG`
- Create: `tuxedo-server/include/*.h`
- Create: `tuxedo-server/src/cnapspocsvr.c`
- Create: `tuxedo-server/src/services/*.c`
- Create: `tuxedo-server/src/common/*.c`
- Create: `tuxedo-server/Makefile`
- Create: `tuxedo-server/build.sh`
- Create: `sql/001_create_user.sql`
- Create: `sql/010_create_tables.sql`
- Create: `sql/020_create_indexes.sql`
- Create: `sql/030_seed_reference_data.sql`
- Create: `sql/090_drop_all.sql`
- Create: `conf/db.env`
- Create: `conf/tuxedo.env`
- Create: `conf/app.properties`
- Create: `scripts/init-db.sh`
- Create: `scripts/build-c.sh`
- Create: `scripts/build-web.sh`
- Create: `scripts/load-tuxconfig.sh`
- Create: `scripts/start-tuxedo.sh`
- Create: `scripts/stop-tuxedo.sh`
- Create: `scripts/status-tuxedo.sh`
- Create: `scripts/deploy-web.sh`
- Create: `scripts/smoke-test.sh`

**Interfaces:**
- Consumes: FML32 field names and service names used by WebFE.
- Produces: source/build/deploy skeleton expected by Linux Tuxedo operators.

- [ ] **Step 1: Run architecture tests while RED**

Run: `mvn -f web-war/pom.xml test -Dtest=TraditionalArchitectureArtifactTest`

Expected: still FAIL until all required artifacts exist.

- [ ] **Step 2: Implement skeleton artifacts**

Add ATMI service entry points for `SYSHEALTH`, `DICTQRY`, `BANKQRY`, `CNAPS5701E`, `CNAPS5701U`, `CNAPS5701D`, `CNAPS4609Q`, `CNAPS5702Q`, `CNAPS5702I`, `CNAPS5702A`, and `CNAPS5702R`; add OCI DAO function declarations; add SQL*Plus scripts and fail-fast shell scripts.

- [ ] **Step 3: Run GREEN**

Run: `mvn -f web-war/pom.xml test -Dtest=TraditionalArchitectureArtifactTest`

Expected: PASS.

---

### Task 4: Full Verification

**Files:**
- Read all modified files.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified project state and completion summary.

- [ ] **Step 1: Run Java contract reference tests**

Run: `mvn -f web-war/pom.xml test`

Expected: PASS, all existing and architecture artifact tests.

- [ ] **Step 2: Run WebFE tests**

Run: `mvn -f web-fe/pom.xml test`

Expected: PASS.

- [ ] **Step 3: Run static artifact checks**

Run:

```powershell
rg -n "SYSHEALTH|DICTQRY|BANKQRY|CNAPS5701E|CNAPS5701U|CNAPS5701D|CNAPS4609Q|CNAPS5702Q|CNAPS5702I|CNAPS5702A|CNAPS5702R" tuxedo tuxedo-server web-fe
rg -n "buildserver|tmloadcf|tmboot|tmadmin|sqlplus" tuxedo-server scripts conf
```

Expected: required service names and runtime commands are present.

- [ ] **Step 4: Check Git status**

Run: `git status --short --branch`

Expected: implementation files are modified or added; pre-existing `docs/cnaps-api-database.md` remains untracked unless the user asks to track it.
