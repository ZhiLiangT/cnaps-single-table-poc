#!/usr/bin/env sh

export APP_HOME=${APP_HOME:-/opt/ruisui-bank-sim}
export TUXDIR=${TUXDIR:-/opt/tuxedo}
export ORACLE_HOME=${ORACLE_HOME:-/opt/oracle/product/21c/dbhomeXE}
export ORACLE_BUILD_HOME=${ORACLE_BUILD_HOME:-/opt/oracle/instantclient}
export ORACLE_HOST=${ORACLE_HOST:-127.0.0.1}
export ORACLE_PORT=${ORACLE_PORT:-1521}
export ORACLE_SERVICE=${ORACLE_SERVICE:-XEPDB1}
export ORACLE_CONNECT_STRING=${ORACLE_CONNECT_STRING:-//${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SERVICE}}
export ORA_NLS10=${ORA_NLS10:-$ORACLE_HOME/nls/data}
export TUXCONFIG=${TUXCONFIG:-$APP_HOME/tuxedo/tuxconfig}
export FLDTBLDIR32=${FLDTBLDIR32:-$APP_HOME/tuxedo-server/fml}
export FIELDTBLS32=${FIELDTBLS32:-cnaps_poc.fml32}
export PATH=$ORACLE_HOME/bin:${PATH:-}
export LD_LIBRARY_PATH=$ORACLE_HOME/lib:$APP_HOME/lib:$TUXDIR/lib:${LD_LIBRARY_PATH:-}
