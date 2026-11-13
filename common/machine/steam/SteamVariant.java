package c_y.meknh_core.common.machine.steam;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * 蒸汽机器的压力版本（{@code 批量注册机器.txt}、{@code 运行，超频与能耗.txt}）。
 *
 * <p>蒸汽机器没有电压等级可选，只分低压与高压两种，内部 ID 前缀分别是
 * {@code lp_steam_} 与 {@code hp_steam_}，例如 {@code lp_steam_alloy_smelter}。</p>
 *
 * <table border="1">
 *     <caption>蒸汽参数</caption>
 *     <tr><th>版本</th><th>ID 前缀</th><th>蒸汽/电量换算</th><th>配方时间倍率</th></tr>
 *     <tr><td>低压</td><td>{@code lp_steam}</td><td>1 mB = 1 FE</td><td>×2</td></tr>
 *     <tr><td>高压</td><td>{@code hp_steam}</td><td>2 mB = 1 FE</td><td>×0.5</td></tr>
 * </table>
 *
 * <p>两种版本的蒸汽缓存都是 16000 mB，见 {@link c_y.meknh_core.common.data.voltage.VoltageTier#ULV}。</p>
 */
public enum SteamVariant implements StringRepresentable {

    /**
     * 低压蒸汽：消耗慢、处理慢。
     */
    LOW("lp_steam", 1.0D, 2.0D),

    /**
     * 高压蒸汽：同样电量要烧两倍蒸汽，但处理时间减半。
     */
    HIGH("hp_steam", 2.0D, 0.5D);

    public static final List<SteamVariant> ALL = List.of(values());

    public static final Codec<SteamVariant> CODEC = Codec.STRING.xmap(SteamVariant::byName, SteamVariant::getSerializedName);

    private final String idPrefix;
    private final double steamPerFe;
    private final double timeMultiplier;

    SteamVariant(String idPrefix, double steamPerFe, double timeMultiplier) {
        this.idPrefix = idPrefix;
        this.steamPerFe = steamPerFe;
        this.timeMultiplier = timeMultiplier;
    }

    @Override
    public String getSerializedName() {
        return this.idPrefix;
    }

    /**
     * 机器内部 ID 的前缀，例如 {@code lp_steam_} 与 {@code hp_steam_}。
     */
    public String idPrefix() {
        return this.idPrefix;
    }

    /**
     * 每 1 FE 需要消耗多少 mB 蒸汽。
     */
    public double steamPerFe() {
        return this.steamPerFe;
    }

    /**
     * 配方时间倍率：低压 ×2，高压 ×0.5。
     */
    public double timeMultiplier() {
        return this.timeMultiplier;
    }

    /**
     * 把每 tick 的能量（FE/t）换算成蒸汽消耗（mB/t）。
     */
    public long steamFor(long fePerTick) {
        return Math.max(0L, (long) Math.ceil(fePerTick * this.steamPerFe));
    }

    public static SteamVariant find(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (SteamVariant variant : values()) {
            if (variant.idPrefix.equals(normalized)) {
                return variant;
            }
        }
        return null;
    }

    public static SteamVariant byName(String name) {
        SteamVariant variant = find(name);
        return variant == null ? LOW : variant;
    }
}
