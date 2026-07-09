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

static void fill_review_row(FBFR32 *fbfr, cnaps_voucher_row *row, const char *status, const char *action)
{
    memset(row, 0, sizeof(*row));
    get_field(fbfr, CNAPS_F_BILL_ID, row->bill_id, sizeof(row->bill_id));
    get_field(fbfr, CNAPS_F_OPERATOR_NO, row->checker_no, sizeof(row->checker_no));
    get_field(fbfr, CNAPS_F_OPERATOR_NO, row->last_operator_no, sizeof(row->last_operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, row->last_request_id, sizeof(row->last_request_id));
    get_field(fbfr, CNAPS_F_REVIEW_COMMENT, row->review_comment, sizeof(row->review_comment));
    get_field(fbfr, CNAPS_F_REJECT_REASON, row->reject_reason, sizeof(row->reject_reason));
    snprintf(row->status, sizeof(row->status), "%s", status);
    snprintf(row->last_action, sizeof(row->last_action), "%s", action);
}

void CNAPS5702A(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};

    cnaps_log_service_start("CNAPS5702A");
    fill_review_row(fbfr, &row, CNAPS_STATUS_APPROVED, "REVIEW_PASS");
    if (row.bill_id[0] == '\0') {
        cnaps_return_error(rqst, "2001", "billId is required");
        return;
    }
    if (db_begin() != 0 || db_update_voucher(&row) != 0 || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_voucher(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", "review pass success");
}

void CNAPS5702R(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};

    cnaps_log_service_start("CNAPS5702R");
    fill_review_row(fbfr, &row, CNAPS_STATUS_REJECTED, "REVIEW_RETURN");
    if (row.bill_id[0] == '\0' || row.reject_reason[0] == '\0') {
        cnaps_return_error(rqst, "2001", "billId and rejectReason are required");
        return;
    }
    if (db_begin() != 0 || db_update_voucher(&row) != 0 || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_voucher(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", "review return success");
}
