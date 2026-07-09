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

void CNAPS4609Q(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    char work_date[11] = {0};
    char branch_no[13] = {0};
    char status[33] = {0};
    int total;

    cnaps_log_service_start("CNAPS4609Q");
    get_field(fbfr, CNAPS_F_WORK_DATE, work_date, sizeof(work_date));
    get_field(fbfr, CNAPS_F_BRANCH_NO, branch_no, sizeof(branch_no));
    get_field(fbfr, CNAPS_F_STATUS, status, sizeof(status));
    total = db_query_vouchers(work_date, branch_no, status);
    if (total < 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_ELEMENTS, total);
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_PAGES, 1);
    cnaps_return_response(rqst, 1, "0000", "query success");
}

void CNAPS5702Q(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    char work_date[11] = {0};
    char branch_no[13] = {0};
    int total;

    cnaps_log_service_start("CNAPS5702Q");
    get_field(fbfr, CNAPS_F_WORK_DATE, work_date, sizeof(work_date));
    get_field(fbfr, CNAPS_F_BRANCH_NO, branch_no, sizeof(branch_no));
    total = db_query_vouchers(work_date, branch_no, CNAPS_STATUS_PENDING_REVIEW);
    if (total < 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_ELEMENTS, total);
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_PAGES, 1);
    cnaps_return_response(rqst, 1, "0000", "query success");
}

void CNAPS5702I(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};
    int rc;

    cnaps_log_service_start("CNAPS5702I");
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
    cnaps_put_voucher(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", "detail query success");
}
