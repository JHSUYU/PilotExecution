
path1="/Users/lizhenyu/Desktop/RecoveryChecker/sootOutput/"
path2="/Users/lizhenyu/Desktop/乱七八糟/hbase/hbase/hbase-server/target/classes/org"
path3="/Users/lizhenyu/Desktop/乱七八糟/hbase/hbase/hbase-server/target/classes"
path4="/Users/lizhenyu/Desktop/乱七八糟/hbase/hbase/hbase-server/originalClass/classes/org/apache/hadoop/hbase/master/MasterRpcServices*.class"
path5="/Users/lizhenyu/Desktop/乱七八糟/hbase/hbase/hbase-server/target/classes/org/apache/hadoop/hbase/master/"

#rm -rf "$path2"
#
#cp -r "$path1" "$path3"

rm -rf "/Users/lizhenyu/Desktop/RCExperiment/hbase-25898/hbase-server/target/classes/org/apache/hadoop/hbase/master/assignment"

cp -r "/Users/lizhenyu/Desktop/RecoveryChecker/sootOutput/org/apache/hadoop/hbase/master/assignment" "/Users/lizhenyu/Desktop/RCExperiment/hbase-25898/hbase-server/target/classes/org/apache/hadoop/hbase/master"