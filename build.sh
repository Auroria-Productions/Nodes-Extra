#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
PAPER_API="${PAPER_API:?Set PAPER_API to Paper 1.21.x API jar}"
NODES_JAR="${NODES_JAR:?Set NODES_JAR to your matching milzzynodes.jar}"
DEPS_DIR="${DEPS_DIR:?Set DEPS_DIR to Paper/Gson dependencies folder}"
rm -rf "$ROOT/build" "$ROOT/dist"
mkdir -p "$ROOT/build/classes" "$ROOT/build/stubs" "$ROOT/dist"
javac --release 21 -d "$ROOT/build/stubs" "$ROOT/src/ci/stubs/net/md_5/bungee/api/chat/BaseComponent.java"
find "$ROOT/src/main/java" -name '*.java' -print0 | sort -z | xargs -0 javac --release 21 -cp "$PAPER_API:$NODES_JAR:$DEPS_DIR/*:$ROOT/build/stubs" -d "$ROOT/build/classes"
cp "$ROOT/src/main/resources/"* "$ROOT/build/classes/"
mkdir -p "$ROOT/build/test-classes"
javac --release 21 -cp "$ROOT/build/classes:$PAPER_API:$NODES_JAR:$DEPS_DIR/*:$ROOT/build/stubs" -d "$ROOT/build/test-classes" $(find "$ROOT/src/test/java" -name '*.java')
java -cp "$ROOT/build/test-classes:$ROOT/build/classes:$PAPER_API:$NODES_JAR:$DEPS_DIR/*:$ROOT/build/stubs" com.auroria.nodesmilitary.MilitaryRegressionTest
jar --create --file "$ROOT/dist/Nodes-Military.jar" -C "$ROOT/build/classes" .
unzip -t "$ROOT/dist/Nodes-Military.jar" | tail -1
unzip -p "$ROOT/dist/Nodes-Military.jar" plugin.yml | grep -F 'version: 1.1.0'
echo "Built $ROOT/dist/Nodes-Military.jar"
