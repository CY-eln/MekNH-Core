package c_y.meknh_core.decor.supercritical_ice;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

public final class DecorText {

    private DecorText() {
    }

    public static Component rainbowInfinity() {
        float hue = (System.currentTimeMillis() % 4000L) / 4000.0F;
        int rgb = Mth.hsvToRgb(hue, 1.0F, 1.0F) & 0xFFFFFF;
        return Component.literal("∞").withStyle(Style.EMPTY.withColor(rgb));
    }

    public static Component coolantLine(String labelKey) {
        return Component.translatable(labelKey).withStyle(ChatFormatting.GRAY).append(rainbowInfinity());
    }
}
