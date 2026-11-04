#!/bin/bash
set -eo pipefail

BIN_DIR=$(dirname $(readlink -f "$0"))
YAK_OPS_HOME=$(cd ${BIN_DIR}/../..;pwd)
STANDALONE_HOME=$(cd ${BIN_DIR}/..;pwd)

export DATABASE=${DATABASE:-h2}
source "$STANDALONE_HOME/conf/yak-ops_env.sh"

JVM_ARGS_ENV_FILE=${STANDALONE_HOME}/bin/jvm_args_env.sh
JVM_ARGS="-server"

if [ -f $JVM_ARGS_ENV_FILE ]; then
  while read line
  do
      if [[ "$line" == -* ]]; then
            JVM_ARGS="${JVM_ARGS} $line"
      fi
  done < $JVM_ARGS_ENV_FILE
fi

JAVA_OPTS=${JAVA_OPTS:-"${JVM_ARGS}"}

if [[ "$DOCKER" == "true" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -XX:-UseContainerSupport -DDOCKER=true"
fi

echo "JAVA_HOME=${JAVA_HOME}"
echo "JAVA_OPTS=${JAVA_OPTS}"

MODULES_PATH=(
api-server
master-server
worker-server
alert-server
)

CP=""
for module in ${MODULES_PATH[@]}; do
  CP=$CP:"$YAK_OPS_HOME/$module/libs/*"
done

PLUGINS_PATH=(
alert-plugins
datasource-plugins
storage-plugins
task-plugins
)

for plugin in ${PLUGINS_PATH[@]}; do
  if [ -d "$YAK_OPS_HOME/plugins/$plugin" ]; then
    CP=$CP:"$YAK_OPS_HOME/plugins/$plugin/*"
  fi
done

for jar in $(find $STANDALONE_HOME/libs/* -name "*.jar"); do
  CP=$CP:"$jar"
done

$JAVA_HOME/bin/java $JAVA_OPTS \
  -cp "$STANDALONE_HOME/conf""$CP" \
  io.yak.ops.StandaloneServer
