# Server Context and Editable Work Date Design

**Date:** 2026-07-10

## Goal

Remove all business common request headers from the frontend API. WebFE generates or resolves trusted request context on the server, while `workDate` becomes an explicit voucher input: required in create payloads, optional in update payloads, and optional as a list query parameter.

This design extends and partially supersedes `2026-07-10-server-configured-operator-no-design.md`. Its server-configured operator behavior remains valid, but `requestId`, `branchNo`, and `workDate` are no longer caller-controlled common headers.

## Scope

The change covers WebFE runtime configuration and request mapping, mock behavior, frontend API documentation, Jolt metadata, Tuxedo C create/update behavior, Oracle update SQL, deployment configuration, and automated contract tests.

It does not add authentication, change the Oracle schema, rename FML32 fields, regenerate voucher identifiers, or add a separate work-date endpoint.

## Public HTTP Contract

The API no longer defines business common request headers. `Content-Type: application/json` remains required for endpoints with JSON bodies because it is a protocol header rather than business context.

The removed caller-controlled headers are:

- `requestId`
- `operatorNo`
- `branchNo`
- `workDate`

WebFE generates `requestId` as `REQ-<timestamp>`. WebFE resolves `operatorNo` and `branchNo` from server runtime configuration. Browser-supplied values with these names must not override trusted context, regardless of whether they arrive as headers, JSON keys, or query parameters.

## Server Runtime Configuration

The existing fixed operator configuration remains:

1. JVM property `webfe.poc.operatorNo`
2. Environment variable `POC_OPERATOR_NO`
3. Application property `webfe.poc.operatorNo`
4. Servlet context parameter `poc.operatorNo`
5. Default `77210021`

The fixed branch configuration uses the same precedence:

1. JVM property `webfe.poc.branchNo`
2. Environment variable `POC_BRANCH_NO`
3. Application property `webfe.poc.branchNo`
4. Servlet context parameter `poc.branchNo`
5. Default `772`

`conf/app.properties` documents both fixed values. `scripts/configure-tomcat.sh` persists both environment variables and corresponding JVM properties.

## WebFE Request Mapping

`TuxedoRuntimeConfig` exposes `pocOperatorNo` and `pocBranchNo`. `BaseJsonServlet` initializes both values from the cached runtime configuration and generates a new request ID for every call without reading HTTP headers.

`TuxedoRequestMapper.from` accepts the generated request ID, configured operator number, configured branch number, and endpoint fields. It maps endpoint fields first, then overlays the trusted fields:

- `REQUEST_ID`
- `REQ_ID`
- `OPERATOR_NO`
- `BRANCH_NO`

`WORK_DATE` is no longer overlaid as trusted common context. The existing `workDate` field-name mapping produces `WORK_DATE` from a JSON body or query parameter.

`RequestSupport` removes all common-header parsing. It retains path and query helpers and provides server-side request-ID generation. For the voucher collection and review-list GET endpoints, WebFE inserts the server's current date when the `workDate` query parameter is absent. Detail GET requests do not need a work date.

## Create Voucher

`POST /api/cnaps/vouchers` requires JSON field `workDate` in `yyyy-MM-dd` form. The field is forwarded as `WORK_DATE`.

Both the mock client and `CNAPS5701E` treat a missing or blank work date as required-field error `2001`. The C service no longer supplies a hard-coded work-date fallback. The supplied date continues to participate in serial-number allocation, `BILL_ID` generation, and Oracle `WORK_DATE` persistence.

Date-format validation remains unchanged. A malformed nonblank date can still reach Oracle and return the existing `4001` database error.

## Update Voucher

`PUT /api/cnaps/vouchers/{billId}` accepts optional JSON field `workDate`.

When present, WebFE forwards it as `WORK_DATE`, Jolt metadata exposes it for `CNAPS5701U`, and the C update service copies it into `row.work_date`. Oracle updates with:

```sql
WORK_DATE=COALESCE(TO_DATE(:work_date, 'YYYY-MM-DD'), WORK_DATE)
```

When absent or blank, the existing work date remains unchanged. The mock client follows the same behavior.

Changing `workDate` does not change `BILL_ID` or `SERIAL_NO`. The identifier remains the immutable creation identifier even if its embedded date no longer matches the current business date. If the target `(WORK_DATE, BRANCH_NO, SERIAL_NO)` conflicts with the existing unique index, the operation keeps the current database-error behavior and returns `4001`.

## List Queries

The following endpoints accept optional query parameter `workDate`:

- `GET /api/cnaps/vouchers`
- `GET /api/cnaps/vouchers/review-list`

When omitted, WebFE supplies the server's current date. `branchNo` always comes from server configuration. Existing status and pagination behavior remains unchanged.

## Jolt and Tuxedo Changes

The `CNAPS5701U` service metadata includes `WORK_DATE` as an `inout` string. Metadata generation scripts and static bulk metadata must agree.

`CNAPS5701E` requires `WORK_DATE` instead of using its hard-coded `2026-07-09` fallback. `CNAPS5701U` reads optional `WORK_DATE`. The shared database binding already binds `:work_date`; the update SQL adds the conditional assignment.

No FML32 field definition or Oracle schema change is required because both already contain `WORK_DATE`.

## Error Handling

- Create without `workDate`: HTTP 400 / `respCode=2001`.
- Update without `workDate`: succeeds without changing the stored date.
- Invalid nonblank date: existing database path, normally HTTP 500 / `respCode=4001`.
- Target-date unique-index conflict: HTTP 500 / `respCode=4001`.

## Documentation

`docs/cnaps-frontend-api.md` removes the business common-request-header section and all related curl headers. It documents:

- server-generated request ID;
- server-configured operator and branch values;
- required create-body `workDate`;
- optional update-body `workDate`;
- optional list-query `workDate` with current-date default;
- immutable `billId` and `serialNo` when the work date changes.

## Testing

Automated tests verify:

- branch configuration precedence and default;
- servlet requests ignore all removed common headers;
- mapper protects request ID, operator number, and branch number but accepts endpoint `workDate`;
- list endpoints default missing `workDate` and preserve an explicit query value;
- create rejects missing `workDate` in mock and C source contracts;
- mock update changes `WORK_DATE` without changing `BILL_ID` or `SERIAL_NO`;
- C update reads `WORK_DATE`, update SQL persists it conditionally, and Jolt metadata exposes it;
- deployment artifacts include `POC_BRANCH_NO`;
- frontend API documentation contains no removed common-header examples;
- the complete WebFE Maven test suite passes.

Local verification cannot execute Oracle Tuxedo or Oracle Database, so native C/OCI behavior is verified through source/metadata contract tests in addition to the Java mock behavior.
