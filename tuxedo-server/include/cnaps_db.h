#ifndef CNAPS_DB_H
#define CNAPS_DB_H

typedef struct {
    char bill_id[33];
    char work_date[11];
    char branch_no[13];
    char operator_no[17];
    char serial_no[17];
    char status[33];
    char amount[32];
} cnaps_voucher_row;

int db_connect(void);
int db_begin(void);
int db_commit(void);
int db_rollback(void);
int db_insert_voucher(const cnaps_voucher_row *row);
int db_update_voucher(const cnaps_voucher_row *row);
int db_find_voucher(const char *bill_id, cnaps_voucher_row *row);
int db_query_vouchers(const char *work_date, const char *branch_no, const char *status);
int db_next_serial_no(const char *work_date, const char *branch_no, char *serial_no, int serial_no_size);

#endif
