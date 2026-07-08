#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/db.env"

echo "Initializing Oracle schema $ORACLE_USER at $ORACLE_CONNECT_STRING"
sqlplus "$ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_CONNECT_STRING" @"$APP_HOME/sql/010_create_tables.sql"
sqlplus "$ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_CONNECT_STRING" @"$APP_HOME/sql/020_create_indexes.sql"
sqlplus "$ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_CONNECT_STRING" @"$APP_HOME/sql/030_seed_reference_data.sql"
