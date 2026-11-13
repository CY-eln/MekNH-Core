package c_y.meknh_core.common.data.overclock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;

/**
 * 超频计算（{@code 运行，超频与能耗.txt}）。
 *
 * <p>超频次数 = 机器等级 − 配方等级，对 ULV 配方额外再减 1 次。每超频一次：</p>
 * <ul>
 *     <li>每 tick 耗能 ×4；</li>
 *     <li>配方时间 ×0.5，且不能低于 1 tick。</li>
 * </ul>
 *
 * <p>蒸汽机器不参与超频（它的等级就是 ULV），改由
 * {@link c_y.meknh_core.common.machine.steam.SteamVariant#timeMultiplier()} 调整时间：
 * 低压 ×2、高压 ×0.5。</p>
 */
public final class Overclock {

    /**
     * ULV 配方在计算超频次数时额外扣掉的次数。
     */
    public static final int ULV_PENALTY = 1;

    private Overclock() {
    }

    /**
     * 计算超频次数。
     *
     * @param machine     机器等级
     * @param recipeLevel 配方要求的最低电压
     * @return 超频次数，最小为 0；蒸汽机器恒为 0
     */
    public static int count(MachineLevel machine, VoltageTier recipeLevel) {
        if (machine.isSteam()) {
            return 0;
        }
        VoltageTier recipe = recipeLevel == null ? VoltageTier.ULV : recipeLevel;
        int difference = machine.voltage().level() - recipe.level();
        if (recipe == VoltageTier.ULV) {
            difference -= ULV_PENALTY;
        }
        return Math.max(0, difference);
    }

    /**
     * 每 tick 耗能（FE/t），未超频时为 {@code 电流 × 电压}。
     *
     * @param amperage 电流（A/t）
     * @param voltage  机器电压
     * @param count    超频次数
     */
    public static long energyPerTick(double amperage, VoltageTier voltage, int count) {
        if (amperage <= 0.0D) {
            return 0L;
        }
        double base = amperage * (double) voltage.voltage();
        double energy = base * Math.pow(4.0D, Math.max(0, count));
        return Math.max(0L, (long) Math.ceil(energy));
    }

    /**
     * 每 tick 的 FE/t（电力机器）或蒸汽换算前的 FE/t（蒸汽机器）。
     */
    public static long energyPerTick(double amperage, MachineLevel machine, VoltageTier recipeLevel) {
        return energyPerTick(amperage, machine.voltage(), count(machine, recipeLevel));
    }

    /**
     * 配方总时间（tick）。
     *
     * @param baseTicks 配方/机器的默认时间
     * @param count     超频次数
     * @param machine   机器等级（用于蒸汽的时间倍率）
     */
    public static int ticks(int baseTicks, int count, MachineLevel machine) {
        double ticks = (double) Math.max(1, baseTicks) * Math.pow(0.5D, Math.max(0, count)) * machine.timeMultiplier();
        return Math.max(1, (int) Math.round(ticks));
    }
}
