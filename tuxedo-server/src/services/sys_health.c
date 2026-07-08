#include "cnaps_service.h"

void SYSHEALTH(TPSVCINFO *rqst)
{
    cnaps_log_service_start("SYSHEALTH");
    cnaps_return_ok(rqst, "SYSHEALTH");
}
