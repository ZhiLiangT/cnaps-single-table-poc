#ifndef CNAPS_SERVICE_H
#define CNAPS_SERVICE_H

#include <atmi.h>
#include <fml32.h>
#include <stddef.h>
#include <userlog.h>

int cnaps_get_string(FBFR32 *fbfr, const char *field_name, char *out, size_t out_size);
int cnaps_get_long(FBFR32 *fbfr, const char *field_name, long *out);
int cnaps_put_string(FBFR32 *fbfr, const char *field_name, const char *value);
int cnaps_put_string_occurrence(FBFR32 *fbfr, const char *field_name, FLDOCC32 occurrence, const char *value);
int cnaps_put_long(FBFR32 *fbfr, const char *field_name, long value);
int cnaps_put_long_occurrence(FBFR32 *fbfr, const char *field_name, FLDOCC32 occurrence, long value);
void cnaps_put_voucher(FBFR32 *fbfr, const void *row);
void cnaps_put_voucher_occurrence(FBFR32 *fbfr, const void *row, FLDOCC32 occurrence);
void cnaps_return_response(TPSVCINFO *rqst, int success, const char *resp_code, const char *resp_msg);
void cnaps_return_ok(TPSVCINFO *rqst, const char *service_name);
void cnaps_return_error(TPSVCINFO *rqst, const char *resp_code, const char *resp_msg);
void cnaps_log_service_start(const char *service_name);

#endif
