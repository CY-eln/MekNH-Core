package c_y.meknh_core.client.editor;

import c_y.meknh_core.common.gui.element.GaugeElement;
import c_y.meknh_core.common.gui.element.ImageElement;
import c_y.meknh_core.common.gui.element.ProgressElement;
import c_y.meknh_core.common.gui.element.SlotElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 元素渲染入口：按元素类型分派到 {@code common/gui/element} 里的元素类，
 * 编辑器预览和正式界面共用同一套，保证「所见即所得」。
 *
 * <ul>
 *     <li>输入槽 → {@code slot/input.png}，输出槽 → {@code slot/output.png}；</li>
 *     <li>进度 → {@code progress/bar.png} 按进度裁宽；</li>
 *     <li>能量 → Mekanism 的竖向能量条 {@code bar/base.png} + {@code bar/vertical_power.png}；</li>
 *     <li>热量 → 仪表 {@code gauge/standard.png} + {@code gauge/red.png}；</li>
 *     <li>自定义元素 → 用户选的贴图，没有就不画。</li>
 * </ul>
 */
public final class UiElementRenderer {

    private UiElementRenderer() {
    }

    /**
     * @return {@code false} 表示这个元素没有任何贴图可画
     */
    public static boolean render(GuiGraphics graphics, UiElement element, int offsetX, int offsetY,
                                 float storageRatio, float progressRatio, float steamRatio) {
        return render(graphics, element, offsetX, offsetY, storageRatio, progressRatio, steamRatio, null);
    }

    /**
     * @param gaugeContent 实际罐内容；null 时退回固定 FILL_* 贴图（编辑器预览）。
     * @return {@code false} 表示这个元素没有任何贴图可画
     */
    public static boolean render(GuiGraphics graphics, UiElement element, int offsetX, int offsetY,
                                 float storageRatio, float progressRatio, float steamRatio,
                                 @Nullable GaugeElement.Content gaugeContent) {
        int x = offsetX + element.x();
        int y = offsetY + element.y();
        int width = element.width();
        int height = element.height();
        ResourceLocation texture = element.texture();

        switch (element.kind()) {
            case ITEM_INPUT -> SlotElement.render(graphics, texture, SlotElement.Type.INPUT, x, y, width, height);
            case ITEM_OUTPUT -> SlotElement.render(graphics, texture, SlotElement.Type.OUTPUT, x, y, width, height);
            case PROGRESS -> ProgressElement.render(graphics, texture, progressRatio, x, y, width, height);
            case ENERGY_SLOT -> SlotElement.render(graphics, texture, SlotElement.Type.POWER, x, y, width, height);
            case STEAM -> GaugeElement.renderGauge(graphics, texture, GaugeElement.FILL_STEAM, steamRatio, x, y, width, height, gaugeContent);
            case FLUID_IN -> GaugeElement.renderGauge(graphics, texture, GaugeElement.FILL_FLUID_IN, storageRatio, x, y, width, height, gaugeContent);
            case FLUID_OUT -> GaugeElement.renderGauge(graphics, texture, GaugeElement.FILL_FLUID_OUT, storageRatio, x, y, width, height, gaugeContent);
            case GAS_IN -> GaugeElement.renderGauge(graphics, texture, GaugeElement.FILL_GAS_IN, storageRatio, x, y, width, height, gaugeContent);
            case GAS_OUT -> GaugeElement.renderGauge(graphics, texture, GaugeElement.FILL_GAS_OUT, storageRatio, x, y, width, height, gaugeContent);
            case ENERGY -> {
                if (texture != null && !GaugeElement.isFillTexture(texture)) {
                    ImageElement.render(graphics, texture, x, y, width, height);
                } else {
                    GaugeElement.renderPower(graphics, texture, storageRatio, x, y, width, height);
                }
            }
            case HEAT -> GaugeElement.renderGauge(graphics, texture, GaugeElement.FILL_HEAT, 0.0F, x, y, width, height);
            case ARROW -> ImageElement.render(graphics,
                    texture != null ? texture : c_y.meknh_core.common.gui.layout.MachineGuiTextures.UP_ARROW,
                    x, y, width, height);
            case CUSTOM -> {
                if (texture == null) {
                    return false;
                }
                ImageElement.render(graphics, texture, x, y, width, height);
            }
        }
        return true;
    }
}
