# Task 3 Report: Voucher Update, Delete, Review Pass, And Review Return

## Implementation Summary

Implemented the voucher lifecycle surface for update, delete, review pass, and review return.

Changed the controller to expose:
- `PUT /api/cnaps/vouchers/{billId}`
- `POST /api/cnaps/vouchers/{billId}/delete`
- `POST /api/cnaps/vouchers/{billId}/review-pass`
- `POST /api/cnaps/vouchers/{billId}/review-return`

Added lifecycle request DTOs:
- `DeleteRequest`
- `ReviewPassRequest`
- `ReviewReturnRequest`

Extended the service and mapper to support:
- update from pending/rejected back to pending review
- delete with delete metadata
- review pass with checker metadata and review comment
- review return with reject reason and checker metadata
- version incrementing across lifecycle transitions
- null-safe lifecycle response serialization

Extended the entity and response model with lifecycle fields so the API can return the expected state after each transition.

Updated exception handling so lifecycle rule violations return the expected HTTP statuses:
- `3003` -> `409 Conflict`
- `3004` -> `409 Conflict`
- `3005` -> `403 Forbidden`

## TDD Evidence

### RED

Ran:
```bash
mvn -f web-war/pom.xml test -Dtest=CnapsVoucherLifecycleApiTest
```

Observed failure before implementation:
- all four lifecycle tests failed
- routes were missing and Spring resolved the lifecycle requests to `NoResourceFoundException`
- this confirmed the test was exercising unimplemented behavior

### GREEN

After implementation, reran:
```bash
mvn -f web-war/pom.xml test -Dtest=CnapsVoucherLifecycleApiTest
```

Result:
- `4` tests passed

Then ran the full module suite:
```bash
mvn -f web-war/pom.xml test
```

Result:
- `16` tests passed

## Files Changed

### New
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/DeleteRequest.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/ReviewPassRequest.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/ReviewReturnRequest.java`
- `web-war/src/test/java/com/ruisui/bank/sim/CnapsVoucherLifecycleApiTest.java`

### Modified
- `web-war/src/main/java/com/ruisui/bank/sim/api/CnapsVoucherController.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/GlobalExceptionHandler.java`
- `web-war/src/main/java/com/ruisui/bank/sim/api/dto/VoucherResponse.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/ErrorCode.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/LastAction.java`
- `web-war/src/main/java/com/ruisui/bank/sim/domain/VoucherStatus.java`
- `web-war/src/main/java/com/ruisui/bank/sim/persistence/CnapsBillPoc.java`
- `web-war/src/main/java/com/ruisui/bank/sim/service/CnapsVoucherService.java`
- `web-war/src/main/java/com/ruisui/bank/sim/service/VoucherMapper.java`

## Self-Review

- The lifecycle routes are wired through the same header resolution and API envelope used by the existing create/query/detail endpoints.
- Status transitions match the brief, including self-review prevention and repeated-review rejection.
- Update clears the rejection state and increments version number as required.
- Response serialization omits null lifecycle fields so rejected/cleared fields do not appear spuriously in JSON.
- Focused and full test runs both passed.

## Concerns

None at the moment.
