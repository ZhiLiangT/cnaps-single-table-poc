#include "cnaps_db.h"
#include "cnaps_service.h"

void CNAPS5701E(TPSVCINFO *rqst)
{
    cnaps_voucher_row row = {0};
    cnaps_log_service_start("CNAPS5701E");
    if (db_begin() != 0 || db_insert_voucher(&row) != 0 || db_commit() != 0) {
        db_rollback();
        cnaps_return_error(rqst, "4001", "database error");
        return;
    }
    cnaps_return_ok(rqst, "CNAPS5701E");
}
