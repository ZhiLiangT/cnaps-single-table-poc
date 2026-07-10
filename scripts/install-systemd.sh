#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}
SYSTEMD_ROOT=${SYSTEMD_ROOT:-/etc/systemd/system}
RUN_USER=${RUN_USER:-$(id -un)}
TEMPLATE_DIR=$APP_HOME/ops/systemd
TUXEDO_TEMPLATE=$TEMPLATE_DIR/cnaps-tuxedo.service.in
TOMCAT_TEMPLATE=$TEMPLATE_DIR/tomcat-cnaps.conf
TUXEDO_UNIT=$SYSTEMD_ROOT/cnaps-tuxedo.service
TOMCAT_DROPIN_DIR=$SYSTEMD_ROOT/tomcat.service.d
TOMCAT_DROPIN=$TOMCAT_DROPIN_DIR/cnaps.conf

as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

require_file() {
  if [ ! -f "$1" ]; then
    echo "Required systemd template not found: $1" >&2
    exit 1
  fi
}

escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[&|]/\\&/g'
}

set_fcontext() {
  selinux_type=$1
  path_pattern=$2
  if ! as_root semanage fcontext -a -t "$selinux_type" "$path_pattern" 2>/dev/null; then
    as_root semanage fcontext -m -t "$selinux_type" "$path_pattern"
  fi
}

configure_selinux() {
  if ! command -v getenforce >/dev/null 2>&1 || [ "$(getenforce)" = Disabled ]; then
    return 0
  fi
  if ! command -v semanage >/dev/null 2>&1; then
    echo "SELinux is enabled but semanage is unavailable; install policycoreutils-python-utils" >&2
    exit 1
  fi

  set_fcontext usr_t "$APP_HOME(/.*)?"
  set_fcontext bin_t "$APP_HOME/scripts(/.*)?"
  set_fcontext bin_t "$APP_HOME/tuxedo-server/bin(/.*)?"
  as_root restorecon -R "$APP_HOME"
}

require_file "$TUXEDO_TEMPLATE"
require_file "$TOMCAT_TEMPLATE"

rendered_unit=$(mktemp)
trap 'rm -f "$rendered_unit"' EXIT HUP INT TERM

escaped_app_home=$(escape_sed_replacement "$APP_HOME")
escaped_run_user=$(escape_sed_replacement "$RUN_USER")
sed \
  -e "s|@APP_HOME@|$escaped_app_home|g" \
  -e "s|@RUN_USER@|$escaped_run_user|g" \
  "$TUXEDO_TEMPLATE" > "$rendered_unit"

as_root install -d -m 0755 "$SYSTEMD_ROOT" "$TOMCAT_DROPIN_DIR"
as_root install -m 0644 "$rendered_unit" "$TUXEDO_UNIT"
as_root install -m 0644 "$TOMCAT_TEMPLATE" "$TOMCAT_DROPIN"
configure_selinux
as_root systemctl daemon-reload
as_root systemctl enable oracle-xe-21c cnaps-tuxedo tomcat

echo "Installed CNAPS systemd automation:"
echo "  $TUXEDO_UNIT"
echo "  $TOMCAT_DROPIN"
echo "Run '$APP_HOME/scripts/up.sh' now or reboot the VM to verify startup."
