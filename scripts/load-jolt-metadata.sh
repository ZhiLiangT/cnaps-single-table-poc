#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}

. "$APP_HOME/conf/env.linux.sh"
. "$APP_HOME/conf/tuxedo.env"

METADATA_FILE=${METADATA_FILE:-$APP_HOME/tuxedo/jolt/cnaps_services.bulk}
FML_TABLE=${FML_TABLE:-$APP_HOME/tuxedo-server/fml/$FIELDTBLS32}
METAREPOS_FILE=${METAREPOS_FILE:-$APP_HOME/tuxedo/jolt/cnaps.metarepos}

if [ ! -f "$METADATA_FILE" ]; then
  echo "Jolt metadata file not found: $METADATA_FILE" >&2
  exit 1
fi

if [ ! -f "$FML_TABLE" ]; then
  echo "FML32 field table not found: $FML_TABLE" >&2
  exit 1
fi

JOLT_REPOSITORY=$(mktemp)
METADATA_INPUT=$(mktemp)
trap 'rm -f "$JOLT_REPOSITORY" "$METADATA_INPUT"' EXIT HUP INT TERM

awk '
  function trim(value) {
    gsub(/^[ \t]+|[ \t]+$/, "", value)
    return value
  }
  function fldid(type, number) {
    return (type == "long" || type == "integer") ? 33554432 + number : 167772160 + number
  }
  function jolt_type(type) {
    return (type == "long" || type == "integer") ? "integer" : "string"
  }
  function jolt_access(value) {
    if (value == "in") {
      return "wr"
    }
    if (value == "out") {
      return "rd"
    }
    return "rw"
  }
  function add_service_field(name, access) {
    if (name in current_seen) {
      return
    }
    field_count++
    service_fields[field_count] = name
    service_access[field_count] = access
    current_seen[name] = 1
  }
  function flush_service(   i, name) {
    if (service_name == "") {
      return
    }
    add_service_field("REQUEST_ID", "rw")
    add_service_field("REQ_ID", "rw")
    add_service_field("OPERATOR_NO", "rw")
    add_service_field("BRANCH_NO", "rw")
    add_service_field("WORK_DATE", "rw")
    printf "add SVC/%s:vs=1:ex=1:bt=FML32", service_name
    for (i = 1; i <= field_count; i++) {
      name = service_fields[i]
      if (!(name in field_ids)) {
        printf "Unknown FML32 field in %s: %s\n", service_name, name > "/dev/stderr"
        missing = 1
        continue
      }
      printf ":\\\n"
      printf "bp:pn=%s:pt=%s:pf=%s:pa=%s:ep", name, field_types[name], field_ids[name], service_access[i]
    }
    printf ":\n"
  }
  BEGIN {
    print "#!JOLT1.0"
  }
  FILENAME == ARGV[1] {
    if ($0 ~ /^[ \t]*#/ || NF < 3) {
      next
    }
    field_ids[$1] = fldid($3, $2)
    field_types[$1] = jolt_type($3)
    next
  }
  FILENAME == ARGV[2] {
    line = trim($0)
    if (line == "" || line ~ /^#/) {
      next
    }
    separator = index(line, "=")
    if (separator == 0) {
      next
    }
    key = substr(line, 1, separator - 1)
    value = substr(line, separator + 1)
    if (key == "service") {
      flush_service()
      service_name = value
      field_count = 0
      delete current_seen
      next
    }
    if (key == "param") {
      add_service_field(value, "rw")
      next
    }
    if (key == "access" && field_count > 0) {
      service_access[field_count] = jolt_access(value)
      next
    }
  }
  END {
    flush_service()
    if (missing) {
      exit 1
    }
  }
' "$FML_TABLE" "$METADATA_FILE" > "$JOLT_REPOSITORY"

mkdir -p "$(dirname "$METAREPOS_FILE")"
tmunloadrepos "$JOLT_REPOSITORY" > "$METADATA_INPUT"
tmloadrepos -y -i "$METADATA_INPUT" "$METAREPOS_FILE"

echo "Loaded Jolt metadata repository $METAREPOS_FILE from $METADATA_FILE"
