#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"

echo "Building CNAPS Tuxedo server in $APP_HOME"
make -C "$APP_HOME/tuxedo-server" clean all
