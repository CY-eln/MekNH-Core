#!/usr/bin/env bash
set -euo pipefail
: "${UNION:?Set UNION to the SecureJarHandler 3.0.8 jar path}"
JAVAC="${JAVAC:-javac}"
JAVA="${JAVA:-java}"
SEP=":"
case "${OSTYPE:-}" in msys*|cygwin*|win32*) SEP=";" ;; esac
mkdir -p build/devtools/multiblock-directory
"$JAVAC" --release 21 -cp "$UNION" -d build/devtools/multiblock-directory src/main/java/c_y/meknh_core/api/multiblock/MultiblockDefinitionFiles.java devtools/MultiblockDirectoryProbe.java
"$JAVA" --add-opens java.base/java.lang.invoke=ALL-UNNAMED -cp "build/devtools/multiblock-directory${SEP}$UNION" c_y.meknh_core.api.multiblock.MultiblockDirectoryProbe
