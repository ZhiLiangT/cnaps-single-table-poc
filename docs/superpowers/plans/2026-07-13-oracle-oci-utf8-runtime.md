# Oracle OCI UTF-8 Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist `NLS_LANG=AMERICAN_AMERICA.AL32UTF8` in every Tuxedo startup and prove Chinese voucher text survives the WebFE → Jolt → Tuxedo → OCI → Oracle round trip.

**Architecture:** Add the default to the tracked `conf/tuxedo.env`, which is sourced before `tmboot` by the existing startup and systemd flow. Protect that contract with an artifact test, document the runtime invariant, then fast-forward the VM, restart Tuxedo, and run environment, OCI, health, and Chinese CRUD checks.

**Tech Stack:** POSIX shell environment files, Oracle Tuxedo/Jolt 22.1, OCI/Oracle XE 21c, Java 17, JUnit 5, AssertJ, Maven, systemd, curl.

## Global Constraints

- Do not modify the Oracle schema or run `scripts/init-db.sh`.
- Preserve the VM-local `conf/db.env`; never print, copy, stage, or commit its contents.
- Do not modify historical rows whose Chinese text is already `U+FFFD` or `?`.
- The default must remain overridable through an already-set `NLS_LANG`.
- The tracked default must be exactly `AMERICAN_AMERICA.AL32UTF8`.

---

### Task 1: Persist and document the OCI UTF-8 runtime contract

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `conf/tuxedo.env`
- Modify: `docs/cnaps-operations.md`

**Interfaces:**
- Consumes: existing shell startup scripts that source `conf/tuxedo.env` before `tmboot`.
- Produces: exported `NLS_LANG` inherited by JSL, JSH, `cnapspocsvr`, and OCI sessions.

- [ ] **Step 1: Write the failing deployment contract test**

Add this test beside `tuxedo22cLocalJoltConfigurationAllowsNonTlsLoopbackForPoc`:

```java
@Test
void tuxedoRuntimeDefaultsOracleOciClientToAl32Utf8() throws Exception {
    assertThat(Files.readString(root.resolve("conf/tuxedo.env")))
        .contains("export NLS_LANG=${NLS_LANG:-AMERICAN_AMERICA.AL32UTF8}");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest#tuxedoRuntimeDefaultsOracleOciClientToAl32Utf8 test
```

Expected: FAIL because `conf/tuxedo.env` does not contain the required export.

- [ ] **Step 3: Add the minimal tracked runtime default**

Add after `TM_ALLOW_NOTLS` in `conf/tuxedo.env`:

```sh
export NLS_LANG=${NLS_LANG:-AMERICAN_AMERICA.AL32UTF8}
```

- [ ] **Step 4: Document the persistent runtime invariant**

Add a `### 2.1 Oracle OCI 中文字符集` subsection after the safety-boundary text in `docs/cnaps-operations.md`:

```markdown
### 2.1 Oracle OCI 中文字符集

`conf/tuxedo.env` 默认导出 `NLS_LANG=AMERICAN_AMERICA.AL32UTF8`。Tuxedo 启动脚本和 systemd 开机服务都会在 `tmboot` 前加载该文件，使 JSL、JSH、业务 C 服务及 OCI 会话继承 UTF-8 客户端字符集。不要只在交互式终端临时设置该变量，否则服务重启后会再次丢失。

修改该配置后必须重启 Tuxedo，并确认业务服务进程环境：

```bash
p=$(pgrep -x cnapspocsvr | head -1)
tr '\0' '\n' < "/proc/$p/environ" | grep '^NLS_LANG=AMERICAN_AMERICA.AL32UTF8$'
```

历史记录中已经变成 `U+FFFD` 或 `?` 的内容无法从替换字符还原，必须依据原始凭证重新录入或人工修正。
```

- [ ] **Step 5: Run focused and complete tests and verify GREEN**

Run:

```bash
mvn -f web-fe/pom.xml -Dtest=DeploymentArtifactTest#tuxedoRuntimeDefaultsOracleOciClientToAl32Utf8 test
mvn -f web-fe/pom.xml test
```

Expected: both commands exit 0; the focused test reports 1 test with 0 failures, and the full suite reports 0 failures and 0 errors.

- [ ] **Step 6: Review and commit the implementation**

Run:

```bash
git diff --check
git diff -- conf/tuxedo.env docs/cnaps-operations.md web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git add conf/tuxedo.env docs/cnaps-operations.md web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "fix(runtime): force OCI UTF-8 client charset"
```

Expected: only the three planned files are committed; `.idea/` remains untracked.

### Task 2: Publish and fast-forward the virtual machine

**Files:**
- No repository file changes.
- Preserve on VM: `/home/tian/cnaps-single-table-poc/conf/db.env`

**Interfaces:**
- Consumes: committed branch `feature/cnaps-single-table-poc`.
- Produces: VM checkout at the same commit without changing database credentials.

- [ ] **Step 1: Verify local and remote Git state**

Run locally:

```bash
git status --short
git log -3 --oneline
git push origin feature/cnaps-single-table-poc
```

Expected: only `.idea/` is untracked before the push; push succeeds.

- [ ] **Step 2: Verify the VM has no conflicting changes**

Run:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && git status --short && git branch --show-current'
```

Expected: branch is `feature/cnaps-single-table-poc`; any tracked modification is limited to `conf/db.env`.

- [ ] **Step 3: Fast-forward the VM checkout**

Run:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && git pull --ff-only origin feature/cnaps-single-table-poc'
```

Expected: fast-forward succeeds and does not overwrite `conf/db.env`.

### Task 3: Restart Tuxedo and verify the production character path

**Files:**
- No repository file changes.
- Runtime state: Tuxedo/JSL/JSH processes on `192.168.84.134`.

**Interfaces:**
- Consumes: VM checkout containing the tracked `NLS_LANG` default.
- Produces: restarted Tuxedo processes with `NLS_LANG=AMERICAN_AMERICA.AL32UTF8` and a verified Chinese CRUD round trip.

- [ ] **Step 1: Restart Tuxedo through the standard controller**

Run:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && ./scripts/stop-tuxedo.sh && ./scripts/start-tuxedo.sh'
```

Expected: `tmshutdown` and `tmboot` succeed; `cnapspocsvr`, `TMMETADATA`, and `JSL` start.

- [ ] **Step 2: Verify the business process inherited the setting**

Run:

```bash
ssh tian@192.168.84.134 'p=$(pgrep -x cnapspocsvr | head -1); tr "\000" "\n" < /proc/$p/environ | grep "^NLS_LANG=AMERICAN_AMERICA.AL32UTF8$"'
```

Expected: exactly one matching line.

- [ ] **Step 3: Verify OCI returns a known Chinese character as UTF-8**

Source `conf/db.env` without printing it, query `UNISTR('\\4E2D')`, and inspect stdout bytes:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && . ./conf/tuxedo.env && . ./conf/db.env && printf "set heading off feedback off pages 0\nselect unistr(chr(92)||chr(52)||chr(69)||chr(50)||chr(68)) from dual;\nexit\n" | sqlplus -L -S "$ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_CONNECT_STRING" | xxd -p'
```

Expected: `e4b8ad0a`, not `3f0a`.

- [ ] **Step 4: Verify service health**

Run:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && ./scripts/cnapsctl.sh status && ./scripts/cnapsctl.sh health'
```

Expected: Oracle, Tuxedo, and WebFE are `UP`; ports 1521, 8000, and 8080 are listening; health returns `respCode=0000`.

- [ ] **Step 5: Run the existing Chinese CRUD smoke test**

Run:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && BASE_URL=http://127.0.0.1:8080/ruisui-bank-sim ./scripts/smoke-test.sh'
```

Expected: create, query, detail, update, and logical delete calls all succeed; responses contain `收款人名称`, `收款人名称-已修改`, `无请求头CRUD验证`, and no `?` or `U+FFFD` replacement characters in those fields.

- [ ] **Step 6: Perform final repository and runtime verification**

Run locally:

```bash
mvn -f web-fe/pom.xml test
git status --short
git log -3 --oneline
```

Run remotely:

```bash
ssh tian@192.168.84.134 'cd /home/tian/cnaps-single-table-poc && git rev-parse HEAD && git status --short && p=$(pgrep -x cnapspocsvr | head -1); tr "\000" "\n" < /proc/$p/environ | grep "^NLS_LANG=AMERICAN_AMERICA.AL32UTF8$"'
```

Expected: local tests pass; local and VM commits match; VM retains only its pre-existing `conf/db.env` modification; runtime environment contains the required value.
