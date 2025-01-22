
path1="/Users/lizhenyu/Desktop/RecoveryChecker/sootOutput/"
path2="/Users/lizhenyu/Desktop/RCExperiment/hbase-25898/hbase/hbase-server/target/classes/org"
path3="/Users/lizhenyu/Desktop/RCExperiment/hbase-25898/hbase/hbase-server/target/classes"

rm -rf "$path2"

cp -r "$path1" "$path3"