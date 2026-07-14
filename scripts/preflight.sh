#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/tuxedo.env"
. "$APP_HOME/conf/db.env"

if [ -z "${TOMCAT_HOME:-}" ]; then
  if [ -d /var/lib/tomcat/webapps ]; then
    TOMCAT_HOME=/var/lib/tomcat
  else
    TOMCAT_HOME=/opt/tomcat
  fi
fi

missing=0
require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    missing=1
  fi
}

require_path() {
  if [ ! -e "$1" ]; then
    echo "missing path: $1" >&2
    missing=1
  fi
}

require_cmd java
require_cmd mvn
require_cmd gcc
require_cmd make
require_cmd sqlplus
require_cmd tmadmin
require_cmd tmloadcf
require_cmd buildserver

require_path "$ORACLE_HOME"
require_path "$ORACLE_HOME/lib"
require_path "$ORACLE_BUILD_HOME/sdk/include"
require_path "$TUXDIR"
require_path "$APP_HOME/tuxedo/UBBCONFIG"
require_path "$APP_HOME/tuxedo/jolt/cnaps_services.bulk"
require_path "$APP_HOME/tuxedo-server/fml/cnaps_poc.fml32"
require_path "$TOMCAT_HOME/webapps"

echo "APP_HOME=$APP_HOME"
echo "ORACLE_CONNECT_STRING=$ORACLE_CONNECT_STRING"
echo "TUXDIR=$TUXDIR"
echo "TUXCONFIG=$TUXCONFIG"
echo "JOLT_LISTEN=$JOLT_LISTEN"
echo "TOMCAT_HOME=$TOMCAT_HOME"

if [ "$missing" -ne 0 ]; then
  exit 1
fi

echo "preflight ok"
