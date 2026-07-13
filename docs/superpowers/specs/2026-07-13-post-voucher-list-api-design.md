# POST Voucher List API Design

## Goal

Change only the two voucher list queries from GET requests to POST requests with JSON request bodies. Keep voucher creation, voucher detail, health, dictionary, and bank APIs unchanged.

## Public API Contract

The resulting voucher endpoints are:

| Method and path | Purpose | Tuxedo service |
|---|---|---|
| `POST /api/cnaps/vouchers` | Create a voucher | `CNAPS5701E` |
| `POST /api/cnaps/vouchers/query` | Query the general voucher list | `CNAPS4609Q` |
| `POST /api/cnaps/vouchers/review-list` | Query vouchers pending review | `CNAPS5702Q` |
| `GET /api/cnaps/vouchers/{billId}` | Get voucher details | `CNAPS5702I` |

The previous list endpoints, `GET /api/cnaps/vouchers` and `GET /api/cnaps/vouchers/review-list`, are removed without a compatibility period. They return HTTP 405 and do not call Tuxedo.

## Request Data

Both POST list endpoints consume `Content-Type: application/json` and read filters from a JSON object. Existing filter names and semantics remain unchanged, including pagination, exact work date, work date range, status, serial number, and deleted-record controls.

Example:

```json
{
  "startWorkDate": "2026-07-01",
  "endWorkDate": "2026-07-13",
  "pageNo": 1,
  "pageSize": 10
}
```

An empty JSON object queries without optional filters. Work-date validation remains limited to the two list operations and preserves the existing `2002` validation response behavior.

## Routing and Data Flow

`TuxedoRequestMapper` maps POST `/api/cnaps/vouchers/query` to `CNAPS4609Q` and POST `/api/cnaps/vouchers/review-list` to `CNAPS5702Q`. Exact list-route checks occur before the generic voucher-detail route.

`CnapsVoucherServlet.doPost` distinguishes three exact operations:

1. `/api/cnaps/vouchers` reads the JSON body and creates a voucher as today.
2. `/api/cnaps/vouchers/query` reads and validates the JSON body, then calls the general list service.
3. `/api/cnaps/vouchers/review-list` reads and validates the JSON body, then calls the pending-review list service.

`CnapsVoucherServlet.doGet` continues serving voucher detail paths. For the two retired collection GET routes it writes HTTP 405 directly and does not invoke the request mapper or Tuxedo client.

The browser query form sends a POST request to `api/cnaps/vouchers/query` with the form values serialized as JSON instead of a query string.

## Error Handling

- Invalid work dates or ranges return the existing HTTP 400 response with `respCode` `2002`.
- Retired GET list routes return HTTP 405 and never reach Tuxedo.
- Tuxedo and response mapping behavior for successful POST list calls remains unchanged.
- Voucher creation and detail errors remain unchanged.

## Documentation and Tests

Update the public API document, examples, and typical flows to use the two POST list routes and JSON bodies.

Automated tests cover:

- mapper routing for both new POST list operations;
- rejection of both retired GET list operations;
- JSON-body forwarding and work-date validation for both POST list operations;
- preservation of POST voucher creation and GET voucher detail;
- the browser query form's POST request shape where practical;
- the complete WebFE regression suite.

## Out of Scope

- Changing dictionary, bank, health, or voucher-detail GET APIs.
- Changing Tuxedo service names, FML32 fields, C services, or database queries.
- Supporting the retired GET list routes during a transition period.
- Moving or redesigning the existing voucher creation endpoint.
