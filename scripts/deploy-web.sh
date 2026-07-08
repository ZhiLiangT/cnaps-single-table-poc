#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}
TOMCAT_HOME=${TOMCAT_HOME:-/opt/tomcat}

echo "Deploying cnaps-web.war to $TOMCAT_HOME"
cp "$APP_HOME/web-fe/target/cnaps-web.war" "$TOMCAT_HOME/webapps/cnaps-web.war"
