#include <atmi.h>
#include "cnaps_db.h"
#include "cnaps_service.h"

void SYSHEALTH(TPSVCINFO *rqst);
void DICTQRY(TPSVCINFO *rqst);
void BANKQRY(TPSVCINFO *rqst);
void CNAPS5701E(TPSVCINFO *rqst);
void CNAPS5701U(TPSVCINFO *rqst);
void CNAPS5701D(TPSVCINFO *rqst);
void CNAPS4609Q(TPSVCINFO *rqst);
void CNAPS5702Q(TPSVCINFO *rqst);
void CNAPS5702I(TPSVCINFO *rqst);
void CNAPS5702A(TPSVCINFO *rqst);
void CNAPS5702R(TPSVCINFO *rqst);

int tpsvrinit(int argc, char **argv)
{
    (void)argc;
    (void)argv;
    userlog("cnapspocsvr initialized");
    if (db_connect() != 0) {
        userlog("Oracle connection is not available during startup; services will report Oracle DOWN until db.env points to a reachable database");
    }
    return 0;
}

void tpsvrdone(void)
{
    db_disconnect();
    userlog("cnapspocsvr stopped");
}
