#!/usr/bin/env sh

export APP_HOME=/opt/ruisui-bank-sim
export TUXDIR=${TUXDIR:-/opt/tuxedo}
export TUXCONFIG=${TUXCONFIG:-$APP_HOME/tuxedo/tuxconfig}
export FLDTBLDIR32=${FLDTBLDIR32:-$APP_HOME/tuxedo-server/fml}
export FIELDTBLS32=${FIELDTBLS32:-cnaps_poc.fml32}
export LD_LIBRARY_PATH=$APP_HOME/lib:$TUXDIR/lib:${LD_LIBRARY_PATH:-}
