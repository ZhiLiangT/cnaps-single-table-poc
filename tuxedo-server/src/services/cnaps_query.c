#include "cnaps_db.h"
#include "cnaps_service.h"

void CNAPS4609Q(TPSVCINFO *rqst)
{
    cnaps_log_service_start("CNAPS4609Q");
    db_query_vouchers("", "", "");
    cnaps_return_ok(rqst, "CNAPS4609Q");
}

void CNAPS5702Q(TPSVCINFO *rqst)
{
    cnaps_log_service_start("CNAPS5702Q");
    db_query_vouchers("", "", "PENDING_REVIEW");
    cnaps_return_ok(rqst, "CNAPS5702Q");
}

void CNAPS5702I(TPSVCINFO *rqst)
{
    cnaps_voucher_row row = {0};
    cnaps_log_service_start("CNAPS5702I");
    db_find_voucher("", &row);
    cnaps_return_ok(rqst, "CNAPS5702I");
}
