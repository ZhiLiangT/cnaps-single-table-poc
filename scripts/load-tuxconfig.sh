#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/tuxedo.env"
export APP_HOME TUXCONFIG TUXDIR ULOGPFX

mkdir -p "$(dirname "$TUXCONFIG")" "$APP_HOME/logs"

UBB_SOURCE=$APP_HOME/tuxedo/UBBCONFIG
UBB_RENDERED=$(mktemp)
trap 'rm -f "$UBB_RENDERED"' EXIT HUP INT TERM

awk '
  {
    gsub(/\$\{APP_HOME\}/, ENVIRON["APP_HOME"])
    gsub(/\$\{TUXCONFIG\}/, ENVIRON["TUXCONFIG"])
    gsub(/\$\{TUXDIR\}/, ENVIRON["TUXDIR"])
    gsub(/\$\{ULOGPFX\}/, ENVIRON["ULOGPFX"])
    print
  }
' "$UBB_SOURCE" > "$UBB_RENDERED"

echo "Loading Tuxedo configuration from $UBB_SOURCE into $TUXCONFIG"
tmloadcf -y "$UBB_RENDERED"
