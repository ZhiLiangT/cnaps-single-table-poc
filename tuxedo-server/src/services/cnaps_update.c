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

static int is_blank(const char *value)
{
    if (value == NULL) {
        return 1;
    }
    while (*value != '\0') {
        if (!isspace((unsigned char)*value)) {
            return 0;
        }
        ++value;
    }
    return 1;
}

static int overlay_optional_dictionary_field(FBFR32 *fbfr, const char *field, char *out, size_t out_size)
{
    char value[513] = {0};
    if (cnaps_get_string(fbfr, field, value, sizeof(value)) != 0 || is_blank(value)) {
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

static int invalid_dictionary_fields(
    const cnaps_voucher_row *row,
    int business_type_supplied,
    int priority_supplied,
    int system_type_supplied,
    int debit_mode_supplied,
    int fee_charge_mode_supplied,
    int send_mode_supplied,
    int fax_flag_supplied
)
{
    return (business_type_supplied && strcmp(row->business_type, "02102") != 0)
        || (priority_supplied && strcmp(row->priority, "NORM") != 0)
        || (system_type_supplied && strcmp(row->system_type, "CNAPS") != 0)
        || (debit_mode_supplied && strcmp(row->debit_mode, "1") != 0)
        || (fee_charge_mode_supplied && strcmp(row->fee_charge_mode, "1") != 0)
        || (send_mode_supplied && strcmp(row->send_mode, "0") != 0)
        || (fax_flag_supplied
            && strcmp(row->fax_flag, "0") != 0
            && strcmp(row->fax_flag, "1") != 0);
}

void CNAPS5701U(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};
    char operator_no[17] = {0};
    char request_id[33] = {0};
    char raw_work_date[513] = {0};
    int work_date_supplied;
    int amount_supplied;
    int fee_amount_supplied;
    int business_type_supplied;
    int priority_supplied;
    int system_type_supplied;
    int debit_mode_supplied;
    int fee_charge_mode_supplied;
    int send_mode_supplied;
    int fax_flag_supplied;
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
    if (strcmp(row.status, CNAPS_STATUS_DRAFT) != 0) {
        cnaps_return_error(rqst, "3003", "当前状态不允许操作");
        return;
    }

    work_date_supplied = cnaps_get_string(fbfr, CNAPS_F_WORK_DATE, raw_work_date, sizeof(raw_work_date)) == 0;
    business_type_supplied = overlay_field(fbfr, CNAPS_F_BUSINESS_TYPE, row.business_type, sizeof(row.business_type));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_PART1, row.account_part1, sizeof(row.account_part1));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_PART2, row.account_part2, sizeof(row.account_part2));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_PART3, row.account_part3, sizeof(row.account_part3));
    overlay_field(fbfr, CNAPS_F_ACCOUNT_NAME, row.account_name, sizeof(row.account_name));
    overlay_field(fbfr, CNAPS_F_PAYER_NAME, row.payer_name, sizeof(row.payer_name));
    overlay_field(fbfr, CNAPS_F_PAYER_ADDRESS, row.payer_address, sizeof(row.payer_address));
    overlay_field(fbfr, CNAPS_F_PAYER_BANK_NAME, row.payer_bank_name, sizeof(row.payer_bank_name));
    overlay_field(fbfr, CNAPS_F_PAYEE_ACCT, row.payee_account_no, sizeof(row.payee_account_no));
    overlay_field(fbfr, CNAPS_F_PAYEE_NAME, row.payee_name, sizeof(row.payee_name));
    overlay_field(fbfr, CNAPS_F_PAYEE_ADDRESS, row.payee_address, sizeof(row.payee_address));
    priority_supplied = overlay_field(fbfr, CNAPS_F_PRIORITY, row.priority, sizeof(row.priority));
    overlay_field(fbfr, CNAPS_F_RECEIVE_BANK_NO, row.receive_bank_no, sizeof(row.receive_bank_no));
    overlay_field(fbfr, CNAPS_F_RECEIVE_BANK_NAME, row.receive_bank_name, sizeof(row.receive_bank_name));
    system_type_supplied = overlay_field(fbfr, CNAPS_F_SYSTEM_TYPE, row.system_type, sizeof(row.system_type));
    amount_supplied = overlay_field(fbfr, CNAPS_F_AMOUNT, row.amount, sizeof(row.amount));
    debit_mode_supplied = overlay_optional_dictionary_field(fbfr, CNAPS_F_DEBIT_MODE, row.debit_mode, sizeof(row.debit_mode));
    fee_amount_supplied = overlay_field(fbfr, CNAPS_F_FEE_AMOUNT, row.fee_amount, sizeof(row.fee_amount));
    fee_charge_mode_supplied = overlay_optional_dictionary_field(fbfr, CNAPS_F_FEE_CHARGE_MODE, row.fee_charge_mode, sizeof(row.fee_charge_mode));
    send_mode_supplied = overlay_optional_dictionary_field(fbfr, CNAPS_F_SEND_MODE, row.send_mode, sizeof(row.send_mode));
    fax_flag_supplied = overlay_optional_dictionary_field(fbfr, CNAPS_F_FAX_FLAG, row.fax_flag, sizeof(row.fax_flag));
    overlay_field(fbfr, CNAPS_F_VOUCHER_NO, row.voucher_no, sizeof(row.voucher_no));
    overlay_field(fbfr, CNAPS_F_REMARK, row.remark, sizeof(row.remark));

    if (work_date_supplied && !cnaps_valid_work_date(raw_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid work date");
        return;
    }
    if (work_date_supplied) {
        snprintf(row.work_date, sizeof(row.work_date), "%s", raw_work_date);
    }
    if ((amount_supplied && !valid_money(row.amount, 0))
        || (fee_amount_supplied && !valid_money(row.fee_amount, 1))) {
        cnaps_return_error(rqst, "2002", "invalid money");
        return;
    }
    if (!cnaps_valid_optional_text(row.payer_address, 256)
        || !cnaps_valid_optional_text(row.payee_address, 256)
        || !cnaps_valid_optional_text(row.payer_bank_name, 128)) {
        cnaps_return_error(rqst, "2002", "field too long");
        return;
    }
    if (invalid_dictionary_fields(
        &row,
        business_type_supplied,
        priority_supplied,
        system_type_supplied,
        debit_mode_supplied,
        fee_charge_mode_supplied,
        send_mode_supplied,
        fax_flag_supplied
    )) {
        cnaps_return_error(rqst, "2003", "invalid dictionary value");
        return;
    }

    get_field(fbfr, CNAPS_F_OPERATOR_NO, operator_no, sizeof(operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, request_id, sizeof(request_id));
    snprintf(row.status, sizeof(row.status), "%s", CNAPS_STATUS_DRAFT);
    snprintf(row.last_action, sizeof(row.last_action), "%s", "UPDATE");
    snprintf(row.last_operator_no, sizeof(row.last_operator_no), "%s", operator_no);
    snprintf(row.last_request_id, sizeof(row.last_request_id), "%s", request_id);
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
