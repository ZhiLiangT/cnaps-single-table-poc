#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/tuxedo.env"
. "$APP_HOME/conf/db.env"

echo "Building Tuxedo C services under $APP_HOME"
make -C "$APP_HOME/tuxedo-server" clean all ORACLE_HOME="$ORACLE_BUILD_HOME"
