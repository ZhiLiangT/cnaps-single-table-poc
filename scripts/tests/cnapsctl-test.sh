#!/usr/bin/env sh
set -eu

TEST_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$TEST_DIR/../.." && pwd)
SOURCE_CONTROLLER=$PROJECT_ROOT/scripts/cnapsctl.sh

if [ ! -f "$SOURCE_CONTROLLER" ]; then
  echo "FAIL: scripts/cnapsctl.sh does not exist" >&2
  exit 1
fi

TMP_ROOT=$(mktemp -d)
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

FIXTURE_ROOT=$TMP_ROOT/project
FIXTURE_SCRIPTS=$FIXTURE_ROOT/scripts
FAKE_BIN=$TMP_ROOT/bin
STATE_DIR=$TMP_ROOT/state
LOG_FILE=$TMP_ROOT/commands.log

mkdir -p "$FIXTURE_SCRIPTS" "$FAKE_BIN" "$STATE_DIR" "$FIXTURE_ROOT/logs"
cp "$SOURCE_CONTROLLER" "$FIXTURE_SCRIPTS/cnapsctl.sh"
chmod +x "$FIXTURE_SCRIPTS/cnapsctl.sh"
: > "$LOG_FILE"
: > "$STATE_DIR/active-services"

for script_name in \
  preflight.sh build-c.sh load-jolt-metadata.sh load-tuxconfig.sh \
  build-web.sh deploy-web.sh start-tuxedo.sh stop-tuxedo.sh \
  status-tuxedo.sh install-systemd.sh; do
  cat > "$FIXTURE_SCRIPTS/$script_name" <<'STUB'
#!/usr/bin/env sh
set -eu
name=$(basename "$0")
short_name=${name%.sh}
echo "script $short_name" >> "$CNAPS_TEST_LOG"

if [ "${FAIL_STAGE:-}" = "$short_name" ]; then
  exit 23
fi

case "$name" in
  start-tuxedo.sh)
    : > "$CNAPS_TEST_STATE/tuxedo-active"
    ;;
  stop-tuxedo.sh)
    rm -f "$CNAPS_TEST_STATE/tuxedo-active"
    ;;
  status-tuxedo.sh)
    if [ -f "$CNAPS_TEST_STATE/tuxedo-active" ]; then
      echo "cnapspocsvr TMMETADATA JSL"
    else
      echo "No bulletin board exists"
    fi
    ;;
esac
STUB
  chmod +x "$FIXTURE_SCRIPTS/$script_name"
done

cat > "$FAKE_BIN/sudo" <<'STUB'
#!/usr/bin/env sh
set -eu
echo "sudo $*" >> "$CNAPS_TEST_LOG"
exec "$@"
STUB

cat > "$FAKE_BIN/systemctl" <<'STUB'
#!/usr/bin/env sh
set -eu
command_name=${1:-}
unit=${2:-}
active_file=$CNAPS_TEST_STATE/active-services

case "$command_name" in
  is-active)
    grep -Fqx "$unit" "$active_file"
    ;;
  start|restart)
    echo "systemctl $command_name $unit" >> "$CNAPS_TEST_LOG"
    if ! grep -Fqx "$unit" "$active_file"; then
      echo "$unit" >> "$active_file"
    fi
    ;;
  stop)
    echo "systemctl stop $unit" >> "$CNAPS_TEST_LOG"
    grep -Fvx "$unit" "$active_file" > "$active_file.next" || true
    mv "$active_file.next" "$active_file"
    ;;
  *)
    echo "systemctl $*" >> "$CNAPS_TEST_LOG"
    ;;
esac
STUB

cat > "$FAKE_BIN/ss" <<'STUB'
#!/usr/bin/env sh
set -eu
active_file=$CNAPS_TEST_STATE/active-services
if grep -Fqx oracle-xe-21c "$active_file"; then
  echo "LISTEN 0 128 *:1521 *:*"
fi
if [ -f "$CNAPS_TEST_STATE/tuxedo-active" ]; then
  echo "LISTEN 0 128 127.0.0.1:8000 *:*"
fi
if grep -Fqx tomcat "$active_file"; then
  echo "LISTEN 0 100 *:8080 *:*"
fi
STUB

cat > "$FAKE_BIN/curl" <<'STUB'
#!/usr/bin/env sh
set -eu
echo "curl health" >> "$CNAPS_TEST_LOG"
if [ "${FAIL_HEALTH:-0}" = 1 ]; then
  exit 22
fi
active_file=$CNAPS_TEST_STATE/active-services
grep -Fqx oracle-xe-21c "$active_file"
grep -Fqx tomcat "$active_file"
test -f "$CNAPS_TEST_STATE/tuxedo-active"
printf '%s\n' '{"respCode":"0000","respMsg":"ok","data":{"oracle":"UP","tuxedo":"UP","webfe":"UP"}}'
STUB

cat > "$FAKE_BIN/journalctl" <<'STUB'
#!/usr/bin/env sh
echo "tomcat journal"
STUB

cat > "$FAKE_BIN/sleep" <<'STUB'
#!/usr/bin/env sh
exit 0
STUB

cat > "$FAKE_BIN/getenforce" <<'STUB'
#!/usr/bin/env sh
echo Enforcing
STUB

cat > "$FAKE_BIN/restorecon" <<'STUB'
#!/usr/bin/env sh
echo "restorecon $*" >> "$CNAPS_TEST_LOG"
STUB

chmod +x "$FAKE_BIN"/*

export APP_HOME=$FIXTURE_ROOT
export CNAPS_TEST_LOG=$LOG_FILE
export CNAPS_TEST_STATE=$STATE_DIR
export PATH=$FAKE_BIN:$PATH
export CNAPS_WAIT_ATTEMPTS=2
export CNAPS_WAIT_INTERVAL=0

reset_fixture() {
  : > "$LOG_FILE"
  : > "$STATE_DIR/active-services"
  rm -f "$STATE_DIR/tuxedo-active"
}

activate_all() {
  printf '%s\n' oracle-xe-21c tomcat > "$STATE_DIR/active-services"
  : > "$STATE_DIR/tuxedo-active"
}

assert_logged() {
  if ! grep -Fq "$1" "$LOG_FILE"; then
    echo "FAIL: expected log entry: $1" >&2
    cat "$LOG_FILE" >&2
    exit 1
  fi
}

assert_not_logged() {
  if grep -Fq "$1" "$LOG_FILE"; then
    echo "FAIL: unexpected log entry: $1" >&2
    cat "$LOG_FILE" >&2
    exit 1
  fi
}

assert_order() {
  first_line=$(grep -nF "$1" "$LOG_FILE" | head -1 | cut -d: -f1)
  second_line=$(grep -nF "$2" "$LOG_FILE" | head -1 | cut -d: -f1)
  third_line=$(grep -nF "$3" "$LOG_FILE" | head -1 | cut -d: -f1)
  if [ "$first_line" -ge "$second_line" ] || [ "$second_line" -ge "$third_line" ]; then
    echo "FAIL: expected order: $1 -> $2 -> $3" >&2
    cat "$LOG_FILE" >&2
    exit 1
  fi
}

run_ctl() {
  sh "$FIXTURE_SCRIPTS/cnapsctl.sh" "$@"
}

reset_fixture
run_ctl up >/dev/null
assert_order "systemctl start oracle-xe-21c" "script start-tuxedo" "systemctl start tomcat"
assert_logged "curl health"

: > "$LOG_FILE"
run_ctl up >/dev/null
assert_not_logged "systemctl start oracle-xe-21c"
assert_not_logged "script start-tuxedo"
assert_not_logged "systemctl start tomcat"
assert_logged "curl health"

: > "$LOG_FILE"
run_ctl down --all >/dev/null
assert_order "systemctl stop tomcat" "script stop-tuxedo" "systemctl stop oracle-xe-21c"

activate_all
: > "$LOG_FILE"
if FAIL_HEALTH=1 run_ctl health >/dev/null 2>&1; then
  echo "FAIL: health command accepted a failed HTTP call" >&2
  exit 1
fi

activate_all
: > "$LOG_FILE"
if FAIL_STAGE=build-c run_ctl rebuild-deploy >/dev/null 2>&1; then
  echo "FAIL: rebuild-deploy ignored a failed build stage" >&2
  exit 1
fi
assert_logged "script build-c"
assert_not_logged "script load-jolt-metadata"

unset FAIL_STAGE
unset FAIL_HEALTH
reset_fixture
run_ctl rebuild-deploy >/dev/null
assert_logged "restorecon -R $FIXTURE_ROOT"
assert_order "script build-web" "restorecon -R $FIXTURE_ROOT" "script start-tuxedo"

echo "PASS: cnapsctl lifecycle contract"
