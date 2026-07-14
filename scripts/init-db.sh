#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/db.env"

echo "Initializing Oracle schema $ORACLE_USER at $ORACLE_CONNECT_STRING"
run_sql_file() {
  sqlplus -L "$ORACLE_USER/$ORACLE_PASSWORD@$ORACLE_CONNECT_STRING" <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
@$1
EXIT
SQL
}

run_sql_file "$APP_HOME/sql/010_create_tables.sql"
run_sql_file "$APP_HOME/sql/040_add_party_address_bank_fields.sql"
run_sql_file "$APP_HOME/sql/050_enable_voucher_review.sql"
run_sql_file "$APP_HOME/sql/020_create_indexes.sql"
run_sql_file "$APP_HOME/sql/030_seed_reference_data.sql"
