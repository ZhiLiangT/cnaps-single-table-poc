#ifndef CNAPS_STATUS_H
#define CNAPS_STATUS_H

#define CNAPS_STATUS_DRAFT "00_DRAFT"
#define CNAPS_STATUS_DELETED "40_DELETED"

int cnaps_status_can_edit(const char *status);
int cnaps_status_can_review(const char *status);

#endif
