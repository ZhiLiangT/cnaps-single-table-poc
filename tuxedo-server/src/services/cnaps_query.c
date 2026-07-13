#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"
#include "cnaps_status.h"

#define CNAPS_QUERY_MAX_ROWS 100
#define CNAPS_QUERY_RESPONSE_BUFFER_SIZE (1024L * 1024L)

static void get_field(FBFR32 *fbfr, const char *field, char *out, size_t out_size)
{
    if (cnaps_get_string(fbfr, field, out, out_size) != 0) {
        out[0] = '\0';
    }
}

void CNAPS4609Q(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row rows[CNAPS_QUERY_MAX_ROWS];
    char work_date[11] = {0};
    char raw_work_date[513] = {0};
    char start_work_date[11] = {0};
    char raw_start_work_date[513] = {0};
    char end_work_date[11] = {0};
    char raw_end_work_date[513] = {0};
    char branch_no[13] = {0};
    char status[33] = {0};
    char serial_no[17] = {0};
    char voucher_no[65] = {0};
    char payee_name[129] = {0};
    char payee_account_no[65] = {0};
    char include_deleted_text[8] = {0};
    long page_no_value = 1;
    long page_size_value = 10;
    int include_deleted;
    int page_no = 1;
    int page_size = 10;
    int row_count;
    int total;

    cnaps_log_service_start("CNAPS4609Q");
    get_field(fbfr, CNAPS_F_WORK_DATE, raw_work_date, sizeof(raw_work_date));
    get_field(fbfr, CNAPS_F_START_WORK_DATE, raw_start_work_date, sizeof(raw_start_work_date));
    get_field(fbfr, CNAPS_F_END_WORK_DATE, raw_end_work_date, sizeof(raw_end_work_date));
    get_field(fbfr, CNAPS_F_BRANCH_NO, branch_no, sizeof(branch_no));
    get_field(fbfr, CNAPS_F_STATUS, status, sizeof(status));
    get_field(fbfr, CNAPS_F_SERIAL_NO, serial_no, sizeof(serial_no));
    get_field(fbfr, CNAPS_F_VOUCHER_NO, voucher_no, sizeof(voucher_no));
    get_field(fbfr, CNAPS_F_PAYEE_NAME, payee_name, sizeof(payee_name));
    get_field(fbfr, CNAPS_F_PAYEE_ACCT, payee_account_no, sizeof(payee_account_no));
    get_field(fbfr, CNAPS_F_INCLUDE_DELETED, include_deleted_text, sizeof(include_deleted_text));
    if (raw_work_date[0] != '\0' && !cnaps_valid_work_date(raw_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid work date");
        return;
    }
    if (raw_start_work_date[0] != '\0' && !cnaps_valid_work_date(raw_start_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid start work date");
        return;
    }
    if (raw_end_work_date[0] != '\0' && !cnaps_valid_work_date(raw_end_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid end work date");
        return;
    }
    if (raw_work_date[0] != '\0'
        && (raw_start_work_date[0] != '\0' || raw_end_work_date[0] != '\0')) {
        cnaps_return_error(rqst, "2002", "work date cannot be combined with range");
        return;
    }
    if (raw_start_work_date[0] != '\0' && raw_end_work_date[0] != '\0'
        && strcmp(raw_start_work_date, raw_end_work_date) > 0) {
        cnaps_return_error(rqst, "2002", "start work date is after end work date");
        return;
    }
    snprintf(work_date, sizeof(work_date), "%s", raw_work_date);
    snprintf(start_work_date, sizeof(start_work_date), "%s", raw_start_work_date);
    snprintf(end_work_date, sizeof(end_work_date), "%s", raw_end_work_date);
    cnaps_get_long(fbfr, CNAPS_F_PAGE_NO, &page_no_value);
    cnaps_get_long(fbfr, CNAPS_F_PAGE_SIZE, &page_size_value);
    page_no = page_no_value > 0 && page_no_value <= 2147483647L ? (int)page_no_value : 1;
    page_size = page_size_value > 0 && page_size_value <= CNAPS_QUERY_MAX_ROWS
        ? (int)page_size_value
        : (page_size_value > CNAPS_QUERY_MAX_ROWS ? CNAPS_QUERY_MAX_ROWS : 10);
    include_deleted = include_deleted_text[0] == '1'
        || include_deleted_text[0] == 'T'
        || include_deleted_text[0] == 't';
    row_count = db_query_vouchers(
        work_date,
        start_work_date,
        end_work_date,
        branch_no,
        status,
        serial_no,
        voucher_no,
        payee_name,
        payee_account_no,
        include_deleted,
        page_no,
        page_size,
        rows,
        CNAPS_QUERY_MAX_ROWS,
        &total
    );
    if (row_count < 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    fbfr = cnaps_reserve_response_buffer(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE);
    if (fbfr == NULL) {
        cnaps_return_error(rqst, "4002", "response buffer allocation failed");
        return;
    }
    cnaps_put_long(fbfr, CNAPS_F_PAGE_NO, page_no);
    cnaps_put_long(fbfr, CNAPS_F_PAGE_SIZE, page_size);
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_ELEMENTS, total);
    for (int i = 0; i < row_count; ++i) {
        cnaps_put_voucher_occurrence(fbfr, &rows[i], (FLDOCC32)i);
    }
    cnaps_return_response(rqst, 1, "0000", "query success");
}

void CNAPS5702Q(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row rows[CNAPS_QUERY_MAX_ROWS];
    char work_date[11] = {0};
    char raw_work_date[513] = {0};
    char start_work_date[11] = {0};
    char raw_start_work_date[513] = {0};
    char end_work_date[11] = {0};
    char raw_end_work_date[513] = {0};
    char branch_no[13] = {0};
    char serial_no[17] = {0};
    long page_no_value = 1;
    long page_size_value = 10;
    int page_no = 1;
    int page_size = 10;
    int row_count;
    int total;

    cnaps_log_service_start("CNAPS5702Q");
    get_field(fbfr, CNAPS_F_WORK_DATE, raw_work_date, sizeof(raw_work_date));
    get_field(fbfr, CNAPS_F_START_WORK_DATE, raw_start_work_date, sizeof(raw_start_work_date));
    get_field(fbfr, CNAPS_F_END_WORK_DATE, raw_end_work_date, sizeof(raw_end_work_date));
    get_field(fbfr, CNAPS_F_BRANCH_NO, branch_no, sizeof(branch_no));
    get_field(fbfr, CNAPS_F_SERIAL_NO, serial_no, sizeof(serial_no));
    if (raw_work_date[0] != '\0' && !cnaps_valid_work_date(raw_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid work date");
        return;
    }
    if (raw_start_work_date[0] != '\0' && !cnaps_valid_work_date(raw_start_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid start work date");
        return;
    }
    if (raw_end_work_date[0] != '\0' && !cnaps_valid_work_date(raw_end_work_date)) {
        cnaps_return_error(rqst, "2002", "invalid end work date");
        return;
    }
    if (raw_work_date[0] != '\0'
        && (raw_start_work_date[0] != '\0' || raw_end_work_date[0] != '\0')) {
        cnaps_return_error(rqst, "2002", "work date cannot be combined with range");
        return;
    }
    if (raw_start_work_date[0] != '\0' && raw_end_work_date[0] != '\0'
        && strcmp(raw_start_work_date, raw_end_work_date) > 0) {
        cnaps_return_error(rqst, "2002", "start work date is after end work date");
        return;
    }
    snprintf(work_date, sizeof(work_date), "%s", raw_work_date);
    snprintf(start_work_date, sizeof(start_work_date), "%s", raw_start_work_date);
    snprintf(end_work_date, sizeof(end_work_date), "%s", raw_end_work_date);
    cnaps_get_long(fbfr, CNAPS_F_PAGE_NO, &page_no_value);
    cnaps_get_long(fbfr, CNAPS_F_PAGE_SIZE, &page_size_value);
    page_no = page_no_value > 0 && page_no_value <= 2147483647L ? (int)page_no_value : 1;
    page_size = page_size_value > 0 && page_size_value <= CNAPS_QUERY_MAX_ROWS
        ? (int)page_size_value
        : (page_size_value > CNAPS_QUERY_MAX_ROWS ? CNAPS_QUERY_MAX_ROWS : 10);
    row_count = db_query_vouchers(
        work_date,
        start_work_date,
        end_work_date,
        branch_no,
        CNAPS_STATUS_PENDING_REVIEW,
        serial_no,
        "",
        "",
        "",
        0,
        page_no,
        page_size,
        rows,
        CNAPS_QUERY_MAX_ROWS,
        &total
    );
    if (row_count < 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    fbfr = cnaps_reserve_response_buffer(rqst, CNAPS_QUERY_RESPONSE_BUFFER_SIZE);
    if (fbfr == NULL) {
        cnaps_return_error(rqst, "4002", "response buffer allocation failed");
        return;
    }
    cnaps_put_long(fbfr, CNAPS_F_PAGE_NO, page_no);
    cnaps_put_long(fbfr, CNAPS_F_PAGE_SIZE, page_size);
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_ELEMENTS, total);
    for (int i = 0; i < row_count; ++i) {
        cnaps_put_voucher_occurrence(fbfr, &rows[i], (FLDOCC32)i);
    }
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
    cnaps_put_voucher_detail_fields(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", "detail query success");
}
