#include <ctype.h>
#include <string.h>
#include "cnaps_status.h"

int cnaps_status_can_edit(const char *status)
{
    return strcmp(status, CNAPS_STATUS_DRAFT) == 0;
}

int cnaps_valid_work_date(const char *value)
{
    static const int days_by_month[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    int year;
    int month;
    int day;
    int max_day;

    if (value == NULL || strlen(value) != 10 || value[4] != '-' || value[7] != '-') {
        return 0;
    }
    for (int i = 0; i < 10; ++i) {
        if (i != 4 && i != 7 && !isdigit((unsigned char)value[i])) {
            return 0;
        }
    }
    year = (value[0] - '0') * 1000 + (value[1] - '0') * 100 + (value[2] - '0') * 10 + value[3] - '0';
    month = (value[5] - '0') * 10 + value[6] - '0';
    day = (value[8] - '0') * 10 + value[9] - '0';
    if (year == 0 || month < 1 || month > 12) {
        return 0;
    }
    max_day = days_by_month[month - 1];
    if (month == 2 && (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0))) {
        max_day = 29;
    }
    return day >= 1 && day <= max_day;
}

int cnaps_valid_optional_text(const char *value, size_t max_characters)
{
    size_t characters = 0;
    const unsigned char *cursor = (const unsigned char *)value;

    if (value == NULL) {
        return 1;
    }
    while (*cursor != '\0') {
        if ((*cursor & 0xC0U) != 0x80U && ++characters > max_characters) {
            return 0;
        }
        ++cursor;
    }
    return 1;
}
