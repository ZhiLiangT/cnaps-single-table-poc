#include <ctype.h>
#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"
#include "cnaps_status.h"

static void copy_text(FBFR32 *fbfr, const char *field_name, char *out, size_t out_size, const char *default_value)
{
    if (cnaps_get_string(fbfr, field_name, out, out_size) != 0 || out[0] == '\0') {
        if (default_value != NULL) {
            snprintf(out, out_size, "%s", default_value);
        }
    }
}

static void compact_date(const char *work_date, char *out, size_t out_size)
{
    size_t j = 0;
    for (size_t i = 0; work_date[i] != '\0' && j + 1 < out_size; i++) {
        if (work_date[i] != '-') {
            out[j++] = work_date[i];
        }
    }
    out[j] = '\0';
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

static int required_field_missing(const cnaps_voucher_row *row)
{
    return is_blank(row->work_date)
        || is_blank(row->business_type)
        || is_blank(row->account_part1)
        || is_blank(row->account_part2)
        || is_blank(row->account_part3)
        || is_blank(row->payee_account_no)
        || is_blank(row->payee_name)
        || is_blank(row->priority)
        || is_blank(row->system_type)
        || is_blank(row->amount);
}

static int invalid_dictionary_fields(const cnaps_voucher_row *row)
{
    return strcmp(row->business_type, "02102") != 0
        || strcmp(row->priority, "NORM") != 0
        || strcmp(row->system_type, "CNAPS") != 0
        || (row->debit_mode[0] != '\0' && strcmp(row->debit_mode, "1") != 0)
        || (row->fee_charge_mode[0] != '\0' && strcmp(row->fee_charge_mode, "1") != 0)
        || (row->send_mode[0] != '\0' && strcmp(row->send_mode, "0") != 0)
        || (row->fax_flag[0] != '\0'
            && strcmp(row->fax_flag, "0") != 0
            && strcmp(row->fax_flag, "1") != 0);
}

static void apply_optional_defaults(cnaps_voucher_row *row)
{
    if (row->debit_mode[0] == '\0') snprintf(row->debit_mode, sizeof(row->debit_mode), "%s", "1");
    if (row->fee_charge_mode[0] == '\0') snprintf(row->fee_charge_mode, sizeof(row->fee_charge_mode), "%s", "1");
    if (row->send_mode[0] == '\0') snprintf(row->send_mode, sizeof(row->send_mode), "%s", "0");
    if (row->fax_flag[0] == '\0') snprintf(row->fax_flag, sizeof(row->fax_flag), "%s", "0");
}

static void row_from_create_request(FBFR32 *fbfr, cnaps_voucher_row *row)
{
    memset(row, 0, sizeof(*row));
    copy_text(fbfr, CNAPS_F_WORK_DATE, row->work_date, sizeof(row->work_date), "");
    copy_text(fbfr, CNAPS_F_BRANCH_NO, row->branch_no, sizeof(row->branch_no), "772");
    copy_text(fbfr, CNAPS_F_OPERATOR_NO, row->operator_no, sizeof(row->operator_no), "");
    copy_text(fbfr, CNAPS_F_BUSINESS_TYPE, row->business_type, sizeof(row->business_type), "");
    copy_text(fbfr, CNAPS_F_ACCOUNT_PART1, row->account_part1, sizeof(row->account_part1), "");
    copy_text(fbfr, CNAPS_F_ACCOUNT_PART2, row->account_part2, sizeof(row->account_part2), "");
    copy_text(fbfr, CNAPS_F_ACCOUNT_PART3, row->account_part3, sizeof(row->account_part3), "");
    copy_text(fbfr, CNAPS_F_ACCOUNT_NAME, row->account_name, sizeof(row->account_name), "");
    copy_text(fbfr, CNAPS_F_PAYER_NAME, row->payer_name, sizeof(row->payer_name), "");
    copy_text(fbfr, CNAPS_F_PAYEE_ACCT, row->payee_account_no, sizeof(row->payee_account_no), "");
    copy_text(fbfr, CNAPS_F_PAYEE_NAME, row->payee_name, sizeof(row->payee_name), "");
    copy_text(fbfr, CNAPS_F_PRIORITY, row->priority, sizeof(row->priority), "");
    copy_text(fbfr, CNAPS_F_RECEIVE_BANK_NO, row->receive_bank_no, sizeof(row->receive_bank_no), "");
    copy_text(fbfr, CNAPS_F_RECEIVE_BANK_NAME, row->receive_bank_name, sizeof(row->receive_bank_name), "");
    copy_text(fbfr, CNAPS_F_SYSTEM_TYPE, row->system_type, sizeof(row->system_type), "");
    copy_text(fbfr, CNAPS_F_AMOUNT, row->amount, sizeof(row->amount), "");
    copy_text(fbfr, CNAPS_F_DEBIT_MODE, row->debit_mode, sizeof(row->debit_mode), "");
    copy_text(fbfr, CNAPS_F_FEE_AMOUNT, row->fee_amount, sizeof(row->fee_amount), "0");
    copy_text(fbfr, CNAPS_F_FEE_CHARGE_MODE, row->fee_charge_mode, sizeof(row->fee_charge_mode), "");
    copy_text(fbfr, CNAPS_F_SEND_MODE, row->send_mode, sizeof(row->send_mode), "");
    copy_text(fbfr, CNAPS_F_FAX_FLAG, row->fax_flag, sizeof(row->fax_flag), "");
    copy_text(fbfr, CNAPS_F_VOUCHER_NO, row->voucher_no, sizeof(row->voucher_no), "");
    copy_text(fbfr, CNAPS_F_REMARK, row->remark, sizeof(row->remark), "");
    copy_text(fbfr, CNAPS_F_REQ_ID, row->last_request_id, sizeof(row->last_request_id), "");
    snprintf(row->status, sizeof(row->status), "%s", CNAPS_STATUS_PENDING_REVIEW);
    snprintf(row->last_action, sizeof(row->last_action), "%s", "CREATE");
    snprintf(row->last_operator_no, sizeof(row->last_operator_no), "%s", row->operator_no);
    row->version_no = 1;
}

static void generate_identifiers(cnaps_voucher_row *row)
{
    char date_part[9] = {0};

    if (db_next_serial_no(row->work_date, row->branch_no, row->serial_no, sizeof(row->serial_no)) != 0) {
        snprintf(row->serial_no, sizeof(row->serial_no), "%s", "0002000");
    }
    compact_date(row->work_date, date_part, sizeof(date_part));
    snprintf(row->bill_id, sizeof(row->bill_id), "B%s%s%s", date_part, row->branch_no, row->serial_no);
}

void CNAPS5701E(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};

    cnaps_log_service_start("CNAPS5701E");
    row_from_create_request(fbfr, &row);
    if (row.work_date[0] == '\0' || required_field_missing(&row)) {
        cnaps_return_error(rqst, "2001", "required field missing");
        return;
    }
    if (!cnaps_valid_work_date(row.work_date)) {
        cnaps_return_error(rqst, "2002", "invalid work date");
        return;
    }
    if (!valid_money(row.amount, 0) || !valid_money(row.fee_amount, 1)) {
        cnaps_return_error(rqst, "2002", "invalid money");
        return;
    }
    if (invalid_dictionary_fields(&row)) {
        cnaps_return_error(rqst, "2003", "invalid dictionary value");
        return;
    }
    apply_optional_defaults(&row);
    generate_identifiers(&row);
    snprintf(bill_id, sizeof(bill_id), "%s", row.bill_id);
    if (db_begin() != 0
        || db_insert_voucher(&row) != 0
        || db_find_voucher(bill_id, &row) != 0
        || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_voucher(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", "create success");
}
