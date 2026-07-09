#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/tuxedo.env"

echo "Checking Tuxedo services with tmadmin"
printf "psr\npsc\nquit\n" | tmadmin
