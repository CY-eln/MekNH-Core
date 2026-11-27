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
JAVAP=$(java_tool javap)
JAR=${1:-build/libs/meknh_core-1.0.0-jei-structures-v1.14-hover-feedback.jar}
category=$("$JAVAP" -p -c -classpath "$JAR" c_y.meknh_core.client.jei.StructureInfoCategory)
plugin=$("$JAVAP" -p -c -classpath "$JAR" c_y.meknh_core.client.jei.MeknhJeiPlugin)
for expected in RENDER_ONLY addInvisibleIngredients candidates controllers controllerPreview; do
  grep -Fq "$expected" <<< "$category"
done
for expected in structureCategories controllerPreview addRecipeCatalysts addRecipes; do
  grep -Fq "$expected" <<< "$plugin"
done
if grep -Fq mezz/jei/gui/recipes <<< "$category$plugin"; then exit 1; fi
echo 'PASS native JEI contract: controller-specific types, render-only shared headers, allowed-part usage index, no internal hook'
