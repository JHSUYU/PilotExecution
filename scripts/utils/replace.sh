#!/bin/bash

source_path="/Users/lizhenyu/Desktop/proj_failure_recovery/source_code/RecoveryChecker/sootOutput/org/apache/hadoop/hbase/ipc/RpcExecutor.class"
target_path="/Users/lizhenyu/Desktop/proj_failure_recovery/source_code/hbase/hbase-server/target/classes/org/apache/hadoop/hbase/ipc/RpcExecutor.class"

cp "$source_path" "$target_path"

if [ $? -eq 0 ]; then
    echo "Copy Success"
else
    echo "Copy Fail"
fi

target_dir="/Users/lizhenyu/Desktop/proj_failure_recovery/DryRun_Checker_Issues/HBase-14598/src/decompiler/"
cp "$source_path" "$target_dir"

if [ $? -eq 0 ]; then
    echo "Move Success"
else
    echo "Move Fail"
fi

