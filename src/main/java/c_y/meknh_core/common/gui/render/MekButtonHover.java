package c_y.meknh_core.common.gui.render;

import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import net.minecraft.client.gui.GuiGraphics;

/** Mekanism 10.7 button states and slot hover, using GUI-scaled half-open hit rectangles. */
public final class MekButtonHover {
    private MekButtonHover() { }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Caller owns tint: colored side buttons must keep their mode color over the hovered texture. */
    public static void drawBase(GuiGraphics graphics, int x, int y, int width, int height,
                                int mouseX, int mouseY, boolean enabled) {
        int textureY = !enabled ? 0 : contains(x, y, width, height, mouseX, mouseY) ? 40 : 20;
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_BUTTON, x, y, width, height,
                20, 4, 200, 60, 0, textureY, 200, 20);
    }

    /** GuiSlot's translucent white highlight; preserves the underlying color rather than replacing it. */
    public static void drawColorOverlay(GuiGraphics graphics, int x, int y, int width, int height,
                                        int mouseX, int mouseY) {
        if (contains(x, y, width, height, mouseX, mouseY)) {
            graphics.fill(x, y, x + width, y + height, 0x80FFFFFF);
        }
    }
}
