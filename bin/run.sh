#!/bin/bash
PROJECT_ROOT="/Users/lizhenyu/Desktop/proj_failure_recovery/source_code/RecoveryChecker"
CLASSPATH="$PROJECT_ROOT/target/classes"

# 使用find命令来正确地添加所有的jar文件
for jar in $(find "$PROJECT_ROOT/target/dependency" -name "*.jar"); do
  CLASSPATH="$CLASSPATH:$jar"
done

echo $CLASSPATH
java -cp "$CLASSPATH" edu.uva.liftlab.recoverychecker.RCMain