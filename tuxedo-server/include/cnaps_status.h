#ifndef CNAPS_STATUS_H
#define CNAPS_STATUS_H

#define CNAPS_STATUS_DRAFT "DRAFT"
#define CNAPS_STATUS_PENDING_REVIEW "PENDING_REVIEW"
#define CNAPS_STATUS_APPROVED "APPROVED"
#define CNAPS_STATUS_REJECTED "REJECTED"
#define CNAPS_STATUS_DELETED "DELETED"

int cnaps_status_can_edit(const char *status);
int cnaps_status_can_review(const char *status);

#endif
