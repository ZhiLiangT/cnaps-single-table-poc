#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=${APP_HOME:-$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)}
INSTALLER=${1:-${TUXEDO_INSTALLER:-}}
TUXDIR=${TUXDIR:-/opt/tuxedo}
TUXEDO_ORACLE_HOME=${TUXEDO_ORACLE_HOME:-/opt/tuxedo-home}
INSTALL_WORK=${INSTALL_WORK:-/tmp/tuxedo-22c-install}
OUI_INVENTORY=${OUI_INVENTORY:-/opt/oracle/oraInventory}
UNIX_GROUP_NAME=${UNIX_GROUP_NAME:-$(id -gn)}
ORACLE_HOME_NAME=${ORACLE_HOME_NAME:-Tuxedo22cHome}
TUXEDO_VERSION=${TUXEDO_VERSION:-22.1.1.0.0}
INSTALL_TYPE=${INSTALL_TYPE:-Full Install}
DEP_MODE=${DEP_MODE:-$INSTALL_TYPE}
TLDEPMODES=${TLDEPMODES:-install_type_1}

if [ -z "$INSTALLER" ]; then
  echo "Usage: $0 /path/to/oracle-tuxedo-22c-linux-x86-64.zip" >&2
  echo "Or set TUXEDO_INSTALLER=/path/to/installer.zip" >&2
  exit 2
fi

if [ ! -e "$INSTALLER" ]; then
  echo "Installer not found: $INSTALLER" >&2
  exit 2
fi

case "$INSTALLER" in
  *.zip)
    rm -rf "$INSTALL_WORK"
    mkdir -p "$INSTALL_WORK"
    unzip -q "$INSTALLER" -d "$INSTALL_WORK"
    INSTALL_ROOT=$INSTALL_WORK
    ;;
  *)
    INSTALL_ROOT=$INSTALLER
    ;;
esac

RUNINSTALLER=$(find "$INSTALL_ROOT" -path '*/Disk1/install/runInstaller.sh' -type f -print | head -n 1)
PRODUCTS_XML=$(find "$INSTALL_ROOT" -path '*/Disk1/stage/products.xml' -type f -print | head -n 1)

if [ -z "$RUNINSTALLER" ] || [ -z "$PRODUCTS_XML" ]; then
  echo "Unable to find Disk1/install/runInstaller.sh and Disk1/stage/products.xml under $INSTALL_ROOT" >&2
  exit 1
fi

sudo mkdir -p "$TUXEDO_ORACLE_HOME" "$OUI_INVENTORY"
sudo chown -R "$(id -u):$(id -g)" "$TUXEDO_ORACLE_HOME" "$OUI_INVENTORY"

ORA_INST_TMP=$(mktemp)
cat > "$ORA_INST_TMP" <<EOF
inventory_loc=$OUI_INVENTORY
inst_group=$UNIX_GROUP_NAME
EOF
sudo install -m 0644 "$ORA_INST_TMP" /etc/oraInst.loc
rm -f "$ORA_INST_TMP"

RESPONSE_FILE=${RESPONSE_FILE:-$INSTALL_WORK/tuxedo22c.rsp}
mkdir -p "$(dirname "$RESPONSE_FILE")"
cat > "$RESPONSE_FILE" <<EOF
RESPONSEFILE_VERSION=2.2.1.0.0
UNIX_GROUP_NAME="$UNIX_GROUP_NAME"
FROM_LOCATION="$PRODUCTS_XML"
FROM_LOCATION_CD_LABEL=<Value Unspecified>
ORACLE_HOME="$TUXEDO_ORACLE_HOME"
ORACLE_BASE=<Value Unspecified>
ORACLE_HOME_NAME="$ORACLE_HOME_NAME"
SHOW_WELCOME_PAGE=false
SHOW_CUSTOM_TREE_PAGE=false
SHOW_COMPONENT_LOCATIONS_PAGE=false
SHOW_SUMMARY_PAGE=false
SHOW_INSTALL_PROGRESS_PAGE=false
SHOW_REQUIRED_CONFIG_TOOL_PAGE=false
SHOW_CONFIG_TOOL_PAGE=false
SHOW_RELEASE_NOTES=false
SHOW_ROOTSH_CONFIRMATION=false
SHOW_END_SESSION_PAGE=false
SHOW_EXIT_CONFIRMATION=false
NEXT_SESSION=false
NEXT_SESSION_ON_FAIL=false
NEXT_SESSION_RESPONSE=<Value Unspecified>
ACCEPT_LICENSE_AGREEMENT=true
TOPLEVEL_COMPONENT={"Tuxedo","$TUXEDO_VERSION"}
SHOW_SPLASH_SCREEN=false
SELECTED_LANGUAGES={"en"}
COMPONENT_LANGUAGES={"en"}
INSTALL_TYPE="$INSTALL_TYPE"
DEP_MODE="$DEP_MODE"
TLDepModes="$TLDEPMODES"
ENABLE_TSAM_AGENT=false
CONFIG_TLISTEN=false
MIN_CRYPT_BITS_CHOOSE=0
MAX_CRYPT_BITS_CHOOSE=0
LDAP_SUPPORT_SSL=false
INSTALL_SAMPLES=false
ENCRYPT_CHOICE=0
LDAP_FILTER_FILE="$TUXEDO_ORACLE_HOME/tuxedo$TUXEDO_VERSION/udataobj/security/bea_ldap_filter.dat"
LDAP_CONFIG={"ldap service name","ldap portid","ldap base object"}
INSTALL_SAMPLES=No
EOF

echo "Installing Oracle Tuxedo from $RUNINSTALLER to $TUXEDO_ORACLE_HOME"
LANG=C LC_ALL=C "$RUNINSTALLER" -silent -waitforcompletion -responseFile "$RESPONSE_FILE" -ignoreSysPrereqs -ignorePrereq

for root_script in "$OUI_INVENTORY/orainstRoot.sh" "$TUXEDO_ORACLE_HOME/root.sh" "$TUXEDO_ORACLE_HOME/tuxedo$TUXEDO_VERSION/root.sh"; do
  if [ -x "$root_script" ]; then
    echo "Running $root_script"
    sudo "$root_script"
  fi
done

. "$APP_HOME/conf/tuxedo.env"

RUNTIME_DIR=
for candidate in "$TUXEDO_ORACLE_HOME/tuxedo$TUXEDO_VERSION" "$TUXEDO_ORACLE_HOME"/tuxedo*/; do
  if [ -x "$candidate/bin/tmadmin" ]; then
    RUNTIME_DIR=${candidate%/}
    break
  fi
done

if [ -z "$RUNTIME_DIR" ]; then
  echo "Unable to locate installed Tuxedo runtime under $TUXEDO_ORACLE_HOME" >&2
  exit 1
fi

if [ "$RUNTIME_DIR" != "$TUXDIR" ]; then
  if [ -L "$TUXDIR" ]; then
    sudo rm -f "$TUXDIR"
  elif [ -d "$TUXDIR" ] && [ -z "$(find "$TUXDIR" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
    sudo rmdir "$TUXDIR"
  elif [ -e "$TUXDIR" ]; then
    echo "$TUXDIR exists and is not an empty directory or symlink; not replacing it" >&2
    exit 1
  fi
  sudo ln -s "$RUNTIME_DIR" "$TUXDIR"
fi

. "$APP_HOME/conf/tuxedo.env"

echo "Installed Tuxedo tools:"
command -v tmadmin
command -v tmloadcf
command -v buildserver

echo "Detected Jolt jars:"
find "$TUXDIR" -type f \( -name 'jolt*.jar' -o -name '*jolt*.jar' \) -print
