#include "cnaps_fields.h"
#include "cnaps_service.h"

void DICTQRY(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    char dict_type[64] = "BUSINESS_TYPE";

    cnaps_log_service_start("DICTQRY");
    cnaps_get_string(fbfr, CNAPS_F_DICT_TYPE, dict_type, sizeof(dict_type));
    cnaps_put_string(fbfr, CNAPS_F_DICT_TYPE, dict_type);
    cnaps_put_string(fbfr, CNAPS_F_DICT_CODE, "02102");
    cnaps_put_string(fbfr, CNAPS_F_DICT_NAME, "ordinary remittance");
    cnaps_put_long(fbfr, CNAPS_F_SORT_NO, 1);
    cnaps_put_string(fbfr, CNAPS_F_SYSTEM_TYPE, "CNAPS");
    cnaps_return_response(rqst, 1, "0000", "query success");
}
