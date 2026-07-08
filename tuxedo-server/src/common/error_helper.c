#include <string.h>
#include "cnaps_error.h"

const char *cnaps_error_message(const char *resp_code)
{
    if (strcmp(resp_code, CNAPS_RESP_SUCCESS) == 0) {
        return "success";
    }
    if (strcmp(resp_code, CNAPS_RESP_REQUIRED_FIELD) == 0) {
        return "required field is empty";
    }
    if (strcmp(resp_code, CNAPS_RESP_FIELD_FORMAT) == 0) {
        return "field format is invalid";
    }
    if (strcmp(resp_code, CNAPS_RESP_DICT_INVALID) == 0) {
        return "dictionary value is invalid";
    }
    if (strcmp(resp_code, CNAPS_RESP_NOT_FOUND) == 0) {
        return "record not found";
    }
    if (strcmp(resp_code, CNAPS_RESP_STATUS_NOT_ALLOWED) == 0) {
        return "status transition is not allowed";
    }
    if (strcmp(resp_code, CNAPS_RESP_DB_ERROR) == 0) {
        return "database error";
    }
    return "unknown error";
}
