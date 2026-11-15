# Four pump-block material audit

Compared with the local GTCEu source checkout: generated machine models, sided/hatch templates,
material icon-set fallbacks, original textures and animation metadata. GUI drawing dimensions
do not apply to world block models; source provenance, complete layers and runtime evidence do.

## Scope on current main

Main already locks primitive pump UVs and uses a fixed hatch shell with six-direction multipart
overlays. This change preserves both implementations, including vertical hatch directions.

- `pump_deck`: original top/side/bottom images and mapping match source; no change.
- `primitive_pump`: preserve existing UV locking and four-frame front animation. Restore source
  overlay bounds (-0.01 .. 16.01) instead of the slightly smaller quad; retain only the non-void
  north overlay face, matching the original sided template.
- `pump_hatch`: source intentionally uses top texture for sides. Three overlay layers, source
  offsets and six-frame emissive animation are complete. No production change in this PR.
- `treated_wood_frame`: WOOD -> FINE -> DULL fallback. Baked base+secondary colors 0x644218 and
  0x4e0b00 match source (one-channel rounding tolerance). The 16x16 result has 72 transparent and
  184 opaque pixels, none partially transparent. Switch from translucent to cutout to avoid
  unnecessary translucent sorting; an actual in-game sorting defect was not reproduced.

## Reproducible checks

```sh
node devtools/check-pump-top-uv.mjs
java -Djava.awt.headless=true devtools/PumpTextureAudit.java <GTCEu-source-checkout>
```

The Java probe requires Java 21+ and verifies byte parity for nine PNG/animation metadata files,
plus baked frame colors and binary alpha. The Node probe checks four locked pump directions,
source overlay bounds, fixed hatch shell, all six hatch overlay directions, item/world model
consistency, layer offsets, emissive metadata, and animation timing/dimensions.

## In-game visual acceptance remains required

A successful build and resource checks are not rendered screenshots. Reload resources (F3+T)
or restart the dev game. Place all four pump facings next to deck blocks and inspect top grain
and upright overlays. Exercise all six hatch directions including top/bottom; confirm the shell
remains fixed. Observe full animation cycles and the hatch in darkness. Check frame holes and
occlusion from both sides and at distance, plus all four item models.

This audit changes material resources only, not gameplay logic.
