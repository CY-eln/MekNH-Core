#!/usr/bin/env bash
set -euo pipefail
# Optional GPU coverage is opt-in and is never reported as PASS when skipped.
case "${RUN_STRUCTURE_GPU:-0}" in
  0) echo 'SKIP StructureCullingGpuProbe: optional GPU test; set RUN_STRUCTURE_GPU=1 to enable'; exit 0 ;;
  1) ;;
  *) echo 'ERROR: RUN_STRUCTURE_GPU must be 0 or 1' >&2; exit 1 ;;
esac
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
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
if command -v cygpath >/dev/null 2>&1; then GRADLE_USER_HOME=$(cygpath -u "$GRADLE_USER_HOME"); fi
CACHE="$GRADLE_USER_HOME/caches/modules-2/files-2.1"
# Resolve cached coordinates without user-specific paths or content hashes; no downloads.
cached_jar() {
  local group=$1 artifact=$2 version=$3 file=$4
  local matches=("$CACHE/$group/$artifact/$version"/*/"$file")
  [[ -f "${matches[0]}" ]] || { echo "ERROR: cache missing $group:$artifact:$version ($file)" >&2; return 1; }
  printf '%s\n' "${matches[0]}"
}
java_cp() {
  # Absolute entries keep MSYS from re-converting a relative-first Windows path list.
  if command -v cygpath >/dev/null 2>&1; then cygpath -amp "$1"; else printf '%s\n' "$1"; fi
}
case "$(uname -s):$(uname -m)" in
  MINGW*:x86_64|MSYS*:x86_64) NATIVE=natives-windows ;;
  Linux:x86_64) NATIVE=natives-linux ;;
  Linux:aarch64|Linux:arm64) NATIVE=natives-linux-arm64 ;;
  *) echo 'SKIP StructureCullingGpuProbe: unsupported native platform (no GPU coverage)'; exit 0 ;;
esac
CP=$(cached_jar org.joml joml 1.10.5 joml-1.10.5.jar)
for module in lwjgl lwjgl-glfw lwjgl-opengl; do
  jar=$(cached_jar org.lwjgl "$module" 3.3.3 "$module-3.3.3.jar")
  CP="$CP:$jar"
  if ! native=$(cached_jar org.lwjgl "$module" 3.3.3 "$module-3.3.3-$NATIVE.jar"); then
    echo "SKIP StructureCullingGpuProbe: missing $NATIVE cache; host-matching natives required (no GPU coverage)"
    exit 0
  fi
  CP="$CP:$native"
done
OUT=build/structure-culling-gpu
mkdir -p "$OUT/classes"
"$JAVAC" -cp "$(java_cp "$CP")" -d "$OUT/classes" src/main/java/c_y/meknh_core/client/structure/StructurePreviewTransform.java devtools/StructureCullingGpuProbe.java
# Initialization, rendering and assertion failures remain real failures, not skips.
"$JAVA" -Djava.awt.headless=true -cp "$(java_cp "$OUT/classes:$CP")" StructureCullingGpuProbe "${1:-$OUT}"
