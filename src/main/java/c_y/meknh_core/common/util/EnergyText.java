package c_y.meknh_core.common.util;

import java.util.Locale;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.network.chat.Component;

/**
 * 能量数值显示：SI 前缀（m/μ/n… 与 k/M/G/T…）+ **1 位小数**。
 *
 * <p>数值本身仍然是 FE / J 的原值，只改变显示；进位与取整方式与 Mek 的
 * {@code MeasurementUnit} 一致（每 1000 进一级、截断不四舍五入），
 * 只是把小数位固定成 1 位。</p>
 *
 * <p>{@link #formatUnit(double, UnitDisplayUtils.EnergyUnit)} 供 Mixin 接管 Mek 自己的
 * {@code UnitDisplayUtils#getDisplayShort(double, EnergyUnit)}（Jade / 机器 GUI 都用它），
 * 这样 Mek 的短单位也统一成 1 位小数。</p>
 */
public final class EnergyText {

    /**
     * 大于等于 1 时的前缀，每 1000 进一级。
     */
    private static final String[] LARGE_PREFIXES = {"", "k", "M", "G", "T", "P", "E"};

    /**
     * 小于 1 时的前缀，每 1/1000 进一级。
     */
    private static final String[] SMALL_PREFIXES = {"m", "μ", "n", "p", "f"};

    private EnergyText() {
    }

    /**
     * FE → {@code "1.2 kFE"}。
     */
    public static Component shortFe(long fe) {
        return Component.literal(formatNumber(Math.max(0L, fe)) + " FE");
    }

    /**
     * 焦耳 → {@code "1.2 kJ"}。
     *
     * <p>注意：Mek 的接口（Jade/TOP 的 {@code EnergyElement}、{@code IStrictEnergyHandler}）
     * 用的是 J，不是 FE；把 FE 直接塞进去会差 2.5 倍。</p>
     */
    public static Component shortJ(long joules) {
        return Component.literal(formatNumber(Math.max(0L, joules)) + " J");
    }

    /**
     * 已经换算到目标单位的数值 → 带该单位符号的短文本，供 Mek 的
     * {@code UnitDisplayUtils#getDisplayShort(double, EnergyUnit)} 替换用。
     */
    public static Component formatUnit(double value, UnitDisplayUtils.EnergyUnit unit) {
        String symbol = unit.getSymbol(false).translate().getString();
        return Component.literal(formatNumber(value) + " " + symbol);
    }

    /**
     * 与 Mek 一致地选前缀（含 m/μ/n/p/f 与 k/M/G/T/P/E），但固定 1 位小数、截断。
     */
    private static String formatNumber(double value) {
        double scaled = Math.max(0.0D, value);
        String prefix = "";
        if (scaled > 0.0D && scaled < 1.0D) {
            for (int index = 0; index < SMALL_PREFIXES.length && scaled < 1.0D; index++) {
                scaled *= 1000.0D;
                prefix = SMALL_PREFIXES[index];
            }
        } else {
            int index = 0;
            while (index < LARGE_PREFIXES.length - 1 && scaled >= 1000.0D) {
                scaled /= 1000.0D;
                index++;
            }
            prefix = LARGE_PREFIXES[index];
        }
        long tenths = (long) (scaled * 10.0D);
        return String.format(Locale.ROOT, "%.1f %s", tenths / 10.0D, prefix);
    }
}
