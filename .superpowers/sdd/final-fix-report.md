# Final Review Fix Wave Report

## Scope and baseline

- Baseline: `932ed19631997a550a8b874cd34d57ad26e81c1b`
- Implementation commit: `2ce32a80341452f9116eed509de49973e76e6012`
- Findings source: `.superpowers/sdd/final-review-findings.md`
- Design source: `docs/superpowers/specs/2026-07-10-headerless-v03-poc-api-design.md`
- Constraints preserved: one voucher table; no account/auth/role/permission logic; no business request headers; same-operator review remains allowed; native query/detail ordering remains unchanged.
- `.idea/` remained untracked and untouched.

## TDD evidence

### Initial RED

Command:

```text
mvn -f web-fe/pom.xml '-Dtest=DeploymentArtifactTest,JoltTuxedoClientTest,BaseJsonServletTest,MockTuxedoClientV03ContractTest,MockTuxedoClientWorkDateTest,TuxedoCSourceContractTest' test
```

Result: expected `BUILD FAILURE`, 81 tests run, 10 failures, 0 errors.

The failures proved each reported defect before its production fix:

1. `DeploymentArtifactTest.singleVoucherServicesTransportCompleteInputsAndVoucherOutputsWithoutOccurrences` failed at missing `CNAPS5701E CHECKER_NO`, demonstrating incomplete non-query Jolt voucher metadata.
2. `JoltTuxedoClientTest.convertsConfiguredMillisecondTimeoutToCeilingJoltSeconds` expected `30` but observed `30000`.
3. `MockTuxedoClientV03ContractTest.createAndUpdateRejectUnsupportedDictionaryValues` expected `2003` but observed `0000`; `TuxedoCSourceContractTest.nativeCreateAndUpdateValidateRequiredAndSuppliedDictionaryValues` also failed on native required defaults and absent dictionary guards.
4. `BaseJsonServletTest.rejectsInvalidExplicitCollectionWorkDateWithoutCallingTuxedo` expected HTTP 400 but observed 200; `MockTuxedoClientWorkDateTest.voucherQueriesRejectMalformedAndCalendarInvalidExplicitWorkDates` expected `2002` but observed `0000`; the native source contract lacked a shared strict validator.
5. `MockTuxedoClientV03ContractTest.bankKeywordMatchesBankNumberAsWellAsName` returned no record for a bank-number substring; the native bank source contract also failed.
6. `MockTuxedoClientV03ContractTest.reviewReturnPreservesOptionalReviewComment` could not find `REVIEW_COMMENT`; the native review source contract also failed.

### Self-review RED

After the initial GREEN cycle, integer-edge self-review found that `(timeoutMillis + 999)` could overflow in `int` space.

Command:

```text
mvn -f web-fe/pom.xml '-Dtest=JoltTuxedoClientTest#convertsMaximumPositiveMillisecondTimeoutWithoutIntegerOverflow' test
```

Result: expected `BUILD FAILURE`, 1 test run, 1 failure; expected `2147484`, observed `1`. The conversion was then changed to use `long` arithmetic.

### Focused GREEN

Command:

```text
mvn -f web-fe/pom.xml '-Dtest=DeploymentArtifactTest,JoltTuxedoClientTest,BaseJsonServletTest,MockTuxedoClientV03ContractTest,MockTuxedoClientWorkDateTest,TuxedoCSourceContractTest' test
```

Result: `BUILD SUCCESS`, 82 tests run, 0 failures, 0 errors, 0 skipped.

The maximum-timeout regression was also rerun alone after the fix: 1 test run, 0 failures.

## Implemented fixes

### 1. Complete non-query Jolt metadata

- Completed `CNAPS5701E`, `CNAPS5701U`, `CNAPS5701D`, `CNAPS5702I`, `CNAPS5702A`, and `CNAPS5702R` in `tuxedo/jolt/cnaps_services.bulk`.
- Each block contains the two internal request identifiers, the complete voucher field set, and scalar `RESP_CODE`/`RESP_MSG` with `outerr` access.
- Request/response fields are `inout`, response-only voucher fields are `out`, and `VERSION_NO` is `long`.
- Self-review parser result: every service has 44 unique params, no duplicates, and zero `count=` declarations.

### 2. Correct Jolt timeout units

- `JoltTuxedoClient` converts positive milliseconds to ceiling seconds with a minimum of one second.
- Conversion uses `long` intermediate arithmetic to avoid overflow.
- Fake Jolt attributes prove `30000 -> 30`, `1 -> 1`, and `Integer.MAX_VALUE -> 2147484`.

### 3. Align required/dictionary validation

- Native create no longer defaults required `BUSINESS_TYPE`, `PRIORITY`, or `SYSTEM_TYPE`; required-field checking returns `2001` before persistence.
- Mock and native create reject unsupported values with `2003`.
- Mock and native update reject supplied unsupported mutable dictionary values with `2003`.
- Supported values are exactly `02102`, `NORM`, `CNAPS`, `1`, `1`, `0`, and `FAX_FLAG` `0|1`.
- Optional native create defaults are applied only after required, date, money, and dictionary validation.

### 4. Reject malformed query workDate

- Java strict `yyyy-MM-dd` validation is centralized in `RequestSupport`.
- Both voucher collection endpoints return HTTP 400 with exactly `respCode`, `respMsg`, and `data`, and do not invoke Tuxedo for explicit malformed/calendar-invalid values.
- Absent or blank values still receive the server current-date default.
- Mock direct queries return `2002` for explicit invalid dates.
- Native create/update/query services share `cnaps_valid_work_date`; both native query services validate before calling `db_query_vouchers`.

### 5. Bank keyword behavior

- Mock and native bank queries now match `keyword` against either bank name or bank number.

### 6. Review-return comment preservation

- Mock and native review-return paths store and return an optional `reviewComment` alongside the required reject reason.

## Exact files changed

Production and deployment metadata:

- `tuxedo/jolt/cnaps_services.bulk`
- `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`
- `tuxedo-server/include/cnaps_service.h`
- `tuxedo-server/src/common/validation_helper.c`
- `tuxedo-server/src/services/bank_query.c`
- `tuxedo-server/src/services/cnaps_create.c`
- `tuxedo-server/src/services/cnaps_query.c`
- `tuxedo-server/src/services/cnaps_review.c`
- `tuxedo-server/src/services/cnaps_update.c`

Tests and test doubles:

- `web-fe/src/test/java/bea/jolt/JoltSessionAttributes.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientV03ContractTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java`
- `.superpowers/sdd/final-fix-report.md`

## Full verification

### Maven clean build

Command:

```text
mvn -f web-fe/pom.xml clean test package
```

Result: `BUILD SUCCESS`, 103 tests run, 0 failures, 0 errors, 0 skipped.

WAR verified at `web-fe/target/ruisui-bank-sim.war` (2,291,063 bytes at verification time).

### Git Bash contracts and syntax

The unqualified Windows `bash.exe` resolved to an unconfigured WSL launcher and could not find `/bin/bash`. The repository's actual Git Bash was then invoked explicitly at `D:\develop\Git\Git\bin\bash.exe`.

- `scripts/tests/cnapsctl-test.sh`: `PASS: cnapsctl lifecycle contract`
- `scripts/tests/install-systemd-test.sh`: `PASS: systemd installation contract`
- `bash -n scripts/smoke-test.sh`: exit 0

### Stale-contract and diff checks

- Stale business-header/response/pagination search: no matches.
- `git diff --check`: exit 0.
- Metadata uniqueness/count audit: passed for all six single-record service blocks.
- Native database helper and its `ORDER BY BILL_ID` query/detail ordering were not changed.
- No account/auth/role/permission subsystem or same-operator review restriction was added.

## Self-review

- Reviewed all Java/native changes and parsed each amended Jolt block for duplicate params and repeated counts.
- Corrected timeout overflow found during review with its own RED/GREEN regression.
- Confirmed validation runs before native transactions/database query calls.
- Confirmed review lifecycle status checks and same-operator behavior remain unchanged.
- Confirmed only intended files are modified; `.idea/` remains the user's untracked content.

## Environment limitations

- Native Tuxedo/FML32/OCI headers, libraries, server runtime, and Oracle connection are unavailable in this Windows worktree, so native C compilation and live native-service execution were not performed.
- Native changes are covered here by source-contract tests only and still require compilation/linking plus live Tuxedo/Oracle verification in the target Linux environment.
- Real Jolt runtime connectivity was not exercised; Java-side timeout behavior is verified with the fake Jolt classes and metadata is verified structurally.
