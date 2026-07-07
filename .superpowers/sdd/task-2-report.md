# Task 2 Report: Voucher Create, Validation, Persistence, Query, And Detail

## Implementation Summary

Implemented the CNAPS voucher flow end to end in `web-war`:

- Added `POST /api/cnaps/vouchers`, `GET /api/cnaps/vouchers`, `GET /api/cnaps/vouchers/review-list`, and `GET /api/cnaps/vouchers/{billId}`.
- Added voucher DTOs, header context handling, voucher status/action enums, persistence entity, repository, service, and mapper.
- Added create-time validation for:
  - missing `payeeAccountNo` -> `REQUIRED_FIELD_EMPTY` / `2001`
  - zero or invalid `amount` -> `FIELD_FORMAT_ERROR` / `2002`
  - unsupported `businessType` -> `DICT_VALUE_INVALID` / `2003`
- Enabled JPA schema creation for the H2 test database with `ddl-auto: create-drop`.
- Updated global exception handling so business exceptions return HTTP 400, and `NOT_FOUND` returns HTTP 404.
- Enabled Spring Data page DTO serialization to remove the page warning and keep a stable page JSON shape.

## Files Changed

- `web-war/src/test/java/com/ruisui/bank/sim/CnapsVoucherCreateQueryApiTest.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/CnapsVoucherController.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/BankInfo.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/DictItem.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/HeaderContext.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/VoucherCreateRequest.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/VoucherResponse.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/ErrorCode.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/LastAction.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/VoucherStatus.java`
- `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPoc.java`
- `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPocRepository.java`
- `web-war/src/main/java/com/ruisui/bank/sim/service/HeaderContextResolver.java`
- `web-war/src/main/java/com/ruisui/bank/sim/service/CnapsVoucherService.java`
- `web-war/src/main/java/com/ruisui/bank/sim/service/VoucherMapper.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/GlobalExceptionHandler.java`
- `web-war/src/main/java/com/ruisui/bank/sim/RuisuiBankSimApplication.java`
- `web-war/src/main/resources/application.yml`

## TDD Evidence

### RED

Ran:

```bash
mvn -f web-war/pom.xml test -Dtest=CnapsVoucherCreateQueryApiTest
```

Result before implementation:

- 4 failures
- Requests to `/api/cnaps/vouchers` were handled by `ResourceHttpRequestHandler` because the controller did not exist yet.
- Validation expectations also failed because the voucher flow was not implemented.

### GREEN

Ran the same focused test after implementation:

```bash
mvn -f web-war/pom.xml test -Dtest=CnapsVoucherCreateQueryApiTest
```

Result:

- 4 tests run
- 0 failures
- 0 errors

### Full Suite

Ran:

```bash
mvn -f web-war/pom.xml test
```

Result:

- 7 tests run
- 0 failures
- 0 errors

## Self-Review

- Spec coverage: create, validate, persist, query, and detail behavior are implemented; the existing health/reference tests still pass.
- Placeholder scan: no TODO/TBD placeholders were introduced in the production path.
- Type consistency: request DTOs, entity fields, repository signatures, and service/controller return types all line up with the task brief and the tests.

## Concerns

- No functional concerns from the implemented feature.
- Maven/Surefire still prints a Java agent warning on this environment's JDK 21 runtime; the build and tests still pass.
