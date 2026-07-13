# Voucher List Range-Only Work Date Filter Design

## Goal

Make `startWorkDate` and `endWorkDate` the only supported work-date query fields for both voucher list APIs, complete their Jolt transport metadata, and remove exact `workDate` filtering from the list-query backend.

The affected HTTP endpoints are:

- `POST /api/cnaps/vouchers/query`
- `POST /api/cnaps/vouchers/review-list`

This change does not remove the voucher record field `workDate`. Voucher creation, voucher update, detail responses, and list record responses continue to use `workDate` / `WORK_DATE` as before.

## HTTP Contract

Both list endpoints accept these optional JSON Body fields:

- `startWorkDate`: inclusive lower work-date bound in strict `yyyy-MM-dd` format.
- `endWorkDate`: inclusive upper work-date bound in strict `yyyy-MM-dd` format.

Either bound may be supplied independently. When neither bound is supplied, the query does not filter by work date. Blank `startWorkDate` and `endWorkDate` values are removed and treated as absent.

The legacy list-query field `workDate` is unsupported. If the request JSON object contains the `workDate` key, including a `null`, empty, or whitespace-only value, WebFE returns HTTP 400 with business code `2002` and the exact message:

```text
列表查询不支持 workDate，请使用 startWorkDate/endWorkDate
```

This explicit rejection prevents a retired filter from being silently ignored and unintentionally widening a query.

Invalid calendar dates, values not strictly formatted as `yyyy-MM-dd`, or `startWorkDate > endWorkDate` continue to return HTTP 400 / business code `2002` with the existing range-validation messages.

## WebFE Processing

`CnapsVoucherServlet` continues to identify the two POST list paths and validates their JSON Body before calling Tuxedo.

The list-filter validator performs checks in this order:

1. Reject the presence of the `workDate` key with the retired-field message.
2. Remove blank `startWorkDate` and `endWorkDate` values.
3. Validate each supplied bound as a real strict ISO local date.
4. Reject a range whose lower bound is later than its upper bound.

Valid fields map to the FML32 names `START_WORK_DATE` and `END_WORK_DATE`. WebFE continues to overlay trusted request, operator, and branch context. It does not inject `WORK_DATE` into a list request.

## Jolt and FML32 Transport

The existing global FML32 fields remain:

- `WORK_DATE`, because non-list operations and voucher result records require it.
- `START_WORK_DATE` and `END_WORK_DATE`, for list-query inputs.

The Jolt service metadata for `CNAPS4609Q` and `CNAPS5702Q` registers `START_WORK_DATE` and `END_WORK_DATE` as scalar string input fields. `WORK_DATE` is no longer a list-service input. It remains available as an output occurrence field because each returned voucher record contains its work date.

Both the checked-in bulk metadata and the metadata-generation path must agree so a deployed Jolt repository accepts the two range parameters.

## Tuxedo and Database Processing

`CNAPS4609Q` and `CNAPS5702Q` read and validate only `START_WORK_DATE` and `END_WORK_DATE` as date filters. The exact-date input buffer, validation, mutual-exclusion validation, and `db_query_vouchers` exact-date argument are removed.

The shared Oracle predicates retain only the range filters:

```sql
(:start_work_date IS NULL OR WORK_DATE >= TO_DATE(:start_work_date, 'YYYY-MM-DD'))
AND (:end_work_date IS NULL OR WORK_DATE < TO_DATE(:end_work_date, 'YYYY-MM-DD') + 1)
```

The upper predicate remains exclusive of the following day, which makes `endWorkDate` inclusive even if the database column contains a time component.

All non-date filters, branch scoping, review-list fixed status, deleted-record handling, ordering, and pagination remain unchanged.

## Response Contract

Successful response structure is unchanged:

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 0,
    "records": []
  }
}
```

Each returned record may still contain `workDate`; only the request-side exact-date query field is removed.

## Testing

Tests will cover:

- both POST list paths forwarding `startWorkDate` and `endWorkDate` as canonical FML32 fields;
- either bound working independently and both endpoints including boundary dates;
- neither bound producing no work-date input filter;
- every request containing the `workDate` key, including null and blank values, returning HTTP 400 / `2002` with the exact retired-field message and not calling Tuxedo;
- malformed dates, nonexistent dates, and reversed ranges returning `2002`;
- both list Jolt metadata blocks accepting the range inputs and not accepting `WORK_DATE` as an input filter;
- C service and database contract tests proving exact-date list-query code is absent and range predicates remain;
- existing create, update, detail, and response-record `workDate` behavior remaining intact;
- public API documentation listing only the two range request fields.

## Deployment

Deploy the updated WebFE artifact, rebuild and redeploy the Tuxedo server, regenerate or reload the Jolt repository metadata, and restart or refresh the affected runtime components according to the existing deployment process. WebFE and Jolt/Tuxedo changes must be deployed together so range fields are accepted end to end.
