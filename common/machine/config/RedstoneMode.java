package c_y.meknh_core.common.machine.config;

import net.minecraft.network.chat.Component;

/**
 * 红石控制模式（对应 Mekanism 的红石 tab）。
 */
public enum RedstoneMode {
    DISABLED,
    HIGH,
    LOW;

    public RedstoneMode next() {
        RedstoneMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static RedstoneMode byOrdinal(int ordinal) {
        RedstoneMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    public Component displayName() {
        return Component.translatable("gui.meknh_core.redstone." + this.name().toLowerCase(java.util.Locale.ROOT));
    }
}
