# JEI extra-area regression evidence

## Proven scope

The registered TankIngredientHandler inherited JEI's empty getGuiExtraAreas default. Real cached JEI 19.70.0.469 grid-layout bytecode permits six active ingredient slots intersecting a 26x26 machine configuration tab in a populated left-bookmark-grid fixture. Forwarding the screen's extra areas blocks all six. This demonstrates a missing-exclusion vulnerability, **not confirmation of the live user's exact click failure**.

## Reproduce offline from repository root

Requires Node, JDK 21+ (JAVA/JAVAC overrides, then JAVA_HOME, then PATH; compiles --release 21), and existing cached JEI/Guava jars (JEI_JAR/GUAVA_JAR overrides accepted). No downloads or new dependency installation.

```
node devtools/check-jei-extra-areas.mjs --missing-exclusions
# exit 1: actual registered slots over config tab = 6 (both scales)
node devtools/check-jei-extra-areas.mjs
# exit 0: actual registered slots over config tab = 0 (both scales)
```

Executed both before/after production forwarding, and repeated using the mutation switch. Output:

```
854x510 scale=2 GUI=427x255 active JEI slots over tab: missing=6 excluded=0 registered=0
854x510 scale=1 GUI=854x510 active JEI slots over tab: missing=6 excluded=0 registered=0
PASS machine handler forwards exclusions (geometry tested separately)
```

The mutation removes only the override in generated test source; it never edits production. Failure is AssertionError about active JEI slots, not a missing class/tool/compiler error. The same runner also checks boiler forwarding and unchanged empty coke-oven exclusions.

## Fidelity and limits

- Extracts and compiles the **actual complete nested TankIngredientHandler source**, using the **real JEI IGuiContainerHandler interface/default implementation** and **real IngredientGridLayout.calculateSlots and intersection bytecode** from the cached jar, plus cached Guava.
- Generated sources/classes are under build/devtools/jei-extra-areas. Minecraft screen shells and Rect2i are explicit fixtures; fake screens provide a chosen extra-area list. Geometry APIs and full plugin registration/startup are NOT tested by this isolated runner.
- Fixture uses a centered 176x166 container and 108-pixel populated bookmark grid immediately left of it. This is not a measurement of JEI's actual placement in the user's session. Does not simulate mouse input, game rendering, overlay visibility, actual bookmarks/configuration, or user's GUI scale. Scales 1 and 2 are both covered to avoid assuming physical 854x510 means scaled 854x510.
- No speculative coordinate tweaks made. Parent supplies MachineScreen.jeiExtraAreas() and BoilerScreen.jeiExtraAreas(), each List<net.minecraft.client.renderer.Rect2i>, using real drawn tabs/open dragged panels. Plugin only forwards them.
- Full integration compile and production geometry verification remain parent-owned. Live confirmation requires the affected user's enabled overlay/bookmarks, GUI scale, machine type/state, then replaying clicks with/without JEI or with exclusion fix.

## Primary sources (fetched)

- [Modrinth version metadata](https://api.modrinth.com/v2/version/1vC6z8wV): cached SHA-1 363f10ce1d8918381a125cc1e8d800caf1bcb40e identifies NeoForge JEI **19.70.0.469**, Minecraft 1.21/1.21.1.
- [IGuiContainerHandler API](https://github.com/mezz/JustEnoughItems/blob/1.21.1/Common/src/api/java/mezz/jei/api/gui/handlers/IGuiContainerHandler.java): default getGuiExtraAreas returns Collections.emptyList; documentation explicitly says extra GUI tabs are the intended use.
- [IngredientGridLayout](https://github.com/mezz/JustEnoughItems/blob/1.21.1/Gui/src/main/java/mezz/jei/gui/overlay/ingredients/IngredientGridLayout.java): calculateSlots marks intersecting exclusion-area slots blocked. Actual cached jar execution above is authoritative for installed version; maintained branch URLs may advance.
- [NeoForge EventRegistration](https://github.com/mezz/JustEnoughItems/blob/1.21.1/NeoForge/src/main/java/mezz/jei/neoforge/startup/EventRegistration.java): ScreenEvent.MouseButtonPressed.Pre invokes handler.onGuiMouseClicked; true cancels event before screen processing. Thus valid MachineScreen.mouseClicked delegation alone does not rule out prior JEI interception. This event contract was source-reviewed, not executed in this harness.
