#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"

int db_connect(void)
{
    return 0;
}

int db_begin(void)
{
    return 0;
}

int db_commit(void)
{
    return 0;
}

int db_rollback(void)
{
    return 0;
}

int db_insert_voucher(const cnaps_voucher_row *row)
{
    (void)row;
    return 0;
}

int db_update_voucher(const cnaps_voucher_row *row)
{
    (void)row;
    return 0;
}

int db_find_voucher(const char *bill_id, cnaps_voucher_row *row)
{
    (void)bill_id;
    (void)row;
    return 1;
}

int db_query_vouchers(const char *work_date, const char *branch_no, const char *status)
{
    (void)work_date;
    (void)branch_no;
    (void)status;
    return 0;
}

int db_next_serial_no(const char *work_date, const char *branch_no, char *serial_no, int serial_no_size)
{
    (void)work_date;
    (void)branch_no;
    snprintf(serial_no, (size_t)serial_no_size, "%07d", 2000);
    return 0;
}
