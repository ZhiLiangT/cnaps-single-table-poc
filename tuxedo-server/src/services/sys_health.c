#include "cnaps_db.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"

void SYSHEALTH(TPSVCINFO *rqst)
{
    FBFR32 *fbfr = (FBFR32 *)rqst->data;
    int oracle_up;

    cnaps_log_service_start("SYSHEALTH");
    oracle_up = db_ping() == 0;
    cnaps_put_string(fbfr, CNAPS_F_WEBFE, "UP");
    cnaps_put_string(fbfr, CNAPS_F_TUXEDO, "UP");
    cnaps_put_string(fbfr, CNAPS_F_ORACLE, oracle_up ? "UP" : "DOWN");
    cnaps_put_string(fbfr, CNAPS_F_SERVICE, "SYSHEALTH");
    cnaps_put_string(fbfr, CNAPS_F_CHECK_TIME, "");
    cnaps_return_response(
        rqst,
        1,
        "0000",
        oracle_up ? "health check success" : "Oracle unavailable"
    );
}
