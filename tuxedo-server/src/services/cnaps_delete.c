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

void CNAPS5701D(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};
    char delete_reason[201] = {0};
    char operator_no[17] = {0};
    char request_id[33] = {0};
    int rc;

    cnaps_log_service_start("CNAPS5701D");
    get_field(fbfr, CNAPS_F_BILL_ID, bill_id, sizeof(bill_id));
    if (bill_id[0] == '\0') {
        cnaps_return_error(rqst, "2001", "billId is required");
        return;
    }

    rc = db_find_voucher(bill_id, &row);
    if (rc == 1) {
        cnaps_return_error(rqst, "3001", "单据不存在");
        return;
    }
    if (rc != 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    if (strcmp(row.status, CNAPS_STATUS_DRAFT) != 0) {
        cnaps_return_error(rqst, "3003", "当前状态不允许操作");
        return;
    }

    get_field(fbfr, CNAPS_F_DELETE_REASON, delete_reason, sizeof(delete_reason));
    get_field(fbfr, CNAPS_F_OPERATOR_NO, operator_no, sizeof(operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, request_id, sizeof(request_id));
    snprintf(row.delete_reason, sizeof(row.delete_reason), "%s", delete_reason);
    snprintf(row.delete_operator_no, sizeof(row.delete_operator_no), "%s", operator_no);
    snprintf(row.last_operator_no, sizeof(row.last_operator_no), "%s", operator_no);
    snprintf(row.last_request_id, sizeof(row.last_request_id), "%s", request_id);
    snprintf(row.status, sizeof(row.status), "%s", CNAPS_STATUS_DELETED);
    snprintf(row.last_action, sizeof(row.last_action), "%s", "DELETE");

    if (db_begin() != 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    rc = db_update_voucher(&row);
    if (rc == 1) {
        db_rollback();
        cnaps_return_error(rqst, "3001", "单据不存在");
        return;
    }
    if (rc != 0 || db_find_voucher(bill_id, &row) != 0 || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_voucher(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", "delete success");
}
