package c_y.meknh_core.client.editor;

import net.minecraft.network.chat.Component;

/**
 * 机器界面编辑器里的元素类型。
 *
 * <p>自动生成的元素：输入槽位、输出槽位、进度条、能量、MEK 热量；
 * 手动「新建元素」建出来的是 {@link #CUSTOM}。</p>
 */
public enum UiElementKind {

    ITEM_INPUT("item_input"),
    ITEM_OUTPUT("item_output"),
    PROGRESS("progress"),
    ENERGY("energy"),
    ENERGY_SLOT("energy_slot"),
    STEAM("steam"),
    FLUID_IN("fluid_in"),
    FLUID_OUT("fluid_out"),
    GAS_IN("gas_in"),
    GAS_OUT("gas_out"),
    HEAT("heat"),
    ARROW("arrow"),
    CUSTOM("custom");

    private final String id;

    UiElementKind(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public Component displayName() {
        return Component.translatable("gui.meknh_core.ui_editor.kind." + this.id);
    }
}
