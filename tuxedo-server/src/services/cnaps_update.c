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

void CNAPS5701U(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};

    cnaps_log_service_start("CNAPS5701U");
    get_field(fbfr, CNAPS_F_BILL_ID, row.bill_id, sizeof(row.bill_id));
    get_field(fbfr, CNAPS_F_WORK_DATE, row.work_date, sizeof(row.work_date));
    get_field(fbfr, CNAPS_F_PAYEE_ACCT, row.payee_account_no, sizeof(row.payee_account_no));
    get_field(fbfr, CNAPS_F_PAYEE_NAME, row.payee_name, sizeof(row.payee_name));
    get_field(fbfr, CNAPS_F_AMOUNT, row.amount, sizeof(row.amount));
    get_field(fbfr, CNAPS_F_REMARK, row.remark, sizeof(row.remark));
    get_field(fbfr, CNAPS_F_OPERATOR_NO, row.last_operator_no, sizeof(row.last_operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, row.last_request_id, sizeof(row.last_request_id));
    snprintf(row.status, sizeof(row.status), "%s", CNAPS_STATUS_PENDING_REVIEW);
    snprintf(row.last_action, sizeof(row.last_action), "%s", "UPDATE");
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
    cnaps_return_response(rqst, 1, "0000", "update success");
}
