#include <string.h>
#include "cnaps_fields.h"
#include "cnaps_service.h"

typedef struct {
    const char *dict_type;
    const char *code;
    const char *name;
    long sort_no;
} cnaps_dict_item;

static const cnaps_dict_item ITEMS[] = {
    {"BUSINESS_TYPE", "02102", "普通汇兑", 1},
    {"PRIORITY", "NORM", "普通", 1},
    {"FEE_CHARGE_MODE", "1", "同城收费", 1},
    {"SEND_MODE", "0", "柜面", 1},
    {"DEBIT_MODE", "1", "扣收", 1},
    {"FAX_FLAG", "0", "否", 1},
    {"FAX_FLAG", "1", "是", 2},
    {"SYSTEM_TYPE", "CNAPS", "CNAPS", 1}
};

void DICTQRY(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    char dict_type[64] = {0};
    FLDOCC32 occurrence = 0;

    cnaps_log_service_start("DICTQRY");
    cnaps_get_string(fbfr, CNAPS_F_DICT_TYPE, dict_type, sizeof(dict_type));
    for (size_t i = 0; i < sizeof(ITEMS) / sizeof(ITEMS[0]); ++i) {
        if (strcmp(dict_type, ITEMS[i].dict_type) != 0) {
            continue;
        }
        cnaps_put_string_occurrence(fbfr, CNAPS_F_DICT_TYPE, occurrence, ITEMS[i].dict_type);
        cnaps_put_string_occurrence(fbfr, CNAPS_F_DICT_CODE, occurrence, ITEMS[i].code);
        cnaps_put_string_occurrence(fbfr, CNAPS_F_DICT_NAME, occurrence, ITEMS[i].name);
        cnaps_put_long_occurrence(fbfr, CNAPS_F_SORT_NO, occurrence, ITEMS[i].sort_no);
        ++occurrence;
    }
    if (occurrence == 0) {
        cnaps_return_error(rqst, "2003", "unknown dictionary type");
        return;
    }
    cnaps_return_response(rqst, 1, "0000", "query success");
}
