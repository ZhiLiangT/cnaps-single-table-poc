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

## Safe Editing Rules

- Modify existing files with small anchored patches; never regenerate or rewrite an existing file in full.
- Never use broad string replacement to insert C declarations. The anchor and all original lines must remain.
- Preserve line order, repeated-field metadata, and every existing `count=0` unless the design explicitly changes it.
- Inspect the diff immediately after editing each existing file. Stop and correct the edit if unrelated lines changed.

For `tuxedo-server/src/cnapspocsvr.c`:

- Insert `CNAPS5702A/R` immediately after the existing `CNAPS5702I` declaration.
- Keep `CNAPS5702I` present exactly once.
- Expected diff: 2 additions and 0 deletions.

For `tuxedo/jolt/cnaps_services.bulk`:

- Append the two review service blocks at EOF only.
- Never parse and reserialize, reformat, or rewrite the existing metadata.
- Expected diff: 82 additions and 0 deletions.
- `DICT_TYPE` must still have `count=0` after the edit.

## Environment and Verification

- The current machine may not have Tuxedo `buildserver`, Tuxedo headers, or Oracle SDK headers.
- Do not run native C LSP diagnostics or `make` unless `buildserver`, `atmi.h`, and `oci.h` are all available.
- Missing external C headers or `buildserver` are known environment limitations; do not modify project code to silence them.
- After all eight files are complete, run the cheap structural checks first:
  - `git diff --check`
  - verify `CNAPS5702I`, `CNAPS5702A`, and `CNAPS5702R` declarations each occur once
  - verify `service=CNAPS5702A` and `service=CNAPS5702R` each occur once
  - verify `DICT_TYPE` still has `count=0`
  - verify the metadata diff has additions only
- Run Maven once after the structural checks: `mvn -f web-fe/pom.xml clean package`.
- Do not run a preliminary `-DskipTests` package followed by another full Maven run.

## Completion Rules

- Finish all eight production files before reporting completion.
- Keep action requests bodyless and return only the six fields defined in the design.
- Preserve `BILL_ID + VERSION_NO` optimistic locking and map zero updated rows to `3004`.
- Use Oracle `SYSTIMESTAMP` for review time.
- Do not commit, push, synchronize, or deploy unless explicitly requested.
- Report the actual modified files and any unresolved compiler issue.
