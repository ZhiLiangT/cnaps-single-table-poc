#include <ctype.h>
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

static int overlay_field(FBFR32 *fbfr, const char *field, char *out, size_t out_size)
{
    char value[513] = {0};
    if (cnaps_get_string(fbfr, field, value, sizeof(value)) != 0) {
        return 0;
    }
    snprintf(out, out_size, "%s", value);
    return 1;
}

static int valid_money(const char *value, int zero_allowed)
{
    int decimal_seen = 0;
    int digits_seen = 0;
    int fractional_digits = 0;
    int nonzero_seen = 0;

    if (value == NULL || value[0] == '\0') {
        return 0;
    }
    for (const unsigned char *cursor = (const unsigned char *)value; *cursor != '\0'; ++cursor) {
        if (isdigit(*cursor)) {
            digits_seen = 1;
            nonzero_seen = nonzero_seen || *cursor != '0';
            if (decimal_seen && ++fractional_digits > 2) {
                return 0;
            }
        } else if (*cursor == '.' && !decimal_seen && digits_seen) {
            decimal_seen = 1;
        } else {
            return 0;
        }
    }
    if (!digits_seen || (decimal_seen && fractional_digits == 0)) {
        return 0;
    }
    return zero_allowed || nonzero_seen;
}

void CNAPS5701U(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};
    char operator_no[17] = {0};
    char request_id[33] = {0};
    int amount_supplied;
    int fee_amount_supplied;
    int rc;

    cnaps_log_service_start("CNAPS5701U");
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
    if (strcmp(row.status, CNAPS_STATUS_PENDING_REVIEW) != 0
        && strcmp(row.status, CNAPS_STATUS_REJECTED) != 0) {
        cnaps_return_error(rqst, "3003", "当前状态不允许操作");
        return;
    }

    overlay_field(fbfr, CNAPS_F_WORK_DATE, row.work_date, sizeof(row.work_date));
    overlay_field(fbfr, CNAPS_F_BUSINESS_TYPE, row.business_type, sizeof(row.business_type));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_PART1, row.account_part1, sizeof(row.account_part1));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_PART2, row.account_part2, sizeof(row.account_part2));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_PART3, row.account_part3, sizeof(row.account_part3));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_NAME, row.account_name, sizeof(row.account_name));
    overlay_field(fbfr, CNAPS_F_PAYER_NAME, row.payer_name, sizeof(row.payer_name));
    overlay_field(fbfr, CNAPS_F_PAYEE_ACCT, row.payee_account_no, sizeof(row.payee_account_no));
    overlay_field(fbfr, CNAPS_F_PAYEE_NAME, row.payee_name, sizeof(row.payee_name));
    overlay_field(fbfr, CNAPS_F_PRIORITY, row.priority, sizeof(row.priority));
    overlay_field(fbfr, CNAPS_F_RECEIVE_BANK_NO, row.receive_bank_no, sizeof(row.receive_bank_no));
    overlay_field(fbfr, CNAPS_F_RECEIVE_BANK_NAME, row.receive_bank_name, sizeof(row.receive_bank_name));
    overlay_field(fbfr, CNAPS_F_SYSTEM_TYPE, row.system_type, sizeof(row.system_type));
    amount_supplied = overlay_field(fbfr, CNAPS_F_AMOUNT, row.amount, sizeof(row.amount));
    overlay_field(fbfr, CNAPS_F_DEBIT_MODE, row.debit_mode, sizeof(row.debit_mode));
    fee_amount_supplied = overlay_field(fbfr, CNAPS_F_FEE_AMOUNT, row.fee_amount, sizeof(row.fee_amount));
    overlay_field(fbfr, CNAPS_F_FEE_CHARGE_MODE, row.fee_charge_mode, sizeof(row.fee_charge_mode));
    overlay_field(fbfr, CNAPS_F_SEND_MODE, row.send_mode, sizeof(row.send_mode));
    overlay_field(fbfr, CNAPS_F_FAX_FLAG, row.fax_flag, sizeof(row.fax_flag));
    overlay_field(fbfr, CNAPS_F_VOUCHER_NO, row.voucher_no, sizeof(row.voucher_no));
    overlay_field(fbfr, CNAPS_F_REMARK, row.remark, sizeof(row.remark));

    if ((amount_supplied && !valid_money(row.amount, 0))
        || (fee_amount_supplied && !valid_money(row.fee_amount, 1))) {
        cnaps_return_error(rqst, "2002", "invalid money");
        return;
    }

    get_field(fbfr, CNAPS_F_OPERATOR_NO, operator_no, sizeof(operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, request_id, sizeof(request_id));
    snprintf(row.status, sizeof(row.status), "%s", CNAPS_STATUS_PENDING_REVIEW);
    snprintf(row.last_action, sizeof(row.last_action), "%s", "UPDATE");
    snprintf(row.last_operator_no, sizeof(row.last_operator_no), "%s", operator_no);
    snprintf(row.last_request_id, sizeof(row.last_request_id), "%s", request_id);
    row.checker_no[0] = '\0';
    row.checker_time[0] = '\0';
    row.review_comment[0] = '\0';
    row.reject_reason[0] = '\0';

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
    cnaps_return_response(rqst, 1, "0000", "update success");
}
