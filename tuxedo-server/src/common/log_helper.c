#include "cnaps_service.h"

void cnaps_log_service_start(const char *service_name)
{
    userlog("service %s started", service_name);
}
