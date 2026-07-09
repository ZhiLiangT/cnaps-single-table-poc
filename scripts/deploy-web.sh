#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

default_tomcat_home() {
  if [ -d /var/lib/tomcat/webapps ]; then
    echo /var/lib/tomcat
  else
    echo /opt/tomcat
  fi
}

TOMCAT_HOME=${TOMCAT_HOME:-$(default_tomcat_home)}

echo "Deploying ruisui-bank-sim.war to $TOMCAT_HOME"
if [ -w "$TOMCAT_HOME/webapps" ]; then
  install -m 0644 "$APP_HOME/web-fe/target/ruisui-bank-sim.war" "$TOMCAT_HOME/webapps/ruisui-bank-sim.war"
else
  sudo install -m 0644 "$APP_HOME/web-fe/target/ruisui-bank-sim.war" "$TOMCAT_HOME/webapps/ruisui-bank-sim.war"
fi
