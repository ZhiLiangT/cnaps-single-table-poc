#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}
BASE_URL=${BASE_URL:-http://127.0.0.1:8080/ruisui-bank-sim}
CNAPS_WAIT_ATTEMPTS=${CNAPS_WAIT_ATTEMPTS:-30}
CNAPS_WAIT_INTERVAL=${CNAPS_WAIT_INTERVAL:-1}
ORACLE_UNIT=${ORACLE_UNIT:-oracle-xe-21c}
TOMCAT_UNIT=${TOMCAT_UNIT:-tomcat}
CURRENT_STAGE=initialization

export APP_HOME

on_exit() {
  exit_code=$?
  trap - EXIT
  if [ "$exit_code" -ne 0 ]; then
    echo "ERROR: CNAPS operation failed during stage: $CURRENT_STAGE" >&2
    echo "Inspect Tuxedo logs under $APP_HOME/logs/ULOG* and Tomcat with: sudo journalctl -u $TOMCAT_UNIT -n 100 --no-pager" >&2
  fi
  exit "$exit_code"
}
trap on_exit EXIT

log() {
  printf '[cnapsctl] %s\n' "$*"
}

as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

unit_is_active() {
  systemctl is-active "$1" >/dev/null 2>&1
}

port_is_listening() {
  ss -ltn 2>/dev/null | awk -v port=":$1" '
    $4 ~ port "$" { found = 1 }
    END { exit found ? 0 : 1 }
  '
}

wait_for_port() {
  port=$1
  label=$2
  attempt=1
  while [ "$attempt" -le "$CNAPS_WAIT_ATTEMPTS" ]; do
    if port_is_listening "$port"; then
      log "$label is listening on port $port"
      return 0
    fi
    sleep "$CNAPS_WAIT_INTERVAL"
    attempt=$((attempt + 1))
  done
  echo "Timed out waiting for $label on port $port" >&2
  return 1
}

tuxedo_is_running() {
  status_output=$("$SCRIPT_DIR/status-tuxedo.sh" 2>&1 || true)
  if printf '%s\n' "$status_output" | grep -q "No bulletin board exists"; then
    return 1
  fi
  printf '%s\n' "$status_output" | grep -Eq 'cnapspocsvr|TMMETADATA|JSL'
}

start_oracle() {
  CURRENT_STAGE="start Oracle XE"
  if unit_is_active "$ORACLE_UNIT"; then
    log "Oracle XE is already active"
  else
    log "Starting Oracle XE"
    as_root systemctl start "$ORACLE_UNIT"
  fi
  wait_for_port 1521 "Oracle listener"
}

stop_oracle() {
  CURRENT_STAGE="stop Oracle XE"
  if unit_is_active "$ORACLE_UNIT"; then
    log "Stopping Oracle XE"
    as_root systemctl stop "$ORACLE_UNIT"
  else
    log "Oracle XE is already stopped"
  fi
}

start_tuxedo() {
  CURRENT_STAGE="start Tuxedo"
  if tuxedo_is_running; then
    if port_is_listening 8000; then
      log "Tuxedo and Jolt are already active"
      return 0
    fi
    log "Tuxedo is partially active; restarting the domain"
    "$SCRIPT_DIR/stop-tuxedo.sh"
  fi
  log "Starting Tuxedo domain"
  "$SCRIPT_DIR/start-tuxedo.sh"
  wait_for_port 8000 "Jolt JSL"
}

stop_tuxedo() {
  CURRENT_STAGE="stop Tuxedo"
  if tuxedo_is_running; then
    log "Stopping Tuxedo domain"
    "$SCRIPT_DIR/stop-tuxedo.sh"
  else
    log "Tuxedo is already stopped"
  fi
}

start_tomcat() {
  CURRENT_STAGE="start Tomcat"
  if unit_is_active "$TOMCAT_UNIT"; then
    if port_is_listening 8080; then
      log "Tomcat is already active"
      return 0
    fi
    log "Tomcat service is active without port 8080; restarting it"
    as_root systemctl restart "$TOMCAT_UNIT"
  else
    log "Starting Tomcat"
    as_root systemctl start "$TOMCAT_UNIT"
  fi
  wait_for_port 8080 "Tomcat"
}

stop_tomcat() {
  CURRENT_STAGE="stop Tomcat"
  if unit_is_active "$TOMCAT_UNIT"; then
    log "Stopping Tomcat"
    as_root systemctl stop "$TOMCAT_UNIT"
  else
    log "Tomcat is already stopped"
  fi
}

fetch_health() {
  curl -fsS --max-time 5 "$BASE_URL/api/health"
}

response_is_healthy() {
  response=$1
  printf '%s\n' "$response" | grep -Eq '"respCode"[[:space:]]*:[[:space:]]*"0000"' &&
    printf '%s\n' "$response" | grep -Eq '"oracle"[[:space:]]*:[[:space:]]*"UP"' &&
    printf '%s\n' "$response" | grep -Eq '"tuxedo"[[:space:]]*:[[:space:]]*"UP"' &&
    printf '%s\n' "$response" | grep -Eq '"webfe"[[:space:]]*:[[:space:]]*"UP"'
}

health_once() {
  response=$(fetch_health) || return 1
  printf '%s\n' "$response"
  response_is_healthy "$response"
}

wait_for_health() {
  CURRENT_STAGE="verify application health"
  attempt=1
  while [ "$attempt" -le "$CNAPS_WAIT_ATTEMPTS" ]; do
    if response=$(fetch_health 2>/dev/null) && response_is_healthy "$response"; then
      printf '%s\n' "$response"
      log "Oracle, Tuxedo, and WebFE are UP"
      return 0
    fi
    sleep "$CNAPS_WAIT_INTERVAL"
    attempt=$((attempt + 1))
  done
  echo "Health endpoint did not become ready: $BASE_URL/api/health" >&2
  return 1
}

up() {
  start_oracle
  start_tuxedo
  start_tomcat
  wait_for_health
}

down() {
  stop_all=${1:-no}
  stop_tomcat
  stop_tuxedo
  if [ "$stop_all" = yes ]; then
    stop_oracle
  else
    log "Oracle XE remains active; use down --all to stop it"
  fi
}

show_status() {
  CURRENT_STAGE="inspect runtime status"
  printf 'Oracle XE: '
  systemctl is-active "$ORACLE_UNIT" || true
  printf 'Tomcat: '
  systemctl is-active "$TOMCAT_UNIT" || true
  printf 'Listening ports:\n'
  ss -ltn 2>/dev/null | grep -E ':(1521|8000|8080)[[:space:]]' || true
  printf 'Tuxedo services:\n'
  "$SCRIPT_DIR/status-tuxedo.sh" || true
  printf 'Health response:\n'
  health_once
}

show_logs() {
  CURRENT_STAGE="read runtime logs"
  latest_ulog=$(ls -1t "$APP_HOME"/logs/ULOG* 2>/dev/null | head -1 || true)
  if [ -n "$latest_ulog" ]; then
    log "Latest Tuxedo log: $latest_ulog"
    tail -n 100 "$latest_ulog"
  else
    log "No Tuxedo ULOG file found"
  fi
  log "Recent Tomcat journal"
  as_root journalctl -u "$TOMCAT_UNIT" -n 100 --no-pager
}

run_stage() {
  CURRENT_STAGE=$1
  shift
  log "$CURRENT_STAGE"
  "$@"
}

restore_selinux_labels() {
  if command -v getenforce >/dev/null 2>&1 &&
      [ "$(getenforce)" != Disabled ] &&
      command -v restorecon >/dev/null 2>&1; then
    as_root restorecon -R "$APP_HOME"
  fi
}

rebuild_deploy() {
  run_stage "preflight" "$SCRIPT_DIR/preflight.sh"
  stop_tomcat
  stop_tuxedo
  run_stage "build Tuxedo C services" "$SCRIPT_DIR/build-c.sh"
  run_stage "load Jolt metadata" "$SCRIPT_DIR/load-jolt-metadata.sh"
  run_stage "load TUXCONFIG" "$SCRIPT_DIR/load-tuxconfig.sh"
  run_stage "build WebFE WAR" "$SCRIPT_DIR/build-web.sh"
  run_stage "deploy WebFE WAR" "$SCRIPT_DIR/deploy-web.sh"
  run_stage "restore SELinux labels" restore_selinux_labels
  up
}

usage() {
  cat <<'USAGE'
Usage: ./scripts/cnapsctl.sh COMMAND

Commands:
  up                 Start Oracle XE, Tuxedo/Jolt, and Tomcat
  down [--all]       Stop Tomcat and Tuxedo; --all also stops Oracle XE
  restart            Restart the application stack
  status             Show service, port, Tuxedo, and health status
  health             Verify the HTTP health response
  logs               Show recent Tuxedo and Tomcat logs
  rebuild-deploy     Build, deploy, start, and verify the complete stack
  install-autostart  Install and enable systemd boot integration
  tuxedo-up          Start only Tuxedo/Jolt (used by systemd)
  tuxedo-down        Stop only Tuxedo/Jolt (used by systemd)
USAGE
}

command_name=${1:-}
case "$command_name" in
  up)
    up
    ;;
  down)
    case "${2:-}" in
      "") down no ;;
      --all) down yes ;;
      *) usage >&2; exit 2 ;;
    esac
    ;;
  restart)
    down no
    up
    ;;
  status)
    show_status
    ;;
  health)
    CURRENT_STAGE="verify application health"
    health_once
    ;;
  logs)
    show_logs
    ;;
  rebuild-deploy)
    rebuild_deploy
    ;;
  install-autostart)
    CURRENT_STAGE="install systemd automation"
    "$SCRIPT_DIR/install-systemd.sh"
    ;;
  tuxedo-up)
    start_tuxedo
    ;;
  tuxedo-down)
    stop_tuxedo
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
