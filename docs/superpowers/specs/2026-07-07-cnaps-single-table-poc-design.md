# CNAPS Single-Table POC Design

## Context

The attached PRD is `ruisui-bank-tuxedo-light-prd-linux-v0.3-single-table-poc.docx`, dated 2026-07-07. It defines a local Linux POC for an old-style bank Tuxedo backend simulation. The current workspace contains documentation only and no existing source modules, so this design scaffolds the POC from scratch in `D:\Project\ruisui`.

The implementation target is the v0.3 boundary, not the larger v1.1 production-style design. The POC must prove the WebFE -> Tuxedo-style service boundary -> Oracle single-table business lifecycle. Because this Windows workspace does not provide a real Linux Tuxedo and Oracle runtime, the runnable local implementation will use a Spring Boot WebFE/business simulator with H2-compatible tests, while also producing the Linux/Tuxedo/Oracle artifacts required by the PRD.

## Goals

- Provide runnable HTTP APIs for CNAPS POC operations.
- Persist vouchers in a single business table model equivalent to `T_CNAPS_BILL_POC`.
- Cover the minimal business lifecycle: create, update, delete, general query, review-list query, detail, review pass, and review return.
- Enforce the v0.3 validation rules and state transitions.
- Generate the deployment artifacts named by the PRD: Oracle schema, FML32 field table, UBBCONFIG skeleton, Linux environment script, and start/stop/status scripts.
- Add automated tests for the PRD acceptance cases that can run in this workspace without external Oracle or Tuxedo.

## Non-Goals

- No login, logout, session expiry, password, captcha, user-role-permission, menu, or button authorization.
- No real CNAPS, CIPS, TIPS, core banking, account debit, fee posting, certificate, hardware, AIX, HA, or external system integration.
- No multi-table audit, dictionary, bank-info, serial, request-log, or bill-flow schema in the POC runtime.
- No WebFE direct business-table write bypass in the design model; the Spring service represents the Tuxedo business execution layer for local verification.

## Architecture

The workspace will contain a small Java project and deployment skeleton:

- `web-war/`: Spring Boot application packaged as a WAR/JAR-style WebFE POC. It exposes JSON-over-HTTP endpoints matching the PRD and delegates all business rules to a service layer that represents the Tuxedo transaction services.
- `web-war/src/main/java/.../api`: controllers and request/response DTOs.
- `web-war/src/main/java/.../service`: business lifecycle, validation, status transitions, serial generation, and error-code mapping.
- `web-war/src/main/java/.../persistence`: JPA entity and repository for `T_CNAPS_BILL_POC`.
- `web-war/src/main/resources/application.yml`: H2 local test profile and app defaults.
- `sql/schema.sql`: Oracle DDL for the single POC table and indexes.
- `tuxedo-server/fml/cnaps_poc.fml32`: field table matching the PRD.
- `tuxedo/UBBCONFIG`: minimal single-server Tuxedo domain skeleton.
- `conf/env.linux.sh`, `conf/dicts.properties`, `conf/banks.properties`: Linux runtime configuration and POC dictionaries.
- `scripts/start.sh`, `scripts/stop.sh`, `scripts/status.sh`: deployment helper scripts.

This keeps the locally testable business behavior separate from the Linux/Tuxedo deployment skeleton. The API and service names keep the old transaction-code flavor so the POC can later be replaced with real ATMI/Jolt calls without changing front-end endpoints.

## API Surface

All business APIs accept common request headers:

- `requestId`: required request trace ID.
- `operatorNo`: required operator number.
- `branchNo`: required branch number.
- `workDate`: required business date in `YYYY-MM-DD`.
- `channel`: optional, default `WEBFE`.

Endpoints:

- `GET /api/health`: returns application/database health and constants for POC smoke tests.
- `GET /api/dicts/{dictType}`: returns configured dictionary values.
- `GET /api/banks`: returns configured bank rows, optionally filtered by bank number or keyword.
- `POST /api/cnaps/vouchers`: maps to `CNAPS5701E`; creates a voucher in `10_PENDING_REVIEW`.
- `PUT /api/cnaps/vouchers/{billId}`: maps to `CNAPS5701U`; updates pending/rejected vouchers and returns them to `10_PENDING_REVIEW`.
- `POST /api/cnaps/vouchers/{billId}/delete`: maps to `CNAPS5701D`; logically deletes pending/rejected vouchers.
- `GET /api/cnaps/vouchers`: maps to `CNAPS4609Q`; general paginated query by status, operator, serial, and work date.
- `GET /api/cnaps/vouchers/review-list`: maps to `CNAPS5702Q`; defaults to `10_PENDING_REVIEW`.
- `GET /api/cnaps/vouchers/{billId}`: maps to `CNAPS5702I`; returns full detail and last-action fields.
- `POST /api/cnaps/vouchers/{billId}/review-pass`: maps to `CNAPS5702A`; approves pending vouchers.
- `POST /api/cnaps/vouchers/{billId}/review-return`: maps to `CNAPS5702R`; rejects pending vouchers with a reason.

Every response follows:

```json
{
  "success": true,
  "respCode": "0000",
  "respMsg": "交易成功",
  "data": {}
}
```

Failures set `success=false`, keep `requestId` in the response when available, and return one of the lightweight PRD error codes.

## Data Model

The runtime entity and Oracle DDL are aligned to `T_CNAPS_BILL_POC`.

Required identifiers and lifecycle fields:

- `BILL_ID`: generated as `B` + `yyyyMMdd` + six-digit sequence for the day.
- `WORK_DATE`, `BRANCH_NO`, `OPERATOR_NO`, `SERIAL_NO`.
- `STATUS`: one of `10_PENDING_REVIEW`, `20_REVIEW_APPROVED`, `30_REVIEW_REJECTED`, `40_DELETED`.
- `CHECKER_NO`, `CHECKER_TIME`, `REVIEW_COMMENT`, `REJECT_REASON`.
- `DELETE_REASON`, `DELETE_OPERATOR_NO`, `DELETE_TIME`.
- `LAST_ACTION`, `LAST_OPERATOR_NO`, `LAST_REQUEST_ID`, `LAST_ACTION_TIME`.
- `CREATED_AT`, `UPDATED_AT`, `VERSION_NO`.

Business fields cover the v0.3 required 5701/5702 surface: business type, account segments, account name, payer name, payee account/name, priority, receiving bank, system type, amount, debit mode, fee amount, fee charge mode, send mode, fax flag, voucher number, and remark.

Serial generation in the local POC uses the current maximum serial number for `(workDate, branchNo)` and starts from `0002000`, matching the PRD. This is acceptable because the PRD excludes concurrent serial-generation hardening from v0.3.

## Business Rules

Create:

- `workDate`, `operatorNo`, `branchNo`, `businessType`, `accountPart1`, `accountPart2`, `accountPart3`, `payeeAccountNo`, `payeeName`, `priority`, `amount`, and key dictionary values are required.
- Amount must be greater than zero with no more than two decimal places.
- Default supported dictionary values: `BUSINESS_TYPE=02102`, `PRIORITY=NORM`, `FEE_CHARGE_MODE=1`, `SEND_MODE=0`, `DEBIT_MODE=1`, `FAX_FLAG=0/1`, `SYSTEM_TYPE=CNAPS`.
- Successful create sets status to `10_PENDING_REVIEW`, writes `LAST_ACTION=CREATE`, and returns `billId`, `serialNo`, status, operator, and amount.

Update:

- Only `10_PENDING_REVIEW` and `30_REVIEW_REJECTED` can be updated.
- Update re-runs create-field validation.
- Successful update sets status to `10_PENDING_REVIEW`, clears the active reject reason, increments `VERSION_NO`, and writes `LAST_ACTION=UPDATE`.

Delete:

- Only `10_PENDING_REVIEW` and `30_REVIEW_REJECTED` can be deleted.
- Delete is logical and sets `STATUS=40_DELETED`, `DELETE_OPERATOR_NO`, `DELETE_TIME`, `DELETE_REASON`, and `LAST_ACTION=DELETE`.

Review pass:

- Only `10_PENDING_REVIEW` can be approved.
- `operatorNo` in the request must differ from the voucher's original `OPERATOR_NO`.
- Successful pass sets `STATUS=20_REVIEW_APPROVED`, `CHECKER_NO`, `CHECKER_TIME`, optional `REVIEW_COMMENT`, and `LAST_ACTION=REVIEW_PASS`.
- Repeating the review after status changes returns `3004` or `3003`; the implementation will use `3004` for a previously existing voucher whose status no longer allows review.

Review return:

- Only `10_PENDING_REVIEW` can be returned.
- Reviewer cannot equal the original input operator.
- `rejectReason` is required and limited to 200 characters.
- Successful return sets `STATUS=30_REVIEW_REJECTED`, `CHECKER_NO`, `CHECKER_TIME`, `REJECT_REASON`, and `LAST_ACTION=REVIEW_RETURN`.

Query/detail:

- General query defaults to the request `workDate` and `branchNo`.
- Review-list query defaults to `10_PENDING_REVIEW`.
- Pagination is bounded to a maximum page size of 50.
- Detail returns full voucher data and last-action fields for audit-style visibility.

## Error Handling

The POC will centralize errors in an enum and one exception handler.

- `0000`: success.
- `2001`: required field is empty.
- `2002`: field format error.
- `2003`: dictionary value invalid.
- `3001`: voucher not found.
- `3003`: operation not allowed in current status.
- `3004`: voucher status changed, such as repeated review.
- `3005`: reviewer cannot review their own input voucher.
- `4001`: database or persistence error.
- `4002`: Tuxedo service timeout response used by the WebFE gateway when a real Tuxedo call times out.
- `4003`: Tuxedo service unavailable response used by the WebFE gateway when a real Tuxedo route is unavailable.
- `9999`: unknown error.

Controllers will return business-friendly `respMsg` values and avoid exposing stack traces in responses.

## Testing Strategy

The automated test suite will use Spring Boot integration tests with H2 in Oracle compatibility mode. Tests will drive HTTP endpoints instead of calling service internals, so they verify the POC API contract and lifecycle.

Required tests:

- `TC-ENV-002`: health endpoint returns `0000`.
- `TC-5701-001`: full voucher create returns `billId`, `serialNo`, and `10_PENDING_REVIEW`.
- `TC-5701-002`: missing payee account returns `2001`.
- `TC-5701-003`: amount `0` returns `2002`.
- `TC-4609-001`: general query can find a created voucher.
- `TC-5702-001`: detail returns full fields and last-action data.
- `TC-5702-002`: review pass by `77210022` changes status to `20_REVIEW_APPROVED`.
- `TC-5702-003`: repeated review returns `3004`.
- `TC-5702-004`: input operator reviewing their own voucher returns `3005`.
- `TC-5702-005`: review return stores `30_REVIEW_REJECTED` and `REJECT_REASON`.
- `TC-5701-002` variant: rejected voucher update returns to `10_PENDING_REVIEW` and increments `VERSION_NO`.
- `TC-5701-003`: pending voucher delete sets `40_DELETED`.
- `TC-5701-004`: approved voucher delete returns `3003`.

Manual deployment evidence for Linux/Tuxedo remains script and config generation, because this workspace cannot run Oracle Tuxedo.

## Acceptance Evidence

The implementation is complete when:

- `web-war` builds successfully.
- The HTTP integration tests pass locally.
- `sql/schema.sql` contains exactly the single business table and required indexes.
- `tuxedo/UBBCONFIG` exposes `SYSHEALTH`, `DICTQRY`, `BANKQRY`, `CNAPS5701E`, `CNAPS5701U`, `CNAPS5701D`, `CNAPS4609Q`, `CNAPS5702Q`, `CNAPS5702I`, `CNAPS5702A`, and `CNAPS5702R`.
- `tuxedo-server/fml/cnaps_poc.fml32` includes the common and voucher fields from the PRD.
- `conf` and `scripts` contain Linux runtime templates aligned to `/opt/ruisui-bank-sim`.

## Open Decisions Resolved for POC

- Use Spring Boot as the runnable local simulator because no existing Java/Tuxedo source tree is present.
- Keep the database model single-table for the POC, matching v0.3.
- Treat Tuxedo artifacts as generated deployment skeletons in this workspace.
- Use H2 only for local automated verification; Oracle DDL remains authoritative for target deployment.
