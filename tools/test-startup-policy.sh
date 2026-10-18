#!/usr/bin/env bash
set -euo pipefail

TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_JDK="${JAVA_HOME:-$TASK_ROOT/.tooling/jdk/Contents/Home}"
TASK_CLASSES="$(mktemp -d "${TMPDIR:-/tmp}/samo-startup-test.XXXXXX")"
trap 'rm -rf "$TASK_CLASSES"' EXIT

"$TASK_JDK/bin/javac" -encoding UTF-8 --release 8 -d "$TASK_CLASSES" \
  "$TASK_ROOT/app/src/main/java/cn/samo/tv/BootStartupPolicy.java" \
  "$TASK_ROOT/tools/tests/cn/samo/tv/BootStartupPolicyTest.java"
"$TASK_JDK/bin/java" -cp "$TASK_CLASSES" cn.samo.tv.BootStartupPolicyTest
