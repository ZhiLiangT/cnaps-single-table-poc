#include <oci.h>
#include <atmi.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <userlog.h>
#include "cnaps_db.h"
#include "cnaps_status.h"

static OCIEnv *g_env = NULL;
static OCIError *g_err = NULL;
static OCISvcCtx *g_svc = NULL;

static const char *env_value(const char *name)
{
    const char *value = getenv(name);
    return value == NULL ? "" : value;
}

static int oci_check(sword status, const char *operation)
{
    text errbuf[512];
    sb4 errcode = 0;

    if (status == OCI_SUCCESS || status == OCI_SUCCESS_WITH_INFO || status == OCI_NO_DATA) {
        return 0;
    }

    if (g_err != NULL) {
        OCIErrorGet(g_err, 1, NULL, &errcode, errbuf, sizeof(errbuf), OCI_HTYPE_ERROR);
        userlog("%s failed: ORA-%d %s", operation, (int)errcode, errbuf);
    } else {
        userlog("%s failed with OCI status %d", operation, (int)status);
    }
    return -1;
}

int db_connect(void)
{
    const char *user = env_value("ORACLE_USER");
    const char *password = env_value("ORACLE_PASSWORD");
    const char *connect_string = env_value("ORACLE_CONNECT_STRING");

    if (g_svc != NULL) {
        return 0;
    }
    if (user[0] == '\0' || password[0] == '\0' || connect_string[0] == '\0') {
        userlog("Oracle environment is incomplete; require ORACLE_USER, ORACLE_PASSWORD, ORACLE_CONNECT_STRING");
        return -1;
    }
    if (oci_check(OCIEnvCreate(&g_env, OCI_THREADED | OCI_OBJECT, NULL, NULL, NULL, NULL, 0, NULL), "OCIEnvCreate") != 0) {
        return -1;
    }
    if (oci_check(OCIHandleAlloc(g_env, (dvoid **)&g_err, OCI_HTYPE_ERROR, 0, NULL), "OCIHandleAlloc(OCIError)") != 0) {
        return -1;
    }
    if (oci_check(OCILogon2(
        g_env,
        g_err,
        &g_svc,
        (const OraText *)user,
        (ub4)strlen(user),
        (const OraText *)password,
        (ub4)strlen(password),
        (const OraText *)connect_string,
        (ub4)strlen(connect_string),
        OCI_DEFAULT
    ), "OCILogon2") != 0) {
        return -1;
    }
    userlog("connected to Oracle %s as %s", connect_string, user);
    return 0;
}

void db_disconnect(void)
{
    if (g_svc != NULL) {
        OCILogoff(g_svc, g_err);
        g_svc = NULL;
    }
    if (g_err != NULL) {
        OCIHandleFree(g_err, OCI_HTYPE_ERROR);
        g_err = NULL;
    }
    if (g_env != NULL) {
        OCIHandleFree(g_env, OCI_HTYPE_ENV);
        g_env = NULL;
    }
}

static int prepare_stmt(const char *sql, OCIStmt **stmt)
{
    if (db_connect() != 0) {
        return -1;
    }
    if (oci_check(OCIHandleAlloc(g_env, (dvoid **)stmt, OCI_HTYPE_STMT, 0, NULL), "OCIHandleAlloc(OCIStmt)") != 0) {
        return -1;
    }
    return oci_check(OCIStmtPrepare(*stmt, g_err, (const OraText *)sql, (ub4)strlen(sql), OCI_NTV_SYNTAX, OCI_DEFAULT), "OCIStmtPrepare");
}

static void free_stmt(OCIStmt *stmt)
{
    if (stmt != NULL) {
        OCIHandleFree(stmt, OCI_HTYPE_STMT);
    }
}

static int bind_text(OCIStmt *stmt, const char *name, const char *value)
{
    OCIBind *bind = NULL;
    const char *safe_value = value == NULL ? "" : value;
    return oci_check(OCIBindByName(
        stmt,
        &bind,
        g_err,
        (const OraText *)name,
        (sb4)strlen(name),
        (dvoid *)safe_value,
        (sb4)strlen(safe_value) + 1,
        SQLT_STR,
        NULL,
        NULL,
        NULL,
        0,
        NULL,
        OCI_DEFAULT
    ), "OCIBindByName");
}

static int sql_has_bind(const char *sql, const char *name)
{
    size_t name_len = strlen(name);
    const char *match = strstr(sql, name);

    while (match != NULL) {
        unsigned char next = (unsigned char)match[name_len];
        if (!(isalnum(next) || next == '_')) {
            return 1;
        }
        match = strstr(match + name_len, name);
    }
    return 0;
}

static int bind_text_if_present(OCIStmt *stmt, const char *sql, const char *name, const char *value)
{
    return sql_has_bind(sql, name) ? bind_text(stmt, name, value) : 0;
}

static int bind_long(OCIStmt *stmt, const char *name, const long *value)
{
    OCIBind *bind = NULL;
    return oci_check(OCIBindByName(
        stmt,
        &bind,
        g_err,
        (const OraText *)name,
        (sb4)strlen(name),
        (dvoid *)value,
        (sb4)sizeof(*value),
        SQLT_INT,
        NULL,
        NULL,
        NULL,
        0,
        NULL,
        OCI_DEFAULT
    ), "OCIBindByName(long)");
}

static int execute_dml(const char *sql, const cnaps_voucher_row *row, int require_affected_row)
{
    OCIStmt *stmt = NULL;
    ub4 row_count = 0;
    ub4 attr_size = 0;
    int rc = -1;

    if (prepare_stmt(sql, &stmt) != 0) {
        return -1;
    }

#define BIND(NAME, VALUE) do { if (bind_text_if_present(stmt, sql, NAME, VALUE) != 0) goto cleanup; } while (0)
    BIND(":bill_id", row->bill_id);
    BIND(":work_date", row->work_date);
    BIND(":branch_no", row->branch_no);
    BIND(":operator_no", row->operator_no);
    BIND(":serial_no", row->serial_no);
    BIND(":business_type", row->business_type);
    BIND(":account_part1", row->account_part1);
    BIND(":account_part2", row->account_part2);
    BIND(":account_part3", row->account_part3);
    BIND(":account_name", row->account_name);
    BIND(":payer_name", row->payer_name);
    BIND(":payer_address", row->payer_address);
    BIND(":payer_bank_name", row->payer_bank_name);
    BIND(":payee_account_no", row->payee_account_no);
    BIND(":payee_name", row->payee_name);
    BIND(":payee_address", row->payee_address);
    BIND(":priority", row->priority);
    BIND(":receive_bank_no", row->receive_bank_no);
    BIND(":receive_bank_name", row->receive_bank_name);
    BIND(":system_type", row->system_type);
    BIND(":amount", row->amount);
    BIND(":debit_mode", row->debit_mode);
    BIND(":fee_amount", row->fee_amount);
    BIND(":fee_charge_mode", row->fee_charge_mode);
    BIND(":send_mode", row->send_mode);
    BIND(":fax_flag", row->fax_flag);
    BIND(":voucher_no", row->voucher_no);
    BIND(":remark", row->remark);
    BIND(":status", row->status);
    BIND(":checker_no", row->checker_no);
    BIND(":review_comment", row->review_comment);
    BIND(":reject_reason", row->reject_reason);
    BIND(":delete_reason", row->delete_reason);
    BIND(":delete_operator_no", row->delete_operator_no);
    BIND(":checker_time", row->checker_time);
    BIND(":delete_time", row->delete_time);
    BIND(":last_action", row->last_action);
    BIND(":last_operator_no", row->last_operator_no);
    BIND(":last_request_id", row->last_request_id);
#undef BIND
    if (sql_has_bind(sql, ":version_no") && bind_long(stmt, ":version_no", &row->version_no) != 0) {
        goto cleanup;
    }

    if (oci_check(OCIStmtExecute(g_svc, stmt, g_err, 1, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute") != 0) {
        goto cleanup;
    }
    if (require_affected_row
        && oci_check(OCIAttrGet(stmt, OCI_HTYPE_STMT, &row_count, &attr_size, OCI_ATTR_ROW_COUNT, g_err), "OCIAttrGet(row count)") != 0) {
        goto cleanup;
    }
    rc = require_affected_row && row_count == 0 ? 1 : 0;

cleanup:
    free_stmt(stmt);
    return rc;
}

int db_ping(void)
{
    OCIStmt *stmt = NULL;
    OCIDefine *define = NULL;
    int ping_value = 0;
    int rc;
    if (prepare_stmt("SELECT 1 FROM dual", &stmt) != 0) {
        return -1;
    }
    if (oci_check(OCIDefineByPos(stmt, &define, g_err, 1, &ping_value, sizeof(ping_value), SQLT_INT, NULL, NULL, NULL, OCI_DEFAULT), "OCIDefineByPos(ping)") != 0) {
        free_stmt(stmt);
        return -1;
    }
    rc = oci_check(OCIStmtExecute(g_svc, stmt, g_err, 1, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute(ping)");
    free_stmt(stmt);
    return rc;
}

int db_begin(void)
{
    return db_connect();
}

int db_commit(void)
{
    return db_connect() == 0 ? oci_check(OCITransCommit(g_svc, g_err, OCI_DEFAULT), "OCITransCommit") : -1;
}

int db_rollback(void)
{
    return g_svc == NULL ? 0 : oci_check(OCITransRollback(g_svc, g_err, OCI_DEFAULT), "OCITransRollback");
}

int db_insert_voucher(const cnaps_voucher_row *row)
{
    static const char *sql =
        "INSERT INTO T_CNAPS_BILL_POC ("
        "BILL_ID, WORK_DATE, BRANCH_NO, OPERATOR_NO, SERIAL_NO, BUSINESS_TYPE, "
        "ACCOUNT_PART1, ACCOUNT_PART2, ACCOUNT_PART3, ACCOUNT_NAME, PAYER_NAME, PAYER_ADDRESS, PAYER_BANK_NAME, "
        "PAYEE_ACCOUNT_NO, PAYEE_NAME, PAYEE_ADDRESS, PRIORITY, RECEIVE_BANK_NO, RECEIVE_BANK_NAME, SYSTEM_TYPE, "
        "AMOUNT, DEBIT_MODE, FEE_AMOUNT, FEE_CHARGE_MODE, SEND_MODE, FAX_FLAG, VOUCHER_NO, REMARK, "
        "STATUS, LAST_ACTION, LAST_OPERATOR_NO, LAST_REQUEST_ID, LAST_ACTION_TIME, CREATED_AT, UPDATED_AT, VERSION_NO"
        ") VALUES ("
        ":bill_id, TO_DATE(:work_date, 'YYYY-MM-DD'), :branch_no, :operator_no, :serial_no, :business_type, "
        ":account_part1, :account_part2, :account_part3, :account_name, :payer_name, :payer_address, :payer_bank_name, "
        ":payee_account_no, :payee_name, :payee_address, :priority, :receive_bank_no, :receive_bank_name, :system_type, "
        "TO_NUMBER(:amount), :debit_mode, TO_NUMBER(NVL(:fee_amount, '0')), :fee_charge_mode, :send_mode, :fax_flag, "
        ":voucher_no, :remark, :status, :last_action, :last_operator_no, :last_request_id, SYSTIMESTAMP, SYSTIMESTAMP, SYSTIMESTAMP, 1"
        ")";

    return execute_dml(sql, row, 0);
}

int db_update_voucher(const cnaps_voucher_row *row)
{
    static const char *sql =
        "UPDATE T_CNAPS_BILL_POC SET "
        "WORK_DATE=COALESCE(TO_DATE(:work_date, 'YYYY-MM-DD'), WORK_DATE), "
        "BUSINESS_TYPE=:business_type, ACCOUNT_PART1=:account_part1, ACCOUNT_PART2=:account_part2, "
        "ACCOUNT_PART3=:account_part3, ACCOUNT_NAME=:account_name, PAYER_NAME=:payer_name, "
        "PAYER_ADDRESS=:payer_address, PAYER_BANK_NAME=:payer_bank_name, "
        "PAYEE_ACCOUNT_NO=:payee_account_no, PAYEE_NAME=:payee_name, PAYEE_ADDRESS=:payee_address, PRIORITY=:priority, "
        "RECEIVE_BANK_NO=:receive_bank_no, RECEIVE_BANK_NAME=:receive_bank_name, SYSTEM_TYPE=:system_type, "
        "AMOUNT=TO_NUMBER(:amount), DEBIT_MODE=:debit_mode, FEE_AMOUNT=TO_NUMBER(NVL(:fee_amount, '0')), "
        "FEE_CHARGE_MODE=:fee_charge_mode, SEND_MODE=:send_mode, FAX_FLAG=:fax_flag, "
        "VOUCHER_NO=:voucher_no, REMARK=:remark, STATUS=:status, CHECKER_NO=:checker_no, "
        "CHECKER_TIME=CASE WHEN :last_action IN ('REVIEW_PASS', 'REVIEW_RETURN') THEN SYSTIMESTAMP "
        "WHEN :checker_time IS NULL THEN NULL ELSE TO_TIMESTAMP(:checker_time, 'YYYY-MM-DD HH24:MI:SS') END, "
        "REVIEW_COMMENT=:review_comment, REJECT_REASON=:reject_reason, DELETE_REASON=:delete_reason, "
        "DELETE_OPERATOR_NO=:delete_operator_no, "
        "DELETE_TIME=CASE WHEN :last_action='DELETE' THEN SYSTIMESTAMP WHEN :delete_time IS NULL THEN NULL "
        "ELSE TO_TIMESTAMP(:delete_time, 'YYYY-MM-DD HH24:MI:SS') END, "
        "LAST_ACTION=:last_action, LAST_OPERATOR_NO=:last_operator_no, LAST_REQUEST_ID=:last_request_id, "
        "UPDATED_AT=SYSTIMESTAMP, LAST_ACTION_TIME=SYSTIMESTAMP, VERSION_NO=NVL(VERSION_NO, 1) + 1 "
        "WHERE BILL_ID=:bill_id AND NVL(VERSION_NO, 1)=:version_no";

    return execute_dml(sql, row, 1);
}

static int define_text(OCIStmt *stmt, int position, char *buffer, ub4 buffer_size, sb2 *indicator)
{
    OCIDefine *define = NULL;
    return oci_check(OCIDefineByPos(stmt, &define, g_err, position, buffer, buffer_size, SQLT_STR, indicator, NULL, NULL, OCI_DEFAULT), "OCIDefineByPos");
}

static int define_long(OCIStmt *stmt, int position, long *value, sb2 *indicator)
{
    OCIDefine *define = NULL;
    return oci_check(OCIDefineByPos(stmt, &define, g_err, position, value, sizeof(*value), SQLT_INT, indicator, NULL, NULL, OCI_DEFAULT), "OCIDefineByPos(long)");
}

#define CNAPS_VOUCHER_SELECT_COLUMNS \
    "BILL_ID, TO_CHAR(WORK_DATE, 'YYYY-MM-DD'), BRANCH_NO, OPERATOR_NO, SERIAL_NO, " \
    "BUSINESS_TYPE, ACCOUNT_PART1, ACCOUNT_PART2, ACCOUNT_PART3, " \
    "NVL(ACCOUNT_NAME, ''), NVL(PAYER_NAME, ''), NVL(PAYER_ADDRESS, ''), NVL(PAYER_BANK_NAME, ''), " \
    "PAYEE_ACCOUNT_NO, PAYEE_NAME, NVL(PAYEE_ADDRESS, ''), " \
    "NVL(PRIORITY, ''), NVL(RECEIVE_BANK_NO, ''), NVL(RECEIVE_BANK_NAME, ''), " \
    "NVL(SYSTEM_TYPE, ''), TO_CHAR(AMOUNT, 'FM9999999999999990D00'), " \
    "NVL(DEBIT_MODE, ''), TO_CHAR(NVL(FEE_AMOUNT, 0), 'FM9999999999999990D00'), " \
    "NVL(FEE_CHARGE_MODE, ''), NVL(SEND_MODE, ''), NVL(FAX_FLAG, ''), " \
    "NVL(VOUCHER_NO, ''), NVL(REMARK, ''), STATUS, " \
    "NVL(CHECKER_NO, ''), NVL(TO_CHAR(CHECKER_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''), " \
    "NVL(REVIEW_COMMENT, ''), NVL(REJECT_REASON, ''), NVL(DELETE_REASON, ''), " \
    "NVL(DELETE_OPERATOR_NO, ''), NVL(TO_CHAR(DELETE_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''), " \
    "NVL(LAST_ACTION, ''), NVL(LAST_OPERATOR_NO, ''), NVL(LAST_REQUEST_ID, ''), " \
    "NVL(TO_CHAR(LAST_ACTION_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''), " \
    "NVL(TO_CHAR(CREATED_AT, 'YYYY-MM-DD HH24:MI:SS'), ''), " \
    "NVL(TO_CHAR(UPDATED_AT, 'YYYY-MM-DD HH24:MI:SS'), ''), NVL(VERSION_NO, 1) "

#define CNAPS_QUERY_PREDICATES \
    "WHERE (:work_date IS NULL OR WORK_DATE=TO_DATE(:work_date, 'YYYY-MM-DD')) " \
    "AND (:branch_no IS NULL OR BRANCH_NO=:branch_no) " \
    "AND (:status IS NULL OR STATUS=:status) " \
    "AND (:serial_no IS NULL OR SERIAL_NO=:serial_no) " \
    "AND (:voucher_no IS NULL OR VOUCHER_NO=:voucher_no) " \
    "AND (:payee_name IS NULL OR PAYEE_NAME LIKE '%' || :payee_name || '%') " \
    "AND (:payee_account_no IS NULL OR PAYEE_ACCOUNT_NO=:payee_account_no) " \
    "AND (:include_deleted=1 OR STATUS<>'40_DELETED') "

static int define_voucher_row(OCIStmt *stmt, cnaps_voucher_row *row, sb2 indicators[43])
{
#define DEFINE_TEXT(POSITION, MEMBER) do { \
    if (define_text(stmt, POSITION, row->MEMBER, sizeof(row->MEMBER), &indicators[(POSITION) - 1]) != 0) return -1; \
} while (0)
    DEFINE_TEXT(1, bill_id);
    DEFINE_TEXT(2, work_date);
    DEFINE_TEXT(3, branch_no);
    DEFINE_TEXT(4, operator_no);
    DEFINE_TEXT(5, serial_no);
    DEFINE_TEXT(6, business_type);
    DEFINE_TEXT(7, account_part1);
    DEFINE_TEXT(8, account_part2);
    DEFINE_TEXT(9, account_part3);
    DEFINE_TEXT(10, account_name);
    DEFINE_TEXT(11, payer_name);
    DEFINE_TEXT(12, payer_address);
    DEFINE_TEXT(13, payer_bank_name);
    DEFINE_TEXT(14, payee_account_no);
    DEFINE_TEXT(15, payee_name);
    DEFINE_TEXT(16, payee_address);
    DEFINE_TEXT(17, priority);
    DEFINE_TEXT(18, receive_bank_no);
    DEFINE_TEXT(19, receive_bank_name);
    DEFINE_TEXT(20, system_type);
    DEFINE_TEXT(21, amount);
    DEFINE_TEXT(22, debit_mode);
    DEFINE_TEXT(23, fee_amount);
    DEFINE_TEXT(24, fee_charge_mode);
    DEFINE_TEXT(25, send_mode);
    DEFINE_TEXT(26, fax_flag);
    DEFINE_TEXT(27, voucher_no);
    DEFINE_TEXT(28, remark);
    DEFINE_TEXT(29, status);
    DEFINE_TEXT(30, checker_no);
    DEFINE_TEXT(31, checker_time);
    DEFINE_TEXT(32, review_comment);
    DEFINE_TEXT(33, reject_reason);
    DEFINE_TEXT(34, delete_reason);
    DEFINE_TEXT(35, delete_operator_no);
    DEFINE_TEXT(36, delete_time);
    DEFINE_TEXT(37, last_action);
    DEFINE_TEXT(38, last_operator_no);
    DEFINE_TEXT(39, last_request_id);
    DEFINE_TEXT(40, last_action_time);
    DEFINE_TEXT(41, created_at);
    DEFINE_TEXT(42, updated_at);
#undef DEFINE_TEXT
    return define_long(stmt, 43, &row->version_no, &indicators[42]);
}

static void clear_voucher_nulls(cnaps_voucher_row *row, const sb2 indicators[43])
{
    char *fields[42] = {
        row->bill_id, row->work_date, row->branch_no, row->operator_no, row->serial_no,
        row->business_type, row->account_part1, row->account_part2, row->account_part3,
        row->account_name, row->payer_name, row->payer_address, row->payer_bank_name,
        row->payee_account_no, row->payee_name, row->payee_address,
        row->priority, row->receive_bank_no, row->receive_bank_name, row->system_type,
        row->amount, row->debit_mode, row->fee_amount, row->fee_charge_mode, row->send_mode,
        row->fax_flag, row->voucher_no, row->remark, row->status, row->checker_no,
        row->checker_time, row->review_comment, row->reject_reason, row->delete_reason,
        row->delete_operator_no, row->delete_time, row->last_action, row->last_operator_no,
        row->last_request_id, row->last_action_time, row->created_at, row->updated_at
    };

    for (size_t i = 0; i < sizeof(fields) / sizeof(fields[0]); ++i) {
        if (indicators[i] == -1) {
            fields[i][0] = '\0';
        }
    }
    if (indicators[42] == -1) {
        row->version_no = 1;
    }
}

int db_find_voucher(const char *bill_id, cnaps_voucher_row *row)
{
    static const char *sql =
        "SELECT " CNAPS_VOUCHER_SELECT_COLUMNS
        "FROM T_CNAPS_BILL_POC WHERE BILL_ID=:bill_id";
    OCIStmt *stmt = NULL;
    sword status;
    sb2 indicators[43] = {0};

    memset(row, 0, sizeof(*row));
    if (prepare_stmt(sql, &stmt) != 0) {
        return -1;
    }
    if (bind_text(stmt, ":bill_id", bill_id) != 0) goto define_error;
    if (define_voucher_row(stmt, row, indicators) != 0) goto define_error;
    if (oci_check(OCIStmtExecute(g_svc, stmt, g_err, 0, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute(find)") != 0) {
        free_stmt(stmt);
        return -1;
    }
    status = OCIStmtFetch2(stmt, g_err, 1, OCI_FETCH_NEXT, 0, OCI_DEFAULT);
    free_stmt(stmt);
    if (status == OCI_NO_DATA) {
        return 1;
    }
    if (oci_check(status, "OCIStmtFetch2(find)") != 0) {
        return -1;
    }
    clear_voucher_nulls(row, indicators);
    return 0;

define_error:
    free_stmt(stmt);
    return -1;
}

int db_query_vouchers(
    const char *work_date,
    const char *branch_no,
    const char *status_filter,
    const char *serial_no,
    const char *voucher_no,
    const char *payee_name,
    const char *payee_account_no,
    int include_deleted,
    int page_no,
    int page_size,
    cnaps_voucher_row *rows,
    int row_capacity,
    int *total
)
{
    static const char *count_sql =
        "SELECT COUNT(1) FROM T_CNAPS_BILL_POC " CNAPS_QUERY_PREDICATES;
    static const char *page_sql =
        "SELECT " CNAPS_VOUCHER_SELECT_COLUMNS
        "FROM T_CNAPS_BILL_POC " CNAPS_QUERY_PREDICATES
        "ORDER BY BILL_ID "
        "OFFSET :offset_rows ROWS FETCH NEXT :page_size ROWS ONLY";
    OCIStmt *stmt = NULL;
    cnaps_voucher_row fetched = {0};
    sb2 indicators[43] = {0};
    sword fetch_status;
    long total_value = 0;
    long include_deleted_value = include_deleted ? 1 : 0;
    long offset_rows;
    long page_size_value;
    int row_count = 0;

    if (total == NULL) {
        return -1;
    }
    *total = 0;
    if (page_no <= 0) page_no = 1;
    if (page_size <= 0) page_size = 10;
    if (rows == NULL || row_capacity <= 0) page_size = 0;
    if (row_capacity > 0 && page_size > row_capacity) page_size = row_capacity;

    if (prepare_stmt(count_sql, &stmt) != 0) return -1;
    if (bind_text(stmt, ":work_date", work_date) != 0
        || bind_text(stmt, ":branch_no", branch_no) != 0
        || bind_text(stmt, ":status", status_filter) != 0
        || bind_text(stmt, ":serial_no", serial_no) != 0
        || bind_text(stmt, ":voucher_no", voucher_no) != 0
        || bind_text(stmt, ":payee_name", payee_name) != 0
        || bind_text(stmt, ":payee_account_no", payee_account_no) != 0
        || bind_long(stmt, ":include_deleted", &include_deleted_value) != 0
        || define_long(stmt, 1, &total_value, NULL) != 0
        || oci_check(OCIStmtExecute(g_svc, stmt, g_err, 0, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute(count)") != 0
        || oci_check(OCIStmtFetch2(stmt, g_err, 1, OCI_FETCH_NEXT, 0, OCI_DEFAULT), "OCIStmtFetch2(count)") != 0) {
        free_stmt(stmt);
        return -1;
    }
    free_stmt(stmt);
    stmt = NULL;
    *total = (int)total_value;
    if (page_size == 0 || total_value == 0) return 0;

    offset_rows = ((long)page_no - 1L) * (long)page_size;
    page_size_value = page_size;
    if (prepare_stmt(page_sql, &stmt) != 0) return -1;
    if (bind_text(stmt, ":work_date", work_date) != 0
        || bind_text(stmt, ":branch_no", branch_no) != 0
        || bind_text(stmt, ":status", status_filter) != 0
        || bind_text(stmt, ":serial_no", serial_no) != 0
        || bind_text(stmt, ":voucher_no", voucher_no) != 0
        || bind_text(stmt, ":payee_name", payee_name) != 0
        || bind_text(stmt, ":payee_account_no", payee_account_no) != 0
        || bind_long(stmt, ":include_deleted", &include_deleted_value) != 0
        || bind_long(stmt, ":offset_rows", &offset_rows) != 0
        || bind_long(stmt, ":page_size", &page_size_value) != 0
        || define_voucher_row(stmt, &fetched, indicators) != 0
        || oci_check(OCIStmtExecute(g_svc, stmt, g_err, 0, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute(query)") != 0) {
        free_stmt(stmt);
        return -1;
    }
    while (row_count < page_size) {
        fetch_status = OCIStmtFetch2(stmt, g_err, 1, OCI_FETCH_NEXT, 0, OCI_DEFAULT);
        if (fetch_status == OCI_NO_DATA) break;
        if (oci_check(fetch_status, "OCIStmtFetch2(query)") != 0) {
            free_stmt(stmt);
            return -1;
        }
        clear_voucher_nulls(&fetched, indicators);
        rows[row_count++] = fetched;
        memset(&fetched, 0, sizeof(fetched));
        memset(indicators, 0, sizeof(indicators));
    }
    free_stmt(stmt);
    return row_count;
}

int db_next_serial_no(const char *work_date, const char *branch_no, char *serial_no, int serial_no_size)
{
    static const char *sql =
        "SELECT NVL(MAX(TO_NUMBER(SERIAL_NO)), 1999) + 1 "
        "FROM T_CNAPS_BILL_POC WHERE WORK_DATE=TO_DATE(:work_date, 'YYYY-MM-DD') AND BRANCH_NO=:branch_no";
    OCIStmt *stmt = NULL;
    int next_value = 2000;
    OCIDefine *define = NULL;

    if (prepare_stmt(sql, &stmt) != 0) {
        return -1;
    }
    if (bind_text(stmt, ":work_date", work_date) != 0
        || bind_text(stmt, ":branch_no", branch_no) != 0
        || oci_check(OCIDefineByPos(stmt, &define, g_err, 1, &next_value, sizeof(next_value), SQLT_INT, NULL, NULL, NULL, OCI_DEFAULT), "OCIDefineByPos(serial)") != 0
        || oci_check(OCIStmtExecute(g_svc, stmt, g_err, 1, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute(serial)") != 0) {
        free_stmt(stmt);
        return -1;
    }
    free_stmt(stmt);
    snprintf(serial_no, (size_t)serial_no_size, "%07d", next_value);
    return 0;
}
