#!/bin/bash

#cp -f "/Users/lizhenyu/Desktop/proj_failure_recovery/source_code/RecoveryChecker/sootOutput/org/apache/hadoop/hbase/ipc/RpcExecutor\$Handler.class" "/Users/lizhenyu/Desktop/proj_failure_recovery/source_code/hbase-1.4.0/hbase-server/target/classes/org/apache/hadoop/hbase/ipc/"


path1="/Users/lizhenyu/Desktop/RecoveryChecker/sootOutput/"
path2="/Users/lizhenyu/IdeaProjects/cassandra/build/classes/main/org"
path3="/Users/lizhenyu/IdeaProjects/cassandra/build/classes/main/"

rm -rf "$path2"

cp -r "$path1" "$path3"

cp -r /Users/lizhenyu/IdeaProjects/cassandra/originalBuildClass/classes/org/apache/cassandra/io/compress/CompressionMetadata.class /Users/lizhenyu/IdeaProjects/cassandra/build/classes/main/org/apache/cassandra/io/compress/CompressionMetadata.class
