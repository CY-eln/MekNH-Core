#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Prefer JAVA_HOME; otherwise use the JDK tools on PATH (Java 21 required).
if [[ -n "${JAVA_HOME:-}" ]]; then
  if command -v cygpath >/dev/null 2>&1; then JAVA_HOME=$(cygpath -u "$JAVA_HOME"); fi
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java_tool() {
  local tool=$1
  if [[ -n "${JAVA_HOME:-}" ]]; then
    [[ -x "$JAVA_HOME/bin/$tool" ]] || { echo "ERROR: missing $tool in JAVA_HOME" >&2; return 1; }
    printf '%s\n' "$JAVA_HOME/bin/$tool"
  else
    command -v "$tool" || { echo "ERROR: $tool missing; set JAVA_HOME to a JDK 21" >&2; return 1; }
  fi
}
JAVA=$(java_tool java)
JAVAC=$(java_tool javac)
mkdir -p build/structure-contrast-check
"$JAVAC" -d build/structure-contrast-check src/main/java/c_y/meknh_core/client/structure/StructurePreviewPalette.java devtools/StructureContrastProbe.java
"$JAVA" -cp build/structure-contrast-check StructureContrastProbe
