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
JOML=$(cached_jar org.joml joml 1.10.5 joml-1.10.5.jar)
GSON=$(cached_jar com.google.code.gson gson 2.10.1 gson-2.10.1.jar)
mkdir -p build/structure-state-probe build/structure-viewport-probe
"$JAVAC" -d build/structure-state-probe src/main/java/c_y/meknh_core/client/structure/StructureViewState.java devtools/StructureViewStateProbe.java
"$JAVA" -cp build/structure-state-probe StructureViewStateProbe
"$JAVAC" -d build/structure-state-probe src/main/java/c_y/meknh_core/client/structure/StructureCandidateGrid.java devtools/StructureCandidateGridProbe.java
"$JAVA" -cp build/structure-state-probe StructureCandidateGridProbe
"$JAVAC" -cp "$(java_cp "$JOML")" -d build/structure-viewport-probe src/main/java/c_y/meknh_core/client/structure/StructureViewportBounds.java devtools/StructureViewportBoundsProbe.java
"$JAVA" -cp "$(java_cp "build/structure-viewport-probe:$JOML")" StructureViewportBoundsProbe
mkdir -p build/probes/picking
"$JAVAC" -cp "$(java_cp "$JOML")" -d build/probes/picking src/main/java/c_y/meknh_core/client/structure/StructurePreviewTransform.java devtools/StructurePickingProbe.java
"$JAVA" -cp "$(java_cp "build/probes/picking:$JOML")" StructurePickingProbe
# Never clean: retain existing deliverables and probe evidence.
GRADLE_CACHE="$GRADLE_USER_HOME"
if command -v cygpath >/dev/null 2>&1; then GRADLE_CACHE=$(cygpath -m "$GRADLE_CACHE"); fi
GRADLE_USER_HOME="$GRADLE_CACHE" bash ./gradlew --offline --no-daemon -Pmod_version=1.0.0-jei-structures-v1.14-hover-feedback build
"$JAVA" --class-path "$(java_cp "$GSON")" devtools/StructureArtifactProbe.java build/libs/meknh_core-1.0.0-jei-structures-v1.14-hover-feedback.jar
bash devtools/check-structure-jei-focus-contract.sh
bash devtools/check-structure-culling-gpu.sh
git diff --check
