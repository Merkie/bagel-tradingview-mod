#!/usr/bin/env bash
# Prints the startup animation's curves (value and speed every 50 ms) and how sharply each changes speed.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
"$JAVA_HOME/bin/javac" -d "$tmp" src/client/java/com/merkie/bageltv/ui/IntroTiming.java
"$JAVA_HOME/bin/java" -cp "$tmp" com.merkie.bageltv.ui.IntroTiming
