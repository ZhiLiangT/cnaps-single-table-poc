#ifndef CNAPS_SERVICE_H
#define CNAPS_SERVICE_H

#include <atmi.h>
#include <fml32.h>

void cnaps_return_ok(TPSVCINFO *rqst, const char *service_name);
void cnaps_return_error(TPSVCINFO *rqst, const char *resp_code, const char *resp_msg);
void cnaps_log_service_start(const char *service_name);

#endif
