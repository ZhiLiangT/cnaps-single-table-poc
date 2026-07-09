#!/usr/bin/env sh
set -eu

MODULE_NAME=${MODULE_NAME:-cnaps_tomcat_jolt}
WORK_DIR=${WORK_DIR:-/tmp/$MODULE_NAME}

if ! command -v semodule >/dev/null 2>&1; then
  echo "semodule is required; install policycoreutils first" >&2
  exit 1
fi

sudo dnf install -y selinux-policy-devel make >/dev/null
mkdir -p "$WORK_DIR"

cat > "$WORK_DIR/$MODULE_NAME.te" <<EOF
module $MODULE_NAME 1.0;

require {
    type tomcat_t;
    type soundd_port_t;
    type xen_port_t;
    class tcp_socket name_connect;
}

allow tomcat_t soundd_port_t:tcp_socket name_connect;
allow tomcat_t xen_port_t:tcp_socket name_connect;
EOF

make -C "$WORK_DIR" -f /usr/share/selinux/devel/Makefile "$MODULE_NAME.pp"
sudo semodule -i "$WORK_DIR/$MODULE_NAME.pp"
semodule -l | grep "$MODULE_NAME"
