#!/bin/bash
#SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
#
#CONFIG_FILE="${SCRIPT_DIR}/../../conf/hbase-14598.properties"
#
#if [ ! -f "$CONFIG_FILE" ]; then
#    echo "Error: Configuration file not found: $CONFIG_FILE"
#    exit 1
#fi
#
#source "$CONFIG_FILE"
#echo "HBASE_SERVER_TARGET_CLASSES_DIR = ${HBASE_SERVER_TARGET_CLASSES_DIR}"
#
#source "${SCRIPT_DIR}/common.sh"
#HBASE_BUILD_DIR="${HBASE_SERVER_DIR}/target/classes"
##
#rm -rf "${out_dir:?}"/*
#mkdir -p ${out_dir}
#
#echo "Running recovery_checker"
#
#CMD="run_autowd -x \"${HBASE_BUILD_DIR}\" -c org.apache.hadoop.hbase.ipc.RpcExecutor\$Handler -e"
#echo "Executing command: $CMD"
#eval "$CMD"
#
#if [ -f "${RPC_EXECUTOR_TARGET}/RpcExecutor\$Handler.class" ]; then
#  echo "Removing existing RpcExecutor\$Handler.class file from ${RPC_EXECUTOR_TARGET}"
#  rm -rf "${RPC_EXECUTOR_TARGET}/RpcExecutor\$Handler.class"
#fi
#
#echo "Copying modified class files"
#find "${RPC_EXECUTOR_SOURCE}" -name "RpcExecutor*.class" -exec cp {} "${RPC_EXECUTOR_TARGET}" \;
#
#CMD="cp "${RPC_EXECUTOR_SOURCE}/RpcExecutor\$Handler.class" ${RPC_EXECUTOR_TARGET}"
#echo "$CMD"
#eval CMD
#
#cp -f /Users/lizhenyu/Desktop/proj_failure_recovery/source_code/RecoveryChecker/sootOutput/org/apache/hadoop/hbase/ipc/*.class /Users/lizhenyu/Desktop/proj_failure_recovery/source_code/hbase-1.4.0/hbase-server/target/classes/org/apache/hadoop/hbase/ipc
#
#if [ $? -eq 0 ]; then
#    echo "Copy Success"
#else
#    echo "Copy Fail"
#    exit 1
#fi
#
#echo "List RpcExecutor*.class files in ${RPC_EXECUTOR_TARGET}:"
#ls -l "${RPC_EXECUTOR_TARGET}"RpcExecutor*.class
#
#echo "Repackaging HBase"
#cd "${HBASE_SOURCE_DIR}/hbase-server" || exit 1
#echo "${MAVEN_PACKAGE_CMD_WITHOUT_COMPILE}"
#eval "${MAVEN_PACKAGE_CMD_WITHOUT_COMPILE}"
#
#if [ -f "${HBASE_SOURCE_DIR}/hbase-assembly/target/hbase-1.4.0-bin.tar.gz" ]; then
#    rm -rf "${HBASE_SOURCE_DIR}/hbase-assembly/target/hbase-1.4.0-bin.tar.gz"
#fi
#
#if [ -d "${HBASE_SOURCE_DIR}/hbase-assembly/target/hbase-1.4.0" ]; then
#    rm -rf "${HBASE_SOURCE_DIR}/hbase-assembly/target/hbase-1.4.0"
#fi
#
#echo "Creating HBase assembly"
#cd "${HBASE_SOURCE_DIR}" || exit 1
#eval "${MAVEN_ASSEMBLY_CMD_WITHOUT_COMPILE}"
#
#if [ -d "${HBASE_TARGET_DIR}" ]; then
#    rm -rf "${HBASE_TARGET_DIR}"
#fi
#echo "Extracting and moving HBase"
#cd "${HBASE_SOURCE_DIR}/hbase-assembly/target" || exit 1
#tar -zxvf "hbase-${HBASE_VERSION}-bin.tar.gz"
#mv "hbase-${HBASE_VERSION}" "${ROOT_DIR}"
#
#echo "---------------Done---------------"

my_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
echo "my_dir is: $my_dir"
. ${my_dir}/common.sh

if [ $# -ne 1 ]; then
  echo "Usage: $0 HBASE_DIR"
  exit 1
fi
hbase_dir=$1
if [ ! -d ${hbase_dir} ]; then
  echo "HBASE dir does not exist ${hbase_dir}"
  exit 1
fi
hbase_build_dir=${hbase_dir}/target/classes
hbase_regionserver_dir=${hbase_build_dir}/org/apache/hadoop/hbase/regionserver
if [ ! -d ${hbase_regionserver_dir} ]; then
  echo "Could not find ${hbase_regionserver_dir}, have you built HBase?"
  exit 1
fi

rm -rf ${out_dir}/*
mkdir -p ${out_dir}

echo "run recovery_checker"

#run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.ipc.RpcExecutor -e
run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.ipc.RpcExecutor -e

. ${my_dir}/replace.sh "$hbase_dir"



