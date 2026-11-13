package c_y.meknh_core.common.data.voltage;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * 机器的电压等级（{@code 运行，超频与能耗.txt}）。
 *
 * <p>电压：ULV=8V、LV=32V，此后每级乘 4。ULV 是蒸汽机器使用的等级，
 * 因此 {@link #isSteam()} 为 {@code true}，它不接 FE 而是消耗蒸汽。</p>
 *
 * <table border="1">
 *     <caption>电压与储能</caption>
 *     <tr><th>等级</th><th>序号</th><th>电压(V)</th><th>FE 缓存</th><th>蒸汽缓存(mB)</th></tr>
 *     <tr><td>ULV</td><td>0</td><td>8</td><td>-</td><td>16000</td></tr>
 *     <tr><td>LV</td><td>1</td><td>32</td><td>2048</td><td>-</td></tr>
 *     <tr><td>MV</td><td>2</td><td>128</td><td>8192</td><td>-</td></tr>
 *     <tr><td>HV</td><td>3</td><td>512</td><td>32768</td><td>-</td></tr>
 *     <tr><td>EV</td><td>4</td><td>2048</td><td>131072</td><td>-</td></tr>
 *     <tr><td>IV</td><td>5</td><td>8192</td><td>524288</td><td>-</td></tr>
 * </table>
 *
 * <p>本类只保存数据，显示名与内部 ID 由 {@code c_y.meknh_core.decor.description.MachineNames} 负责。</p>
 */
public enum VoltageTier implements StringRepresentable {

    ULV("ulv", 0, 8L, 0L, 16_000L, true),
    LV("lv", 1, 32L, electricCapacity(1), 0L, false),
    MV("mv", 2, 128L, electricCapacity(2), 0L, false),
    HV("hv", 3, 512L, electricCapacity(3), 0L, false),
    EV("ev", 4, 2_048L, electricCapacity(4), 0L, false),
    IV("iv", 5, 8_192L, electricCapacity(5), 0L, false);

    /**
     * LV 缓存上限；更高等级每级 ×4。
     */
    private static long electricCapacity(int level) {
        return 2_048L << (2 * (level - 1));
    }

    /**
     * JSON 中 {@code level} 字段的写法允许用这个值表示“全部等级”。
     */
    public static final String ALL = "all";

    /**
     * 电网电压等级，不含蒸汽的 ULV（注册机器时按需追加蒸汽机器）。
     */
    public static final List<VoltageTier> ELECTRIC = List.of(LV, MV, HV, EV, IV);

    public static final Codec<VoltageTier> CODEC = Codec.STRING.xmap(VoltageTier::byName, VoltageTier::getSerializedName);

    public static final StreamCodec<io.netty.buffer.ByteBuf, VoltageTier> STREAM_CODEC =
            ByteBufCodecs.idMapper(index -> values()[index], Enum::ordinal);

    private final String name;
    private final int level;
    private final long voltage;
    private final long energyCapacity;
    private final long steamCapacity;
    private final boolean steam;

    VoltageTier(String name, int level, long voltage, long energyCapacity, long steamCapacity, boolean steam) {
        this.name = name;
        this.level = level;
        this.voltage = voltage;
        this.energyCapacity = energyCapacity;
        this.steamCapacity = steamCapacity;
        this.steam = steam;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * 等级序号：ULV 为 0，每升一级加 1。超频次数由它相减得到。
     */
    public int level() {
        return this.level;
    }

    /**
     * 电压，单位 V（不是 FE/t）。每 tick 耗能 = 电流(A) × 电压(V)。
     */
    public long voltage() {
        return this.voltage;
    }

    /**
     * FE 缓存上限；蒸汽机器为 0。
     */
    public long energyCapacity() {
        return this.energyCapacity;
    }

    /**
     * 蒸汽缓存上限（mB）；电力机器为 0。
     */
    public long steamCapacity() {
        return this.steamCapacity;
    }

    /**
     * 是否为蒸汽等级；蒸汽机器消耗蒸汽而不是 FE。
     */
    public boolean isSteam() {
        return this.steam;
    }

    /**
     * 该等级的并行数：LV=1、MV=2、HV=4、EV=8、IV=16；蒸汽等级固定为 1。
     */
    public int parallel() {
        return this.steam ? 1 : 1 << Math.max(0, this.level - 1);
    }

    /**
     * 按 JSON 名称查找等级，未找到返回 {@code null}。
     */
    public static VoltageTier find(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (VoltageTier tier : values()) {
            if (tier.name.equals(normalized)) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 按 JSON 名称查找等级，未找到时回退到 {@link #LV}（配方未写 {@code level} 时的默认值）。
     */
    public static VoltageTier byName(String name) {
        VoltageTier tier = find(name);
        return tier == null ? LV : tier;
    }
}
