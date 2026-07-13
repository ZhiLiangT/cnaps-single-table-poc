#ifndef CNAPS_DB_H
#define CNAPS_DB_H

typedef struct {
    char bill_id[33];
    char work_date[11];
    char branch_no[13];
    char operator_no[17];
    char serial_no[17];
    char status[33];
    char business_type[13];
    char account_part1[33];
    char account_part2[33];
    char account_part3[65];
    char account_name[129];
    char payer_name[129];
    char payer_address[1025];
    char payer_bank_name[513];
    char payee_account_no[65];
    char payee_name[129];
    char payee_address[1025];
    char priority[13];
    char receive_bank_no[33];
    char receive_bank_name[129];
    char system_type[17];
    char amount[32];
    char debit_mode[9];
    char fee_amount[32];
    char fee_charge_mode[9];
    char send_mode[9];
    char fax_flag[2];
    char voucher_no[65];
    char remark[513];
    char checker_no[17];
    char checker_time[20];
    char review_comment[513];
    char reject_reason[201];
    char delete_reason[201];
    char delete_operator_no[17];
    char delete_time[20];
    char last_action[33];
    char last_operator_no[17];
    char last_request_id[33];
    char last_action_time[20];
    char created_at[20];
    char updated_at[20];
    long version_no;
} cnaps_voucher_row;

int db_connect(void);
void db_disconnect(void);
int db_ping(void);
int db_begin(void);
int db_commit(void);
int db_rollback(void);
int db_insert_voucher(const cnaps_voucher_row *row);
int db_update_voucher(const cnaps_voucher_row *row);
int db_find_voucher(const char *bill_id, cnaps_voucher_row *row);
int db_query_vouchers(
    const char *start_work_date,
    const char *end_work_date,
    const char *branch_no,
    const char *status,
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
);
int db_next_serial_no(const char *work_date, const char *branch_no, char *serial_no, int serial_no_size);

#endif
