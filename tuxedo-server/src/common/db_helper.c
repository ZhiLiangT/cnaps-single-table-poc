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

static int execute_dml(const char *sql, const cnaps_voucher_row *row)
{
    OCIStmt *stmt = NULL;
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
    BIND(":last_action", row->last_action);
    BIND(":last_operator_no", row->last_operator_no);
    BIND(":last_request_id", row->last_request_id);
#undef BIND

    rc = oci_check(OCIStmtExecute(g_svc, stmt, g_err, 1, 0, NULL, NULL, OCI_DEFAULT), "OCIStmtExecute");

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

    return execute_dml(sql, row);
}

int db_update_voucher(const cnaps_voucher_row *row)
{
    static const char *sql =
        "UPDATE T_CNAPS_BILL_POC SET "
        "WORK_DATE=COALESCE(TO_DATE(:work_date, 'YYYY-MM-DD'), WORK_DATE), "
        "PAYEE_ACCOUNT_NO=COALESCE(:payee_account_no, PAYEE_ACCOUNT_NO), "
        "PAYEE_NAME=COALESCE(:payee_name, PAYEE_NAME), "
        "AMOUNT=COALESCE(TO_NUMBER(:amount), AMOUNT), "
        "REMARK=COALESCE(:remark, REMARK), "
        "STATUS=COALESCE(:status, STATUS), "
        "CHECKER_NO=COALESCE(:checker_no, CHECKER_NO), "
        "REVIEW_COMMENT=COALESCE(:review_comment, REVIEW_COMMENT), "
        "REJECT_REASON=COALESCE(:reject_reason, REJECT_REASON), "
        "DELETE_REASON=COALESCE(:delete_reason, DELETE_REASON), "
        "DELETE_OPERATOR_NO=COALESCE(:delete_operator_no, DELETE_OPERATOR_NO), "
        "LAST_ACTION=COALESCE(:last_action, LAST_ACTION), "
        "LAST_OPERATOR_NO=COALESCE(:last_operator_no, LAST_OPERATOR_NO), "
        "LAST_REQUEST_ID=COALESCE(:last_request_id, LAST_REQUEST_ID), "
        "UPDATED_AT=SYSTIMESTAMP, LAST_ACTION_TIME=SYSTIMESTAMP, VERSION_NO=NVL(VERSION_NO, 1) + 1 "
        "WHERE BILL_ID=:bill_id";

    return execute_dml(sql, row);
}

static int define_text(OCIStmt *stmt, int position, char *buffer, ub4 buffer_size, sb2 *indicator)
{
    OCIDefine *define = NULL;
    return oci_check(OCIDefineByPos(stmt, &define, g_err, position, buffer, buffer_size, SQLT_STR, indicator, NULL, NULL, OCI_DEFAULT), "OCIDefineByPos");
}

int db_find_voucher(const char *bill_id, cnaps_voucher_row *row)
{
    static const char *sql =
        "SELECT BILL_ID, TO_CHAR(WORK_DATE, 'YYYY-MM-DD'), BRANCH_NO, OPERATOR_NO, SERIAL_NO, "
        "BUSINESS_TYPE, PAYEE_ACCOUNT_NO, PAYEE_NAME, TO_CHAR(AMOUNT), STATUS, "
        "NVL(REJECT_REASON, ''), NVL(REVIEW_COMMENT, ''), NVL(LAST_ACTION, '') "
        "FROM T_CNAPS_BILL_POC WHERE BILL_ID=:bill_id";
    OCIStmt *stmt = NULL;
    sword status;
    sb2 indicators[13] = {0};
    char *fields[13];

    memset(row, 0, sizeof(*row));
    fields[0] = row->bill_id;
    fields[1] = row->work_date;
    fields[2] = row->branch_no;
    fields[3] = row->operator_no;
    fields[4] = row->serial_no;
    fields[5] = row->business_type;
    fields[6] = row->payee_account_no;
    fields[7] = row->payee_name;
    fields[8] = row->amount;
    fields[9] = row->status;
    fields[10] = row->reject_reason;
    fields[11] = row->review_comment;
    fields[12] = row->last_action;
    if (prepare_stmt(sql, &stmt) != 0) {
        return -1;
    }
    if (bind_text(stmt, ":bill_id", bill_id) != 0
        || define_text(stmt, 1, row->bill_id, sizeof(row->bill_id), &indicators[0]) != 0
        || define_text(stmt, 2, row->work_date, sizeof(row->work_date), &indicators[1]) != 0
        || define_text(stmt, 3, row->branch_no, sizeof(row->branch_no), &indicators[2]) != 0
        || define_text(stmt, 4, row->operator_no, sizeof(row->operator_no), &indicators[3]) != 0
        || define_text(stmt, 5, row->serial_no, sizeof(row->serial_no), &indicators[4]) != 0
        || define_text(stmt, 6, row->business_type, sizeof(row->business_type), &indicators[5]) != 0
        || define_text(stmt, 7, row->payee_account_no, sizeof(row->payee_account_no), &indicators[6]) != 0
        || define_text(stmt, 8, row->payee_name, sizeof(row->payee_name), &indicators[7]) != 0
        || define_text(stmt, 9, row->amount, sizeof(row->amount), &indicators[8]) != 0
        || define_text(stmt, 10, row->status, sizeof(row->status), &indicators[9]) != 0
        || define_text(stmt, 11, row->reject_reason, sizeof(row->reject_reason), &indicators[10]) != 0
        || define_text(stmt, 12, row->review_comment, sizeof(row->review_comment), &indicators[11]) != 0
        || define_text(stmt, 13, row->last_action, sizeof(row->last_action), &indicators[12]) != 0) {
        free_stmt(stmt);
        return -1;
    }
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
    for (size_t i = 0; i < sizeof(indicators) / sizeof(indicators[0]); ++i) {
        if (indicators[i] == -1) {
            fields[i][0] = '\0';
        }
    }
    return 0;
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
