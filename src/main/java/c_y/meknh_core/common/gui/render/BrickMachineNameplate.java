package c_y.meknh_core.common.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** High-contrast title decoration for brick machines; owns no widgets or hitboxes. */
public final class BrickMachineNameplate {
    private static final int BACKGROUND = 0xFF3B3027;
    private static final int BORDER = 0xFF211B16;
    private static final int HIGHLIGHT = 0xFF9C8466;
    private static final int TEXT = 0xFFF3E5CA;

    private BrickMachineNameplate() {
    }

    public static void render(GuiGraphics graphics, Font font, Component title, int panelWidth, int titleY) {
        int textWidth = font.width(title);
        int textX = (panelWidth - textWidth) / 2;
        int left = textX - 5;
        int top = titleY - 2;
        int right = textX + textWidth + 5;
        int bottom = titleY + font.lineHeight + 2;
        graphics.fill(left, top, right, bottom, BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, BACKGROUND);
        graphics.fill(left + 1, top, right - 1, top + 1, HIGHLIGHT);
        graphics.drawString(font, title, textX, titleY, TEXT, true);
    }
}
