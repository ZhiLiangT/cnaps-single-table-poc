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

static void review_voucher(
    TPSVCINFO *rqst,
    const char *service_name,
    const char *target_status,
    const char *action,
    int reject_required,
    const char *success_message
)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    cnaps_voucher_row row = {0};
    char bill_id[33] = {0};
    char operator_no[17] = {0};
    char request_id[33] = {0};
    char review_comment[513] = {0};
    char reject_reason[201] = {0};
    int rc;

    cnaps_log_service_start(service_name);
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

    get_field(fbfr, CNAPS_F_REJECT_REASON, reject_reason, sizeof(reject_reason));
    if (reject_required && is_blank(reject_reason)) {
        cnaps_return_error(rqst, "2001", "rejectReason is required");
        return;
    }
    if (strcmp(row.status, CNAPS_STATUS_PENDING_REVIEW) != 0) {
        cnaps_return_error(rqst, "3004", "单据状态已变化");
        return;
    }

    get_field(fbfr, CNAPS_F_OPERATOR_NO, operator_no, sizeof(operator_no));
    get_field(fbfr, CNAPS_F_REQ_ID, request_id, sizeof(request_id));
    get_field(fbfr, CNAPS_F_REVIEW_COMMENT, review_comment, sizeof(review_comment));
    snprintf(row.checker_no, sizeof(row.checker_no), "%s", operator_no);
    snprintf(row.last_operator_no, sizeof(row.last_operator_no), "%s", operator_no);
    snprintf(row.last_request_id, sizeof(row.last_request_id), "%s", request_id);
    snprintf(row.status, sizeof(row.status), "%s", target_status);
    snprintf(row.last_action, sizeof(row.last_action), "%s", action);
    row.checker_time[0] = '\0';
    if (reject_required) {
        snprintf(row.reject_reason, sizeof(row.reject_reason), "%s", reject_reason);
        row.review_comment[0] = '\0';
    } else {
        snprintf(row.review_comment, sizeof(row.review_comment), "%s", review_comment);
        row.reject_reason[0] = '\0';
    }

    if (db_begin() != 0) {
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    rc = db_update_voucher(&row);
    if (rc == 1) {
        db_rollback();
        cnaps_return_error(rqst, "3004", "单据状态已变化");
        return;
    }
    if (rc != 0 || db_find_voucher(bill_id, &row) != 0 || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_put_voucher(fbfr, &row);
    cnaps_return_response(rqst, 1, "0000", success_message);
}

void CNAPS5702A(TPSVCINFO *rqst)
{
    review_voucher(
        rqst,
        "CNAPS5702A",
        CNAPS_STATUS_APPROVED,
        "REVIEW_PASS",
        0,
        "review pass success"
    );
}

void CNAPS5702R(TPSVCINFO *rqst)
{
    review_voucher(
        rqst,
        "CNAPS5702R",
        CNAPS_STATUS_REJECTED,
        "REVIEW_RETURN",
        1,
        "review return success"
    );
}
