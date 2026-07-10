# CNAPS Runtime Automation Design

## Goal

Provide a repeatable operational interface for the VM deployment at
`/home/tian/cnaps-single-table-poc`. Operators should be able to rebuild,
deploy, start, stop, inspect, and verify the Oracle XE -> Tuxedo/Jolt -> Tomcat
stack without manually replaying the individual project scripts.

The automation must preserve the VM-local `conf/db.env`, must not initialize
or recreate database objects during normal operation, and must fail visibly
when any required service or health check is unavailable.

## Selected Approach

Use a repository-owned command controller plus thin convenience scripts, and
install a small systemd unit for boot ordering. This combines the useful parts
of the alternatives considered:

- Script-only automation is easy to debug but does not recover after a VM boot.
- Systemd-only automation starts services but is a poor interface for builds and deployments.
- The selected hybrid keeps build/deploy logic in scripts while systemd delegates Tuxedo lifecycle to those same scripts.

## Command Interface

Add a single `scripts/cnapsctl.sh` entry point with these commands:

- `up`: start Oracle XE, Tuxedo, and Tomcat in dependency order; skip services
  that are already healthy.
- `down`: stop Tomcat and Tuxedo in reverse order. Oracle remains running by
  default to avoid unnecessary database shutdowns; `down --all` also stops XE.
- `restart`: perform `down` followed by `up`.
- `status`: show systemd service states, expected listener ports, Tuxedo server
  status, and the HTTP health response.
- `health`: call the WebFE health endpoint and fail unless HTTP succeeds,
  `respCode` is `0000`, and Oracle/Tuxedo/WebFE are all `UP`.
- `rebuild-deploy`: run preflight, stop the application tier, build the C
  server, load Jolt metadata and TUXCONFIG, build/test the WAR, deploy it, start
  the stack, and run the health check.
- `logs`: print recent Tuxedo ULOG and Tomcat journal output.
- `install-autostart`: install and enable the systemd integration through a
  dedicated installer script.

Add `scripts/up.sh`, `scripts/down.sh`, and `scripts/rebuild-deploy.sh` as thin
wrappers so the common operations remain discoverable. All scripts derive
`APP_HOME` from their own location, accept environment overrides, and use
POSIX shell syntax compatible with Oracle Linux 8.

## Runtime Behavior

The controller reuses the existing focused scripts rather than duplicating
their build or Tuxedo configuration logic. It handles service state before
calling them so repeated execution is safe:

1. Ensure Oracle XE is active and `1521` is listening.
2. Start Tuxedo only when its bulletin board/JSL is absent; verify `8000`.
3. Start or restart Tomcat as required; verify `8080`.
4. Poll the health endpoint for a bounded period before declaring success.

`rebuild-deploy` never runs `init-db.sh`. It stops Tomcat and any active Tuxedo
domain before replacing binaries or TUXCONFIG, then uses the existing build,
metadata, configuration, and WAR deployment scripts. Failure exits non-zero,
prints the failed stage, and points to `logs/ULOG*` and the Tomcat journal.

Sudo is used only for Oracle/Tomcat systemd operations, WAR installation, and
systemd installation. The password is requested interactively and is never
stored in the repository or scripts.

## Boot Automation

Add a templated `cnaps-tuxedo.service` installed as a `Type=oneshot` unit with
`RemainAfterExit=yes`. It runs as the project owner, starts after and requires
`oracle-xe-21c.service`, and delegates start/stop to the repository scripts.

Install a Tomcat drop-in that requires and starts after `cnaps-tuxedo.service`.
The installer resolves the current absolute project path and user, installs
both files under `/etc/systemd/system`, reloads systemd, and enables Oracle XE,
CNAPS Tuxedo, and Tomcat. Re-running the installer updates the managed files
without touching unrelated systemd configuration.

## Documentation And Prompt Control

Add an operations guide covering one-time installation, full rebuild/deploy,
daily lifecycle commands, status/health/log inspection, recovery, uninstall,
and expected ports/URLs. It explicitly marks database initialization as a
first-install-only action.

The guide also provides ready-to-use Chinese Codex prompts for:

- full build/deploy and end-to-end verification;
- daily start, stop, restart, and status inspection;
- log-based diagnosis without changing code;
- safe code synchronization while preserving `conf/db.env`;
- installation or repair of boot automation.

Prompts identify the VM address and project path, prohibit committing or
printing secrets, and require the agent to report command evidence and any
failed stage.

## Testing And Acceptance

Add shell contract tests that run with stubbed `sudo`, `systemctl`, Tuxedo
commands, port checks, and HTTP responses. Tests cover command dispatch,
service ordering, already-running idempotence, `down --all`, failed health,
and early exit during rebuild/deploy. The tests must not contact the real VM.

Acceptance on the VM requires:

- the shell contract tests and existing Maven tests pass;
- `rebuild-deploy` produces the C server and WAR and ends with all three health
  components `UP`;
- repeated `up` succeeds without duplicate Tuxedo boot attempts;
- systemd reports Oracle XE, CNAPS Tuxedo, and Tomcat active after reboot;
- the Windows host can reach the health endpoint on port `8080`.

## Constraints

- `conf/db.env` remains VM-local and is never committed, copied, or printed.
- Database schema creation is outside normal automation.
- Oracle stays bound to the existing local configuration; no new firewall rule
  is added for `1521` or Jolt `8000`.
- Existing granular scripts remain usable for diagnosis and manual recovery.
