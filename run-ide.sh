#!/usr/bin/env bash
set -euo pipefail

JDK17='/c/Users/hehua/scoop/apps/temurin17-jdk/current'
if [[ -d "$JDK17" ]]; then
  export JAVA_HOME="$JDK17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

./gradlew runIde
