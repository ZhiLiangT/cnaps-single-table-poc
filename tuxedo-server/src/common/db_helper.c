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
    BIND(":payee_account_no", row->payee_account_no);
    BIND(":payee_name", row->payee_name);
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
        "ACCOUNT_PART1, ACCOUNT_PART2, ACCOUNT_PART3, ACCOUNT_NAME, PAYER_NAME, "
        "PAYEE_ACCOUNT_NO, PAYEE_NAME, PRIORITY, RECEIVE_BANK_NO, RECEIVE_BANK_NAME, SYSTEM_TYPE, "
        "AMOUNT, DEBIT_MODE, FEE_AMOUNT, FEE_CHARGE_MODE, SEND_MODE, FAX_FLAG, VOUCHER_NO, REMARK, "
        "STATUS, LAST_ACTION, LAST_OPERATOR_NO, LAST_REQUEST_ID, LAST_ACTION_TIME, CREATED_AT, UPDATED_AT, VERSION_NO"
        ") VALUES ("
        ":bill_id, TO_DATE(:work_date, 'YYYY-MM-DD'), :branch_no, :operator_no, :serial_no, :business_type, "
        ":account_part1, :account_part2, :account_part3, :account_name, :payer_name, "
        ":payee_account_no, :payee_name, :priority, :receive_bank_no, :receive_bank_name, :system_type, "
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
        "PAYEE_ACCOUNT_NO=:payee_account_no, PAYEE_NAME=:payee_name, PRIORITY=:priority, "
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

int db_find_voucher(const char *bill_id, cnaps_voucher_row *row)
{
    static const char *sql =
        "SELECT BILL_ID, TO_CHAR(WORK_DATE, 'YYYY-MM-DD'), BRANCH_NO, OPERATOR_NO, SERIAL_NO, "
        "BUSINESS_TYPE, ACCOUNT_PART1, ACCOUNT_PART2, ACCOUNT_PART3, "
        "NVL(ACCOUNT_NAME, ''), NVL(PAYER_NAME, ''), PAYEE_ACCOUNT_NO, PAYEE_NAME, "
        "NVL(PRIORITY, ''), NVL(RECEIVE_BANK_NO, ''), NVL(RECEIVE_BANK_NAME, ''), "
        "NVL(SYSTEM_TYPE, ''), TO_CHAR(AMOUNT, 'FM999999999999990D00'), "
        "NVL(DEBIT_MODE, ''), TO_CHAR(NVL(FEE_AMOUNT, 0), 'FM999999999999990D00'), "
        "NVL(FEE_CHARGE_MODE, ''), NVL(SEND_MODE, ''), NVL(FAX_FLAG, ''), "
        "NVL(VOUCHER_NO, ''), NVL(REMARK, ''), STATUS, "
        "NVL(CHECKER_NO, ''), NVL(TO_CHAR(CHECKER_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''), "
        "NVL(REVIEW_COMMENT, ''), NVL(REJECT_REASON, ''), NVL(DELETE_REASON, ''), "
        "NVL(DELETE_OPERATOR_NO, ''), NVL(TO_CHAR(DELETE_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''), "
        "NVL(LAST_ACTION, ''), NVL(LAST_OPERATOR_NO, ''), NVL(LAST_REQUEST_ID, ''), "
        "NVL(TO_CHAR(LAST_ACTION_TIME, 'YYYY-MM-DD HH24:MI:SS'), ''), "
        "NVL(TO_CHAR(CREATED_AT, 'YYYY-MM-DD HH24:MI:SS'), ''), "
        "NVL(TO_CHAR(UPDATED_AT, 'YYYY-MM-DD HH24:MI:SS'), ''), NVL(VERSION_NO, 1) "
        "FROM T_CNAPS_BILL_POC WHERE BILL_ID=:bill_id";
    OCIStmt *stmt = NULL;
    sword status;
    sb2 indicators[40] = {0};
    char *fields[39];

    memset(row, 0, sizeof(*row));
    fields[0] = row->bill_id;
    fields[1] = row->work_date;
    fields[2] = row->branch_no;
    fields[3] = row->operator_no;
    fields[4] = row->serial_no;
    fields[5] = row->business_type;
    fields[6] = row->account_part1;
    fields[7] = row->account_part2;
    fields[8] = row->account_part3;
    fields[9] = row->account_name;
    fields[10] = row->payer_name;
    fields[11] = row->payee_account_no;
    fields[12] = row->payee_name;
    fields[13] = row->priority;
    fields[14] = row->receive_bank_no;
    fields[15] = row->receive_bank_name;
    fields[16] = row->system_type;
    fields[17] = row->amount;
    fields[18] = row->debit_mode;
    fields[19] = row->fee_amount;
    fields[20] = row->fee_charge_mode;
    fields[21] = row->send_mode;
    fields[22] = row->fax_flag;
    fields[23] = row->voucher_no;
    fields[24] = row->remark;
    fields[25] = row->status;
    fields[26] = row->checker_no;
    fields[27] = row->checker_time;
    fields[28] = row->review_comment;
    fields[29] = row->reject_reason;
    fields[30] = row->delete_reason;
    fields[31] = row->delete_operator_no;
    fields[32] = row->delete_time;
    fields[33] = row->last_action;
    fields[34] = row->last_operator_no;
    fields[35] = row->last_request_id;
    fields[36] = row->last_action_time;
    fields[37] = row->created_at;
    fields[38] = row->updated_at;
    if (prepare_stmt(sql, &stmt) != 0) {
        return -1;
    }
    if (bind_text(stmt, ":bill_id", bill_id) != 0) goto define_error;
#define DEFINE_TEXT(POSITION, MEMBER) do { \
    if (define_text(stmt, POSITION, row->MEMBER, sizeof(row->MEMBER), &indicators[(POSITION) - 1]) != 0) goto define_error; \
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
    DEFINE_TEXT(12, payee_account_no);
    DEFINE_TEXT(13, payee_name);
    DEFINE_TEXT(14, priority);
    DEFINE_TEXT(15, receive_bank_no);
    DEFINE_TEXT(16, receive_bank_name);
    DEFINE_TEXT(17, system_type);
    DEFINE_TEXT(18, amount);
    DEFINE_TEXT(19, debit_mode);
    DEFINE_TEXT(20, fee_amount);
    DEFINE_TEXT(21, fee_charge_mode);
    DEFINE_TEXT(22, send_mode);
    DEFINE_TEXT(23, fax_flag);
    DEFINE_TEXT(24, voucher_no);
    DEFINE_TEXT(25, remark);
    DEFINE_TEXT(26, status);
    DEFINE_TEXT(27, checker_no);
    DEFINE_TEXT(28, checker_time);
    DEFINE_TEXT(29, review_comment);
    DEFINE_TEXT(30, reject_reason);
    DEFINE_TEXT(31, delete_reason);
    DEFINE_TEXT(32, delete_operator_no);
    DEFINE_TEXT(33, delete_time);
    DEFINE_TEXT(34, last_action);
    DEFINE_TEXT(35, last_operator_no);
    DEFINE_TEXT(36, last_request_id);
    DEFINE_TEXT(37, last_action_time);
    DEFINE_TEXT(38, created_at);
    DEFINE_TEXT(39, updated_at);
#undef DEFINE_TEXT
    if (define_long(stmt, 40, &row->version_no, &indicators[39]) != 0) goto define_error;
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
    for (size_t i = 0; i < sizeof(fields) / sizeof(fields[0]); ++i) {
        if (indicators[i] == -1) {
            fields[i][0] = '\0';
        }
    }
    return 0;

define_error:
    free_stmt(stmt);
    return -1;
}

int db_query_vouchers(const char *work_date, const char *branch_no, const char *status)
{
    static const char *sql =
        "SELECT COUNT(1) FROM T_CNAPS_BILL_POC "
        "WHERE (:work_date IS NULL OR WORK_DATE=TO_DATE(:work_date, 'YYYY-MM-DD')) "
        "AND (:branch_no IS NULL OR BRANCH_NO=:branch_no) "
        "AND (:status IS NULL OR STATUS=:status)";
    OCIStmt *stmt = NULL;
    int total = 0;
    OCIDefine *define = NULL;

    if (prepare_stmt(sql, &stmt) != 0) {
        return -1;
    }
    if (bind_text(stmt, ":work_date", work_date) != 0
        || bind_text(stmt, ":branch_no", branch_no) != 0
        || bind_text(stmt, ":status", status) != 0
        || oci_check(OCIDefineByPos(stmt, &define, g_err, 1, &total, sizeof(total), SQLT_INT, NULL, NULL, NULL, OCI_DEFAULT), "OCIDefineByPos(count)") != 0
        || oci_check(OCIStmtExecute(g_svc, stmt, g_err, 1, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute(count)") != 0) {
        free_stmt(stmt);
        return -1;
    }
    free_stmt(stmt);
    return total;
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
