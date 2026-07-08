#include <string.h>
#include "cnaps_status.h"

int cnaps_status_can_edit(const char *status)
{
    return strcmp(status, CNAPS_STATUS_DRAFT) == 0
        || strcmp(status, CNAPS_STATUS_PENDING_REVIEW) == 0
        || strcmp(status, CNAPS_STATUS_REJECTED) == 0;
}

int cnaps_status_can_review(const char *status)
{
    return strcmp(status, CNAPS_STATUS_PENDING_REVIEW) == 0;
}
