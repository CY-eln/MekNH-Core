package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.security.SecurityText;
import mekanism.api.security.SecurityMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 仓室 / 多方块控制器 GUI 右上角的安全 Tab（渲染 + 命中）。
 *
 * <p>点击后由各 Screen 调用 {@code handleInventoryButtonClick(containerId, 201)}，
 * 服务端 {@code clickMenuButton} 里只有主人能真正切换。</p>
 */
public final class SecurityTabRenderer {

    private SecurityTabRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, int left, int top, int imageWidth,
                              int mouseX, int mouseY, SecurityMode mode) {
        TabStrip.renderSecurity(graphics, left, top, imageWidth, mode);
        if (TabStrip.isOverSecurity(mouseX, mouseY, left, top, imageWidth)) {
            graphics.renderTooltip(font,
                    Component.translatable(SecurityText.tabKey(mode.ordinal())), mouseX, mouseY);
        }
    }

    public static boolean isClick(double mouseX, double mouseY, int button, int left, int top, int imageWidth) {
        return button == 0 && TabStrip.isOverSecurity(mouseX, mouseY, left, top, imageWidth);
    }
}
