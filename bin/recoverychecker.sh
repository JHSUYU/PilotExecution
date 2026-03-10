#!/usr/bin/env bash

bin_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
env_source="${bin_dir}/recoverychecker-env.sh"

if [ ! -f ${env_source} ]; then
  echo "Could not find the env source ${env_source}"
  exit 1
fi
# source the envs
. ${env_source}

echo "SOOT_CLASSPATH is: ${SOOT_CLASSPATH}"

(set -x;
"${JAVA}" -cp ${RC_CLASSPATH} ${RC_JAVA_OPTS} ${RC_MAIN} -x ${SOOT_CLASSPATH} "$@"
)
