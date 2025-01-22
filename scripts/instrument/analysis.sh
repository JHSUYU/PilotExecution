compile_recovery_checker() {
    # 保存当前目录
    current_dir=$(pwd)

    # 切换到父目录
    cd ../..

    # 执行命令（这里用 'ls -l' 作为示例）
    echo "Executing command in parent directory (Method 1):"
    mvn compile dependency:copy-dependencies

    # 返回原目录
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
#run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.regionserver.RSRpcServices -e -i ${hbase_build_dir}
run_autowd -x ${hbase_build_dir} -c org.apache.hadoop.hbase.regionserver.RSRpcServices org.apache.hadoop.hbase.regionserver.RegionScannerImpl org.apache.hadoop.hbase.regionserver.HRegionServer -e
