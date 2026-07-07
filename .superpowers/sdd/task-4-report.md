# Task 4 Report: Oracle, Tuxedo, And Linux Deployment Artifacts

## Implementation Summary

Implemented the deployment artifact set required by Task 4 and added the artifact verification test first, per TDD. The repo now contains:

- Oracle DDL for the single POC table and indexes at [sql/schema.sql](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/sql/schema.sql)
- Tuxedo field table at [tuxedo-server/fml/cnaps_poc.fml32](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/tuxedo-server/fml/cnaps_poc.fml32)
- Tuxedo deployment skeleton at [tuxedo/UBBCONFIG](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/tuxedo/UBBCONFIG)
- Linux runtime template at [conf/env.linux.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/conf/env.linux.sh)
- Reference data templates at [conf/dicts.properties](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/conf/dicts.properties) and [conf/banks.properties](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/conf/banks.properties)
- Helper scripts at [scripts/start.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/scripts/start.sh), [scripts/stop.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/scripts/stop.sh), and [scripts/status.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/scripts/status.sh)
- Verification test at [web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java)

The artifact contents match the Task 4 brief verbatim for the required names, values, and service registrations.

## TDD Evidence

### RED

Ran:

```bash
mvn -f web-war/pom.xml test -Dtest=DeploymentArtifactTest
```

Result: failed with `NoSuchFileException` for the expected missing artifacts:

- `sql/schema.sql`
- `tuxedo/UBBCONFIG`
- `conf/env.linux.sh`

The failure mode was correct and proved the test was asserting real file presence.

### GREEN

After creating the artifact files, reran:

```bash
mvn -f web-war/pom.xml test -Dtest=DeploymentArtifactTest
```

Result: passed, 3 tests run, 0 failures, 0 errors.

## Test Results

- Focused artifact test: pass
- Full `web-war` suite: pass, 21 tests run, 0 failures, 0 errors

## Files Changed

- Added [web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java)
- Added [sql/schema.sql](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/sql/schema.sql)
- Added [tuxedo-server/fml/cnaps_poc.fml32](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/tuxedo-server/fml/cnaps_poc.fml32)
- Added [tuxedo/UBBCONFIG](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/tuxedo/UBBCONFIG)
- Added [conf/env.linux.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/conf/env.linux.sh)
- Added [conf/dicts.properties](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/conf/dicts.properties)
- Added [conf/banks.properties](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/conf/banks.properties)
- Added [scripts/start.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/scripts/start.sh)
- Added [scripts/stop.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/scripts/stop.sh)
- Added [scripts/status.sh](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/scripts/status.sh)

## Self-Review

- Kept the work scoped to Task 4 artifacts and the verification test.
- Used the approved PRD values for the table, indexes, service names, and reference data.
- Did not touch business logic or the existing API implementation.
- Left `.superpowers/` scratch files and `web-war/target/` alone.

## Concerns

- The full test run emitted JVM warnings about dynamic Java-agent loading and class-data sharing. The tests still passed cleanly, but the console output was not perfectly pristine.

## Fix Report Append

Review follow-up fixed the Oracle/entity drift and added direct value checks for the reference property files.

### What changed

- Updated [web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPoc.java](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPoc.java) so the JPA mapping matches the Oracle DDL:
  - `ACCOUNT_PART1`, `ACCOUNT_PART2`, `ACCOUNT_PART3`
  - `LAST_ACTION_TIME`
  - reviewed field lengths/nullability for `BILL_ID`, `PAYEE_ACCOUNT_NO`, `PAYEE_NAME`, `AMOUNT`, `OPERATOR_NO`, `BRANCH_NO`, `FAX_FLAG`, `REJECT_REASON`, and `DELETE_REASON`
- Expanded [web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java](file:///D:/Project/ruisui/.worktrees/cnaps-single-table-poc/web-war/src/test/java/com/ruisui/bank/sim/DeploymentArtifactTest.java) to assert:
  - entity column names and lengths against the Oracle contract
  - `conf/dicts.properties` values
  - `conf/banks.properties` values

### Command summary

RED:

```bash
mvn -f web-war/pom.xml test -Dtest=DeploymentArtifactTest
```

Observed failure: the new alignment test failed on `ACCOUNT_PART1` vs `ACCOUNT_PART_1`, proving the mismatch was real. The config-value test initially exposed a UTF-8 vs `Properties.load(InputStream)` issue, which was corrected by loading the files with a UTF-8 reader.

GREEN:

```bash
mvn -f web-war/pom.xml test -Dtest=DeploymentArtifactTest
mvn -f web-war/pom.xml test
```

Both commands passed after the mapping and test updates.
