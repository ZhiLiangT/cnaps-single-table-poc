#include <string.h>
#include "cnaps_fields.h"
#include "cnaps_service.h"

#define BANK_QUERY_MAX_PAGE_SIZE 100

void BANKQRY(TPSVCINFO *rqst)
{
    static const char *bank_no = "102290000002";
    static const char *bank_name = "接收行名称";
    static const char *city = "上海";
    static const char *system_type = "CNAPS";
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    char bank_no_filter[33] = {0};
    char keyword[129] = {0};
    char city_filter[33] = {0};
    char system_type_filter[17] = {0};
    long page_no = 1;
    long page_size = 10;
    int matches;

    cnaps_log_service_start("BANKQRY");
    cnaps_get_string(fbfr, CNAPS_F_BANK_NO, bank_no_filter, sizeof(bank_no_filter));
    cnaps_get_string(fbfr, CNAPS_F_KEYWORD, keyword, sizeof(keyword));
    cnaps_get_string(fbfr, CNAPS_F_CITY, city_filter, sizeof(city_filter));
    cnaps_get_string(fbfr, CNAPS_F_SYSTEM_TYPE, system_type_filter, sizeof(system_type_filter));
    cnaps_get_long(fbfr, CNAPS_F_PAGE_NO, &page_no);
    cnaps_get_long(fbfr, CNAPS_F_PAGE_SIZE, &page_size);
    if (page_no <= 0) page_no = 1;
    if (page_size <= 0) page_size = 10;
    if (page_size > BANK_QUERY_MAX_PAGE_SIZE) page_size = BANK_QUERY_MAX_PAGE_SIZE;

    matches = (bank_no_filter[0] == '\0' || strcmp(bank_no_filter, bank_no) == 0)
        && (keyword[0] == '\0' || strstr(bank_name, keyword) != NULL)
        && (city_filter[0] == '\0' || strcmp(city_filter, city) == 0)
        && (system_type_filter[0] == '\0' || strcmp(system_type_filter, system_type) == 0);

    cnaps_put_long(fbfr, CNAPS_F_PAGE_NO, page_no);
    cnaps_put_long(fbfr, CNAPS_F_PAGE_SIZE, page_size);
    cnaps_put_long(fbfr, CNAPS_F_TOTAL_ELEMENTS, matches ? 1 : 0);
    if (matches && page_no == 1) {
        cnaps_put_string_occurrence(fbfr, CNAPS_F_BANK_NO, 0, bank_no);
        cnaps_put_string_occurrence(fbfr, CNAPS_F_BANK_NAME, 0, bank_name);
        cnaps_put_string_occurrence(fbfr, CNAPS_F_CITY, 0, city);
        cnaps_put_string_occurrence(fbfr, CNAPS_F_SYSTEM_TYPE, 0, system_type);
    }
    cnaps_return_response(rqst, 1, "0000", "query success");
}
