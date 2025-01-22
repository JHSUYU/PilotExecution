#!/bin/bash
#exec > /Users/lizhenyu/Desktop/RecoveryChecker/logs/analysis.log 2>&1

echo "compile success"

my_dir=$(cd "$(dirname "${BASH_SOURCE-$0}")"; pwd)
echo "my_dir is: $my_dir"
. ${my_dir}/common.sh

if [ $# -ne 2 ]; then
  echo "Usage: $0 fail"
  exit 1
fi
target_dir=$1
if [ ! -d ${target_dir} ]; then
  echo "target dir does not exist ${target_dir}"
  exit 1
fi

config_file=$2
# if config file does not exist, exit
if [ ! -f ${config_file} ]; then
  echo "config file does not exist ${config_file}"
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
run_recovery_checker -i ${target_dir} -e -C ${config_file}

