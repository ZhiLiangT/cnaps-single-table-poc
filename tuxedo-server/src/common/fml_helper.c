#include <string.h>
#include "cnaps_error.h"
#include "cnaps_service.h"

void cnaps_return_ok(TPSVCINFO *rqst, const char *service_name)
{
    userlog("service %s completed", service_name);
    tpreturn(TPSUCCESS, 0, rqst->data, 0L, 0);
}

void cnaps_return_error(TPSVCINFO *rqst, const char *resp_code, const char *resp_msg)
{
    userlog("service failed: %s %s", resp_code, resp_msg);
    tpreturn(TPFAIL, 0, rqst->data, 0L, 0);
}
