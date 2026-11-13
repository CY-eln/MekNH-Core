package c_y.meknh_core.common.machine.config;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 每个面允许的物流方向（对应 Mekanism 的侧面配置）。
 */
public enum SideMode {
    NONE(0x404040),
    INPUT(0xD43B3B),
    OUTPUT(0x3B6BD4),
    BOTH(0x9E9E9E);

    private final int color;

    SideMode(int color) {
        this.color = color;
    }

    public int color() {
        return this.color;
    }

    public boolean allowsInsert() {
        return this == INPUT || this == BOTH;
    }

    public boolean allowsExtract() {
        return this == OUTPUT || this == BOTH;
    }

    public SideMode next() {
        SideMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public SideMode previous() {
        SideMode[] values = values();
        return values[Math.floorMod(this.ordinal() - 1, values.length)];
    }

    public Component displayName() {
        return Component.translatable("gui.meknh_core.side." + this.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static SideMode byOrdinal(int ordinal) {
        SideMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    public static SideMode of(@Nullable Integer ordinal) {
        return ordinal == null ? NONE : byOrdinal(ordinal);
    }

    public static SideMode forDirection(@Nullable SideMode[] modes, Direction direction) {
        return modes == null ? NONE : modes[direction.ordinal()];
    }
}
