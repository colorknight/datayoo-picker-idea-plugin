#!/usr/bin/env bash
set -euo pipefail

JDK21='G:/Java/jdk-21.0.7'
if [[ ! -d "$JDK21" ]]; then
  echo "ERROR: JDK path not found: $JDK21" >&2
  exit 1
fi

# 强制每次重新生成插件包（避免 UP-TO-DATE 不产出新文件）
# - clean：清理上次产物
# - --rerun-tasks：即使输入未变也强制执行任务
./gradlew -Dorg.gradle.java.home="$JDK21" clean buildPlugin --rerun-tasks
