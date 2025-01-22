#!/usr/bin/env bash


cur_dir=$(dirname "${BASH_SOURCE-$0}")
cur_dir="$(cd "${cur_dir}"; pwd)"

# We prefer java from the JAVA_HOME
if [[ -z "$JAVA_HOME" ]]; then
  JAVA="$JAVA_HOME/bin/java"
else
  if [[ -z "$(which java)" ]]; then
    echo "java command not found"
    exit 1
  fi
  JAVA=java
  # when JAVA_HOME is not set, we will set it
  JAVA_HOME=${JAVA_HOME:-"$(dirname $(which java))/.."}
fi

VERSION=1.0-SNAPSHOT
RC_HOME=$(dirname "${cur_dir}")
RC_JAR="${RC_HOME}/target/RecoveryChecker-${VERSION}-jar-with-dependencies.jar"
SOOT_JAR=""
RC_MAIN=edu.uva.liftlab.recoverychecker.RCMain

RC_CONF_DIR="${RC_CONF_DIR:-${RC_HOME}/conf}"
RC_LOGS_DIR="${RC_LOGS_DIR:-${RC_HOME}/logs}"
#
RC_JAVA_OPTS+=" -Drc.conf.dir=${RC_CONF_DIR} -Drc.logs.dir=${RC_LOGS_DIR}"
RC_JAVA_OPTS+=" -Dlog4j.configuration=file:${RC_CONF_DIR}/log4j.properties"

RC_CLASSPATH="${RC_CONF_DIR}:${RC_JAR}"


# classpaths that Soot needs to know.
if [[ -z "$SOOT_CLASSPATH" ]]; then
  SOOT_CLASSPATH="${RC_HOME}/lib/opentelemetry-api-1.44.1.jar:${RC_HOME}/lib/opentelemetry-context-1.44.1.jar:${RC_HOME}/lib/opentelemetry-sdk-1.44.1.jar"
fi

