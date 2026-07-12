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
    return cnaps_put_string_occurrence(fbfr, field_name, 0, value);
}

int cnaps_get_long(FBFR32 *fbfr, const char *field_name, long *out)
{
    FLDID32 field_id;
    FLDLEN32 len = (FLDLEN32)sizeof(*out);

    if (fbfr == NULL || field_name == NULL || out == NULL) {
        return -1;
    }
    field_id = Fldid32((char *)field_name);
    if (field_id == BADFLDID) {
        userlog("unknown FML32 field: %s", field_name);
        return -1;
    }
    return Fget32(fbfr, field_id, 0, (char *)out, &len) < 0 ? -1 : 0;
}

int cnaps_put_string_occurrence(
    FBFR32 *fbfr,
    const char *field_name,
    FLDOCC32 occurrence,
    const char *value
)
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

    if (Fchg32(fbfr, field_id, occurrence, (char *)safe_value, 0) < 0) {
        userlog("failed to change FML32 field %s: %s", field_name, Fstrerror32(Ferror32));
        return -1;
    }
    return 0;
}

int cnaps_put_long(FBFR32 *fbfr, const char *field_name, long value)
{
    return cnaps_put_long_occurrence(fbfr, field_name, 0, value);
}

int cnaps_put_long_occurrence(FBFR32 *fbfr, const char *field_name, FLDOCC32 occurrence, long value)
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
    if (Fchg32(fbfr, field_id, occurrence, (char *)&value, 0) < 0) {
        userlog("failed to change FML32 long field %s: %s", field_name, Fstrerror32(Ferror32));
        return -1;
    }
    return 0;
}

void cnaps_put_voucher(FBFR32 *fbfr, const void *value)
{
    cnaps_put_voucher_occurrence(fbfr, value, 0);
}

void cnaps_put_voucher_detail_fields(FBFR32 *fbfr, const void *value)
{
    const cnaps_voucher_row *row = (const cnaps_voucher_row *)value;
    if (row == NULL) {
        return;
    }
    cnaps_put_string(fbfr, CNAPS_F_PAYER_ADDRESS, row->payer_address);
    cnaps_put_string(fbfr, CNAPS_F_PAYEE_ADDRESS, row->payee_address);
    cnaps_put_string(fbfr, CNAPS_F_PAYER_BANK_NAME, row->payer_bank_name);
}

void cnaps_put_voucher_occurrence(FBFR32 *fbfr, const void *value, FLDOCC32 occurrence)
{
    const cnaps_voucher_row *row = (const cnaps_voucher_row *)value;
    if (row == NULL) {
        return;
    }
#define PUT_STRING(FIELD, MEMBER) cnaps_put_string_occurrence(fbfr, FIELD, occurrence, row->MEMBER)
    PUT_STRING(CNAPS_F_BILL_ID, bill_id);
    PUT_STRING(CNAPS_F_WORK_DATE, work_date);
    PUT_STRING(CNAPS_F_BRANCH_NO, branch_no);
    PUT_STRING(CNAPS_F_OPERATOR_NO, operator_no);
    PUT_STRING(CNAPS_F_SERIAL_NO, serial_no);
    PUT_STRING(CNAPS_F_BUSINESS_TYPE, business_type);
    PUT_STRING(CNAPS_F_ACCOUNT_PART1, account_part1);
    PUT_STRING(CNAPS_F_ACCOUNT_PART2, account_part2);
    PUT_STRING(CNAPS_F_ACCOUNT_PART3, account_part3);
    PUT_STRING(CNAPS_F_ACCOUNT_NAME, account_name);
    PUT_STRING(CNAPS_F_PAYER_NAME, payer_name);
    PUT_STRING(CNAPS_F_PAYEE_ACCT, payee_account_no);
    PUT_STRING(CNAPS_F_PAYEE_NAME, payee_name);
    PUT_STRING(CNAPS_F_PRIORITY, priority);
    PUT_STRING(CNAPS_F_RECEIVE_BANK_NO, receive_bank_no);
    PUT_STRING(CNAPS_F_RECEIVE_BANK_NAME, receive_bank_name);
    PUT_STRING(CNAPS_F_SYSTEM_TYPE, system_type);
    PUT_STRING(CNAPS_F_AMOUNT, amount);
    PUT_STRING(CNAPS_F_DEBIT_MODE, debit_mode);
    PUT_STRING(CNAPS_F_FEE_AMOUNT, fee_amount);
    PUT_STRING(CNAPS_F_FEE_CHARGE_MODE, fee_charge_mode);
    PUT_STRING(CNAPS_F_SEND_MODE, send_mode);
    PUT_STRING(CNAPS_F_FAX_FLAG, fax_flag);
    PUT_STRING(CNAPS_F_VOUCHER_NO, voucher_no);
    PUT_STRING(CNAPS_F_REMARK, remark);
    PUT_STRING(CNAPS_F_STATUS, status);
    PUT_STRING(CNAPS_F_CHECKER_NO, checker_no);
    PUT_STRING(CNAPS_F_CHECKER_TIME, checker_time);
    PUT_STRING(CNAPS_F_REJECT_REASON, reject_reason);
    PUT_STRING(CNAPS_F_REVIEW_COMMENT, review_comment);
    PUT_STRING(CNAPS_F_DELETE_REASON, delete_reason);
    PUT_STRING(CNAPS_F_DELETE_OPERATOR_NO, delete_operator_no);
    PUT_STRING(CNAPS_F_DELETE_TIME, delete_time);
    PUT_STRING(CNAPS_F_LAST_ACTION, last_action);
    PUT_STRING(CNAPS_F_LAST_OPERATOR_NO, last_operator_no);
    PUT_STRING(CNAPS_F_LAST_REQUEST_ID, last_request_id);
    PUT_STRING(CNAPS_F_LAST_ACTION_TIME, last_action_time);
    PUT_STRING(CNAPS_F_CREATED_AT, created_at);
    PUT_STRING(CNAPS_F_UPDATED_AT, updated_at);
#undef PUT_STRING
    cnaps_put_long_occurrence(fbfr, CNAPS_F_VERSION_NO, occurrence, row->version_no);
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
