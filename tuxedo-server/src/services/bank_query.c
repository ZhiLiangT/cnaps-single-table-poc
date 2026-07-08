#include "cnaps_service.h"

void BANKQRY(TPSVCINFO *rqst)
{
    cnaps_log_service_start("BANKQRY");
    cnaps_return_ok(rqst, "BANKQRY");
}
