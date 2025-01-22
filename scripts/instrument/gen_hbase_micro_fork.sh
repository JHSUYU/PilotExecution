compile_recovery_checker() {
    current_dir=$(pwd)
    cd ../..

    mvn compile dependency:copy-dependencies

    cd "$current_dir"
}

exec > /Users/lizhenyu/Desktop/RecoveryChecker/logs/analysis.log 2>&1


compile_recovery_checker

echo "compile success"

my_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
echo "my_dir is: $my_dir"
. ${my_dir}/common.sh

if [ $# -ne 1 ]; then
  echo "Usage: $0 HBASE_DIR"
  exit 1
fi
target_dir=$1
if [ ! -d ${target_dir} ]; then
  echo "target dir does not exist ${target_dir}"
  exit 1
fi
target_dir=${target_dir}/classes
#hbase_regionserver_dir=${hbase_build_dir}/org/apache/hadoop/hbase/regionserver
#if [ ! -d ${hbase_regionserver_dir} ]; then
#  echo "Could not find ${hbase_regionserver_dir}, have you built HBase?"
#  exit 1
#fi

rm -rf ${out_dir}/*
mkdir -p ${out_dir}

echo "run recovery_checker"

lib_dir=/Users/lizhenyu/Desktop/RecoveryChecker/lib
#run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.ipc.RpcExecutor -e
#run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.regionserver.RSRpcServices -e -i ${hbase_build_dir}
#run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.regionserver.RSRpcServices org.apache.hadoop.hbase.regionserver.RegionScannerImpl org.apache.hadoop.hbase.regionserver.HRegionServer -e
#run_autowd -x ${lib_dir} -i ${target_dir} -e
run_autowd -x ${lib_dir}/opentelemetry-api-1.44.1.jar:${lib_dir}/opentelemetry-context-1.44.1.jar:${lib_dir}/opentelemetry-sdk-1.44.1.jar -i ${target_dir} -e
#run_autowd -x ${target_dir} -c org.apache.hadoop.hbase.io.hfile.LruBlockCache -e
#/bin/bash gen_hbase_micro_fork.sh /Users/lizhenyu/Desktop/RCExperiment/hbase-25898/hbase/hbase-server/originalClass

