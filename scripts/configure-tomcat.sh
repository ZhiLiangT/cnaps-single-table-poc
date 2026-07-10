#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}
TOMCAT_SYSCONFIG=${TOMCAT_SYSCONFIG:-/etc/sysconfig/tomcat}
TOMCAT_LIB=${TOMCAT_LIB:-/usr/share/tomcat/lib}
WEBFE_TUXEDO_CLIENT_MODE=${WEBFE_TUXEDO_CLIENT_MODE:-jolt}
WEBFE_TUXEDO_JOLT_LISTEN=${WEBFE_TUXEDO_JOLT_LISTEN:-//127.0.0.1:8000}
POC_OPERATOR_NO=${POC_OPERATOR_NO:-77210021}
POC_BRANCH_NO=${POC_BRANCH_NO:-772}
TM_ALLOW_NOTLS=${TM_ALLOW_NOTLS:-Y}

export APP_HOME
if [ -f "$APP_HOME/conf/tuxedo.env" ]; then
  # shellcheck disable=SC1091
  . "$APP_HOME/conf/tuxedo.env"
fi

find_jolt_jar() {
  if [ -n "${JOLT_JAR:-}" ]; then
    echo "$JOLT_JAR"
    return
  fi
  for candidate in \
    "${TUXDIR:-/opt/tuxedo}/udataobj/jolt/jolt.jar" \
    "${TUXDIR:-/opt/tuxedo}/udataobj/jolt/joltjse.jar" \
    "${TUXDIR:-/opt/tuxedo}/jolt/lib/jolt.jar"; do
    if [ -r "$candidate" ]; then
      echo "$candidate"
      return
    fi
  done
}

JOLT_JAR_PATH=$(find_jolt_jar || true)
if [ -n "$JOLT_JAR_PATH" ]; then
  echo "Installing Jolt runtime jar into $TOMCAT_LIB"
  sudo install -m 0644 "$JOLT_JAR_PATH" "$TOMCAT_LIB/$(basename "$JOLT_JAR_PATH")"
else
  echo "Jolt runtime jar not found yet; Tomcat will report jolt class errors until Tuxedo is installed" >&2
fi

tmp=$(mktemp)
if [ -f "$TOMCAT_SYSCONFIG" ]; then
  awk '
    /^# CNAPS POC begin$/ { skip = 1; next }
    /^# CNAPS POC end$/ { skip = 0; next }
    skip != 1 { print }
  ' "$TOMCAT_SYSCONFIG" > "$tmp"
else
  : > "$tmp"
fi

{
  echo "# CNAPS POC begin"
  echo "APP_HOME=\"$APP_HOME\""
  echo "WEBFE_TUXEDO_CLIENT_MODE=\"$WEBFE_TUXEDO_CLIENT_MODE\""
  echo "WEBFE_TUXEDO_JOLT_LISTEN=\"$WEBFE_TUXEDO_JOLT_LISTEN\""
  echo "POC_OPERATOR_NO=\"$POC_OPERATOR_NO\""
  echo "POC_BRANCH_NO=\"$POC_BRANCH_NO\""
  echo "TM_ALLOW_NOTLS=\"$TM_ALLOW_NOTLS\""
  echo "JAVA_OPTS=\"-Dapp.home=$APP_HOME -Dwebfe.poc.operatorNo=$POC_OPERATOR_NO -Dwebfe.poc.branchNo=$POC_BRANCH_NO -Dwebfe.tuxedo.client.mode=$WEBFE_TUXEDO_CLIENT_MODE -Dwebfe.tuxedo.jolt.listen=$WEBFE_TUXEDO_JOLT_LISTEN -DTM_ALLOW_NOTLS=$TM_ALLOW_NOTLS\""
  echo "# CNAPS POC end"
} >> "$tmp"

sudo install -m 0644 "$tmp" "$TOMCAT_SYSCONFIG"
rm -f "$tmp"
echo "Configured $TOMCAT_SYSCONFIG for APP_HOME=$APP_HOME, mode=$WEBFE_TUXEDO_CLIENT_MODE, operator=$POC_OPERATOR_NO, and branch=$POC_BRANCH_NO"
