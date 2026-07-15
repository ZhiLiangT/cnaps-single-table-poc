# CNAPS Single-Table POC Rules

## Project Scope

- This repository is a backend-only Java Servlet, Jolt/Tuxedo C, and Oracle POC.
- Do not add JSP, JavaScript, CSS, frontend frameworks, or browser pages.
- Preserve the existing HTTP response envelope and FML32 naming conventions.

## Voucher Review Task

The sources of truth are loaded from:

- `docs/cnaps-review-requirements.md`
- `docs/cnaps-review-design.md`
- `docs/cnaps-frontend-api.md` only when an API detail is not already defined by the first two files

Implement the review task exactly as designed:

- `review-list` reuses `CNAPS4609Q` with server-forced `STATUS=10_PENDING_REVIEW`.
- Add only `CNAPS5702A` and `CNAPS5702R`.
- Never add `CNAPS5702Q`.
- Do not modify Mock behavior, tests, frontend resources, operations documentation, or deployment scripts.
- Do not restore an old review implementation from Git history.
- Do not refactor unrelated CRUD, query, Jolt client, or response-mapping code.

For this task, production changes are limited to:

1. `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java`
2. `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java`
3. `tuxedo-server/src/services/cnaps_review.c`
4. `tuxedo-server/src/common/db_helper.c`
5. `tuxedo-server/src/cnapspocsvr.c`
6. `tuxedo-server/Makefile`
7. `tuxedo/UBBCONFIG`
8. `tuxedo/jolt/cnaps_services.bulk`

## Existing Patterns to Reuse

- Servlet routing and list validation: `CnapsVoucherServlet` and `RequestSupport`.
- HTTP-to-service mapping: existing query and delete branches in `TuxedoRequestMapper`.
- Native field reading, errors, and transactions: `cnaps_delete.c`.
- Audit fields and optimistic update: `cnaps_update.c` and `db_helper.c`.
- Status validation: `cnaps_status.h` and `validation_helper.c`.
- FML32 output: `fml_helper.c`.

## Completion Rules

- Finish all eight production files before reporting completion.
- Keep action requests bodyless and return only the six fields defined in the design.
- Preserve `BILL_ID + VERSION_NO` optimistic locking and map zero updated rows to `3004`.
- Use Oracle `SYSTIMESTAMP` for review time.
- Compile Java with `mvn -f web-fe/pom.xml -DskipTests package`.
- Do not commit, push, synchronize, or deploy unless explicitly requested.
- Report the actual modified files and any unresolved compiler issue.
