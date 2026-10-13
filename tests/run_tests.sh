#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

# 1. Recompile any files modified by the candidate
mvn -q compile jar:jar -DskipTests 1>&2

# 2. Run the Java TestRunner that outputs pure JSON
exec java -cp target/app.jar com.hosteleats.TestRunner
