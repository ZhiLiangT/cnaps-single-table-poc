#include "cnaps_service.h"

void DICTQRY(TPSVCINFO *rqst)
{
    cnaps_log_service_start("DICTQRY");
    cnaps_return_ok(rqst, "DICTQRY");
}
