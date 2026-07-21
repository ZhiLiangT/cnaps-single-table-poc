#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"
#include "cnaps_status.h"

static void get_field(FBFR32 *fbfr, const char *field, char *out, size_t out_size)
{
    if (cnaps_get_string(fbfr, field, out, out_size) != 0) {
        out[0] = '\0';
    }
}

static void put_review_summary(FBFR32 *fbfr, const cnaps_voucher_row *row)
{
    cnaps_put_string(fbfr, CNAPS_F_BILL_ID, row->bill_id);
    cnaps_put_string(fbfr, CNAPS_F_STATUS, row->status);
    cnaps_put_string(fbfr, CNAPS_F_CHECKER_NO, row->checker_no);
    cnaps_put_string(fbfr, CNAPS_F_CHECKER_TIME, row->checker_time);
    cnaps_put_string(fbfr, CNAPS_F_LAST_ACTION, row->last_action);
    cnaps_put_long(fbfr, CNAPS_F_VERSION_NO, row->version_no);
}

static void review_voucher(
    TPSVCINFO *rqst,
    const char *service_name,
    const char *target_status,
    const char *last_action,
    const char *success_message
)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};
    char operator_no[17] = {0};
    char request_id[33] = {0};
    int rc;

    cnaps_log_service_start(service_name);
    get_field(fbfr, CNAPS_F_BILL_ID, bill_id, sizeof(bill_id));
    if (bill_id[0] == '\0') {
        cnaps_return_error(rqst, "2001", "billId is required");
        return;
    }

    rc = db_find_voucher(bill_id, &row);
    if (rc == 1) {
        cnaps_return_error(rqst, "3001", "voucher not found");
        return;
    }
    if (rc != 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    if (!cnaps_status_can_review(row.status)) {
        cnaps_return_error(rqst, "3004", "current status cannot be reviewed");
        return;
    }

    get_field(fbfr, CNAPS_F_OPERATOR_NO, operator_no, sizeof(operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, request_id, sizeof(request_id));
    snprintf(row.status, sizeof(row.status), "%s", target_status);
    snprintf(row.checker_no, sizeof(row.checker_no), "%s", operator_no);
    snprintf(row.last_action, sizeof(row.last_action), "%s", last_action);
    snprintf(row.last_operator_no, sizeof(row.last_operator_no), "%s", operator_no);
    snprintf(row.last_request_id, sizeof(row.last_request_id), "%s", request_id);

    if (db_begin() != 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    rc = db_review_voucher(&row);
    if (rc == 1) {
        db_rollback();
        cnaps_return_error(rqst, "3004", "current status or version changed");
        return;
    }
    if (rc != 0 || db_find_voucher(bill_id, &row) != 0 || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    put_review_summary(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", success_message);
}

void CNAPS5702A(TPSVCINFO *rqst)
{
    review_voucher(
        rqst,
        "CNAPS5702A",
        CNAPS_STATUS_APPROVED,
        "REVIEW_PASS",
        "review pass success"
    );
}

void CNAPS5702R(TPSVCINFO *rqst)
{
    review_voucher(
        rqst,
        "CNAPS5702R",
        CNAPS_STATUS_REJECTED,
        "REVIEW_RETURN",
        "review return success"
    );
}
