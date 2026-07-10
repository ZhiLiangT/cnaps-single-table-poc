#!/usr/bin/env sh
set -eu

TEST_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$TEST_DIR/../.." && pwd)
INSTALLER=$PROJECT_ROOT/scripts/install-systemd.sh

if [ ! -f "$INSTALLER" ]; then
  echo "FAIL: scripts/install-systemd.sh does not exist" >&2
  exit 1
fi

TMP_ROOT=$(mktemp -d)
trap 'rm -rf "$TMP_ROOT"' EXIT HUP INT TERM

FAKE_BIN=$TMP_ROOT/bin
SYSTEMD_ROOT=$TMP_ROOT/systemd
LOG_FILE=$TMP_ROOT/commands.log
mkdir -p "$FAKE_BIN" "$SYSTEMD_ROOT"
: > "$LOG_FILE"

cat > "$FAKE_BIN/sudo" <<'STUB'
#!/usr/bin/env sh
set -eu
echo "sudo $*" >> "$CNAPS_TEST_LOG"
exec "$@"
STUB

cat > "$FAKE_BIN/systemctl" <<'STUB'
#!/usr/bin/env sh
set -eu
echo "systemctl $*" >> "$CNAPS_TEST_LOG"
STUB

chmod +x "$FAKE_BIN"/*

export PATH=$FAKE_BIN:$PATH
export CNAPS_TEST_LOG=$LOG_FILE
export SYSTEMD_ROOT
export RUN_USER=tian

run_installer() {
  sh "$INSTALLER"
}

assert_file_contains() {
  file=$1
  expected=$2
  if ! grep -Fq "$expected" "$file"; then
    echo "FAIL: $file does not contain: $expected" >&2
    cat "$file" >&2
    exit 1
  fi
}

assert_logged() {
  if ! grep -Fq "$1" "$LOG_FILE"; then
    echo "FAIL: expected log entry: $1" >&2
    cat "$LOG_FILE" >&2
    exit 1
  fi
}

run_installer >/dev/null

TUXEDO_UNIT=$SYSTEMD_ROOT/cnaps-tuxedo.service
TOMCAT_DROPIN=$SYSTEMD_ROOT/tomcat.service.d/cnaps.conf

test -f "$TUXEDO_UNIT"
test -f "$TOMCAT_DROPIN"
assert_file_contains "$TUXEDO_UNIT" "User=tian"
assert_file_contains "$TUXEDO_UNIT" "Requires=oracle-xe-21c.service"
assert_file_contains "$TUXEDO_UNIT" "SELinuxContext=unconfined_u:unconfined_r:unconfined_t:s0"
assert_file_contains "$TUXEDO_UNIT" "ExecStart=$PROJECT_ROOT/scripts/cnapsctl.sh tuxedo-up"
assert_file_contains "$TUXEDO_UNIT" "ExecStop=$PROJECT_ROOT/scripts/cnapsctl.sh tuxedo-down"
assert_file_contains "$TOMCAT_DROPIN" "Requires=cnaps-tuxedo.service"
assert_file_contains "$TOMCAT_DROPIN" "After=cnaps-tuxedo.service"

if grep -Eq '@APP_HOME@|@RUN_USER@' "$TUXEDO_UNIT"; then
  echo "FAIL: unresolved systemd template placeholder" >&2
  exit 1
fi

assert_logged "systemctl daemon-reload"
assert_logged "systemctl enable oracle-xe-21c cnaps-tuxedo tomcat"

first_checksum=$(cksum "$TUXEDO_UNIT" "$TOMCAT_DROPIN")
: > "$LOG_FILE"
run_installer >/dev/null
second_checksum=$(cksum "$TUXEDO_UNIT" "$TOMCAT_DROPIN")

if [ "$first_checksum" != "$second_checksum" ]; then
  echo "FAIL: repeated installation changed rendered systemd files" >&2
  exit 1
fi
assert_logged "systemctl daemon-reload"

echo "PASS: systemd installation contract"
