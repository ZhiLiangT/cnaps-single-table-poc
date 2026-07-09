#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

echo "Building WebFE WAR under $APP_HOME"
mvn -f "$APP_HOME/web-fe/pom.xml" clean package
