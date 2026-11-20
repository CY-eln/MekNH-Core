package c_y.meknh_core.common.gui.render;

import c_y.meknh_core.MekNHCore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Decorative layer only: does not own any slots, hitboxes or machine state. */
public final class MetalMachineBackground {
    private static final java.util.Map<MachineBackgroundMaterial, ResourceLocation> TEXTURES =
            new java.util.EnumMap<>(MachineBackgroundMaterial.class);
    static {
        for (MachineBackgroundMaterial material : MachineBackgroundMaterial.values()) {
            TEXTURES.put(material, MekNHCore.rl(material.texture()));
        }
    }
    private static int cachedPanelWidth = -1;
    private static int cachedTitleWidth = -1;
    private static TitleRing ring;

    private MetalMachineBackground() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              MachineBackgroundState state, int titleWidth) {
        render(graphics, x, y, width, height, state, titleWidth, MachineBackgroundMaterial.METAL);
    }

    public static void renderBase(GuiGraphics graphics, int x, int y, int width, int height,
                                  MachineBackgroundMaterial material) {
        // Brick resources are pre-tiled at native pixel scale. Crop, never stretch the bricks.
        int sourceWidth = material.tiled() ? width : 176;
        int sourceHeight = material.tiled() ? height : 177;
        graphics.blit(TEXTURES.get(material), x, y, width, height, 0, 0, sourceWidth, sourceHeight, 176, 177);
        if (!material.tiled()) {
            for (int layer = 0; layer < MetalBevel.DEPTH; layer++) {
                int left = x + layer, top = y + layer;
                int right = x + width - layer, bottom = y + height - layer;
                graphics.fill(left, top, right, top + 1, MetalBevel.color(material, layer, MetalBevel.TOP));
                graphics.fill(left, top, left + 1, bottom, MetalBevel.color(material, layer, MetalBevel.LEFT));
                graphics.fill(left, bottom - 1, right, bottom, MetalBevel.color(material, layer, MetalBevel.BOTTOM));
                graphics.fill(right - 1, top, right, bottom, MetalBevel.color(material, layer, MetalBevel.RIGHT));
            }
        }
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              MachineBackgroundState state, int titleWidth, MachineBackgroundMaterial material) {
        renderBase(graphics, x, y, width, height, material);
        if (ring == null || cachedPanelWidth != width || cachedTitleWidth != titleWidth) {
            ring = new TitleRing(width, titleWidth);
            cachedPanelWidth = width;
            cachedTitleWidth = titleWidth;
        }
        // Cosmetic clockwise motion around the closed ring; no horizontal sweep or side leads.
        double phase = (System.nanoTime() % 3_200_000_000L) / 3_200_000_000.0;
        for (int point = 0; point < ring.size(); point++) {
            int px = x + ring.x(point), py = y + ring.y(point);
            graphics.fill(px, py, px + 2, py + 2, ring.color(point, state, phase));
        }
    }
}
