# Controller binding contract

Canonical top-level JSON: "controller_block": "meknh_core:primitive_pump".
Only the primitive pump is an external candidate initially. It requires level:none.
Candidates retain DeferredBlock/DeferredItem handles; catalog and preflight never call holder.get().

The loader first parses every definition, then validates all requests by parsed definition ID,
then installs the binding plan and registers definitions in sorted order. This is fail-closed:
any invalid JSON (including previously skipped ordinary malformed definitions), scan/read error,
duplicate parsed ID, invalid ResourceLocation, non-string/null controller_block, unsupported
block, voltage incompatibility, or conflicting explicit claims aborts the entire batch before
any multiblock definition is registered. Diagnostics are sorted; two explicit claims to one
block both fail, independent of file/mod scan order. Invalid explicit requests never fall back.

An explicit valid pump claim overrides its legacy implicit fallback. The legacy definition
is not removed; it generates its own ordinary controller under its own ID instead.
A native dynamic controller declaration is only a no-op if it is exactly the sole generated
block ID of that same definition. It cannot borrow another definition's coke controller.
No factory ownership changes: custom BE factories remain keyed by definition ID.
Pump construction resolves the final selected definition by block ID; placement and chunk
loading still share MultiblockRegistration.createController.

## Pure Java probe (Java 21+, no Gradle or Minecraft dependencies)

From Core root:

```sh
mkdir -p build/devtools/controller-binding
javac --release 21 -d build/devtools/controller-binding src/main/java/c_y/meknh_core/api/multiblock/ControllerBindingPlan.java devtools/ControllerBindingPlanProbe.java
java -cp build/devtools/controller-binding ControllerBindingPlanProbe
```

Generated block IDs and BE paths are checked across requests (including namespace-flattened
collisions). Generated IDs cannot replace external candidates or known Core hatch/casing
registrations, even with native self no-op declarations. The reserved catalog uses holder IDs
only. This does not attempt to discover arbitrary other-mod registries before registration.
