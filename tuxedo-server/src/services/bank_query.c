#include "cnaps_fields.h"
#include "cnaps_service.h"

void BANKQRY(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;

    cnaps_log_service_start("BANKQRY");
    cnaps_put_string(fbfr, CNAPS_F_BANK_NO, "102290000002");
    cnaps_put_string(fbfr, CNAPS_F_BANK_NAME, "CNAPS receiving bank");
    cnaps_put_string(fbfr, CNAPS_F_RECEIVE_BANK_NO, "102290000002");
    cnaps_put_string(fbfr, CNAPS_F_RECEIVE_BANK_NAME, "CNAPS receiving bank");
    cnaps_return_response(rqst, 1, "0000", "query success");
}
