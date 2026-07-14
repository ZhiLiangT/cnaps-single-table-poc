#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/db.env"

echo "Migrating voucher statuses in Oracle schema $ORACLE_USER at $ORACLE_CONNECT_STRING"
sqlplus -L "$ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_CONNECT_STRING" <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
@$APP_HOME/sql/050_enable_voucher_review.sql
EXIT
SQL
