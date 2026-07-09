#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"
#include "cnaps_error.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"

int cnaps_get_string(FBFR32 *fbfr, const char *field_name, char *out, size_t out_size)
{
    FLDID32 field_id;
    FLDLEN32 len;

    if (fbfr == NULL || field_name == NULL || out == NULL || out_size == 0) {
        return -1;
    }

    out[0] = '\0';
    field_id = Fldid32((char *)field_name);
    if (field_id == BADFLDID) {
        userlog("unknown FML32 field: %s", field_name);
        return -1;
    }

    len = (FLDLEN32)out_size;
    if (Fget32(fbfr, field_id, 0, out, &len) < 0) {
        return -1;
    }
    out[out_size - 1] = '\0';
    return 0;
}

int cnaps_put_string(FBFR32 *fbfr, const char *field_name, const char *value)
{
    FLDID32 field_id;
    const char *safe_value = value == NULL ? "" : value;

    if (fbfr == NULL || field_name == NULL) {
        return -1;
    }

    field_id = Fldid32((char *)field_name);
    if (field_id == BADFLDID) {
        userlog("unknown FML32 field: %s", field_name);
        return -1;
    }

    if (Fchg32(fbfr, field_id, 0, (char *)safe_value, 0) < 0) {
        userlog("failed to change FML32 field %s: %s", field_name, Fstrerror32(Ferror32));
        return -1;
    }
    return 0;
}

int cnaps_put_long(FBFR32 *fbfr, const char *field_name, long value)
{
    FLDID32 field_id;
    if (fbfr == NULL || field_name == NULL) {
        return -1;
    }
    field_id = Fldid32((char *)field_name);
    if (field_id == BADFLDID) {
        userlog("unknown FML32 field: %s", field_name);
        return -1;
    }
    if (Fchg32(fbfr, field_id, 0, (char *)&value, 0) < 0) {
        userlog("failed to change FML32 long field %s: %s", field_name, Fstrerror32(Ferror32));
        return -1;
    }
    return 0;
}

void cnaps_put_voucher(FBFR32 *fbfr, const void *value)
{
    const cnaps_voucher_row *row = (const cnaps_voucher_row *)value;
    if (row == NULL) {
        return;
    }
    cnaps_put_string(fbfr, CNAPS_F_BILL_ID, row->bill_id);
    cnaps_put_string(fbfr, CNAPS_F_WORK_DATE, row->work_date);
    cnaps_put_string(fbfr, CNAPS_F_BRANCH_NO, row->branch_no);
    cnaps_put_string(fbfr, CNAPS_F_OPERATOR_NO, row->operator_no);
    cnaps_put_string(fbfr, CNAPS_F_SERIAL_NO, row->serial_no);
    cnaps_put_string(fbfr, CNAPS_F_BUSINESS_TYPE, row->business_type);
    cnaps_put_string(fbfr, CNAPS_F_PAYEE_ACCT, row->payee_account_no);
    cnaps_put_string(fbfr, CNAPS_F_PAYEE_NAME, row->payee_name);
    cnaps_put_string(fbfr, CNAPS_F_AMOUNT, row->amount);
    cnaps_put_string(fbfr, CNAPS_F_STATUS, row->status);
    cnaps_put_string(fbfr, CNAPS_F_REJECT_REASON, row->reject_reason);
    cnaps_put_string(fbfr, CNAPS_F_REVIEW_COMMENT, row->review_comment);
    cnaps_put_string(fbfr, CNAPS_F_LAST_ACTION, row->last_action);
}

void cnaps_return_response(TPSVCINFO *rqst, int success, const char *resp_code, const char *resp_msg)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_put_string(fbfr, CNAPS_F_RESP_CODE, resp_code);
    cnaps_put_string(fbfr, CNAPS_F_RESP_MSG, resp_msg);
    tpreturn(success ? TPSUCCESS : TPFAIL, 0, rqst->data, 0L, 0);
}

void cnaps_return_ok(TPSVCINFO *rqst, const char *service_name)
{
    userlog("service %s completed", service_name);
    cnaps_return_response(rqst, 1, CNAPS_RESP_SUCCESS, "success");
}

void cnaps_return_error(TPSVCINFO *rqst, const char *resp_code, const char *resp_msg)
{
    userlog("service failed: %s %s", resp_code, resp_msg);
    cnaps_return_response(rqst, 0, resp_code, resp_msg);
}
