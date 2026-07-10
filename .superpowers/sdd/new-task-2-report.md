# Task 2 Report

## Status

DONE

## Summary

- Changed `TuxedoRequestMapper.from` to the four-argument trusted-context API. Endpoint `workDate` maps to `WORK_DATE`; endpoint request/operator/branch values cannot override server values.
- Added collection-list work-date defaulting for `/api/cnaps/vouchers` and `/api/cnaps/vouchers/review-list` only. Explicit values remain unchanged and detail requests receive no default.
- Made mock create require a nonblank `WORK_DATE`; updates may change or omit it while preserving `BILL_ID` and `SERIAL_NO`.

## TDD Evidence

### Mapper cycle

- RED: `mvn -f web-fe/pom.xml "-Dtest=TuxedoRequestMapperTest,V03TuxedoContractTest" test`
- Expected failure: test compilation reported that `TuxedoRequestMapper.from` still required five arguments, including trusted `workDate`.
- GREEN: the same focused command passed 5 tests with 0 failures/errors after the signature and caller changes.

### List-date helper cycle

- RED: `mvn -f web-fe/pom.xml "-Dtest=RequestSupportTest" test`
- Expected failure: test compilation could not find `RequestSupport.includeDefaultWorkDate`.
- GREEN: the focused helper command passed 7 tests with 0 failures/errors after the helper implementation.

### Collection routing cycle

- RED: `mvn -f web-fe/pom.xml "-Dtest=BaseJsonServletTest" test`
- Expected failure: collection GET lacked the expected `WORK_DATE` while explicit and detail behavior already passed.
- GREEN: the focused servlet command passed 4 tests with 0 failures/errors after exact-path wiring.

### Mock cycle

- RED: `mvn -f web-fe/pom.xml "-Dtest=MockTuxedoClientWorkDateTest" test`
- Expected failures: create without `WORK_DATE` returned `0000` instead of `2001`, and update allowed a supplied `SERIAL_NO` to replace the stored number.
- GREEN: the focused mock command passed 3 tests with 0 failures/errors after create validation and identifier protection.

## Final Verification

- Focused: `mvn -f web-fe/pom.xml "-Dtest=TuxedoRequestMapperTest,V03TuxedoContractTest,RequestSupportTest,MockTuxedoClientWorkDateTest,BaseJsonServletTest" test` — 20 tests, 0 failures, 0 errors.
- Full WebFE: `mvn -f web-fe/pom.xml clean test` — 34 tests, 0 failures, 0 errors.
- `git diff --check` — no whitespace errors.
- Independent read-only review found no production defects. Its important optional-update coverage gap and minor midnight-boundary concern were addressed before final verification.

## Changed Files

- `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/BaseJsonServlet.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java`
- `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientWorkDateTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java`
- `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/V03TuxedoContractTest.java`
- `.superpowers/sdd/new-task-2-report.md`

## Commits

- `26be7a2` — endpoint-owned work-date implementation and primary tests.
- `d194bab` — optional-update regression coverage and midnight-safe date assertions.

## Concerns

None.
