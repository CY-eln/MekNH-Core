package c_y.meknh_core.common.gui.element;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 通用侧边 tab 控制器：图集、点击、侧面配置面板都不属于某一台机器。
 *
 * <p>机器/锅炉只负责提供 {@link SideConfigPanel.Context}、能量/传输回调与按钮发送。</p>
 */
public final class ScreenTabs {

    private final SideConfigPanel sideConfig = new SideConfigPanel();
    @Nullable
    private IntConsumer energyClick;
    @Nullable
    private Runnable transporterClick;

    public void init(BlockPos pos) {
        this.sideConfig.init(pos);
    }

    public void setEnergyClick(@Nullable IntConsumer energyClick) {
        this.energyClick = energyClick;
    }

    public void setTransporterClick(@Nullable Runnable transporterClick) {
        this.transporterClick = transporterClick;
    }

    public void renderTabs(GuiGraphics graphics, int left, int top,
                           int redstoneOrdinal, boolean privateMode, int warningCode) {
        TabStrip.render(graphics, left, top, redstoneOrdinal, privateMode, warningCode);
    }

    public void renderOverlay(GuiGraphics graphics, Font font, int left, int top,
                              SideConfigPanel.Context context) {
        this.sideConfig.render(graphics, font, left, top, context);
    }

    public void renderWithConfig(GuiGraphics graphics, Font font, int left, int top,
                                 int redstoneOrdinal, boolean privateMode, int warningCode,
                                 SideConfigPanel.Context context) {
        this.renderTabs(graphics, left, top, redstoneOrdinal, privateMode, warningCode);
        this.renderOverlay(graphics, font, left, top, context);
    }

    public boolean isConfigOpen() {
        return this.sideConfig.isOpen();
    }

    public boolean isConfigPinned() {
        return this.sideConfig.isPinned();
    }

    public void closeConfig() {
        this.sideConfig.close();
    }

    public boolean keyPressed(int keyCode) {
        return this.sideConfig.keyPressed(keyCode);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return this.sideConfig.mouseDragged(mouseX, mouseY, button);
    }

    public void mouseReleased() {
        this.sideConfig.mouseReleased();
    }

    public boolean renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                                 int left, int top, SideConfigPanel.Context context) {
        return this.sideConfig.renderTooltip(graphics, font, mouseX, mouseY, left, top, context);
    }

    @Nullable
    public TabStrip.Tab tabAt(int mouseX, int mouseY, int left, int top, boolean warning) {
        return TabStrip.tabAt(mouseX, mouseY, left, top, warning);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton, int left, int top,
                                boolean warning, IntConsumer sendButton, SideConfigPanel.Context context) {
        if (this.sideConfig.isOpen()) {
            return this.sideConfig.mouseClicked(mouseX, mouseY, mouseButton, left, top, context);
        }
        TabStrip.Tab tab = TabStrip.tabAt((int)mouseX, (int)mouseY, left, top, warning);
        if (tab != null) {
            switch (tab) {
                case CONFIG -> this.sideConfig.toggle();
                case TRANSPORTER -> {
                    if (this.transporterClick != null) {
                        this.transporterClick.run();
                    } else {
                        this.sideConfig.toggle();
                    }
                }
                case REDSTONE -> sendButton.accept(200);
                case SECURITY -> sendButton.accept(201);
                case ENERGY -> {
                    if (this.energyClick != null) {
                        this.energyClick.accept(mouseButton);
                    }
                }
                case WARNING -> {
                }
            }
            return true;
        }
        return false;
    }
}
