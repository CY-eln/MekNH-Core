package c_y.meknh_core.common.data.voltage;

import c_y.meknh_core.common.machine.steam.SteamVariant;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 一个具体机器“版本”的等级：要么是某个电压的电力机器，要么是低压/高压蒸汽机器。
 *
 * <p>机器 JSON 的 {@code level} 字段描述的是“要批量注册哪些版本”，
 * 支持下面几种写法（{@code 批量注册机器.txt}）：</p>
 *
 * <pre>{@code
 * "level": "all"                          // 两个蒸汽版本 + LV/MV/HV/EV/IV
 * "level": ["LV", "HV"]                   // 只注册 LV 与 HV
 * "level": [{"from": "LV", "to": "EV"}]   // 注册 LV 到 EV 之间全部等级
 * "level": ["LV..EV"]                     // 上面的简写
 * "level": "ULV"                          // 只注册低压与高压两个蒸汽版本
 * }</pre>
 *
 * <p>电力机器的内部 ID 是 {@code <电压>_<机器ID>}（例如 {@code lv_alloy_smelter}），
 * 蒸汽机器是 {@code lp_steam_<机器ID>} / {@code hp_steam_<机器ID>}。</p>
 */
public record MachineLevel(VoltageTier voltage, @Nullable SteamVariant steam) implements Comparable<MachineLevel> {

    public MachineLevel {
        if (voltage == null) {
            throw new IllegalArgumentException("Machine level requires a voltage tier");
        }
        if (steam != null && voltage != VoltageTier.ULV) {
            throw new IllegalArgumentException("Steam machine level must use ULV, got " + voltage.getSerializedName());
        }
        if (steam == null && voltage == VoltageTier.ULV) {
            throw new IllegalArgumentException("ULV is the steam tier; pass a SteamVariant for a steam machine");
        }
    }

    /**
     * 电力机器等级。
     */
    public static MachineLevel electric(VoltageTier tier) {
        return new MachineLevel(tier, null);
    }

    /**
     * 蒸汽机器等级。
     */
    public static MachineLevel steam(SteamVariant variant) {
        return new MachineLevel(VoltageTier.ULV, variant);
    }

    /**
     * 全部等级：低压蒸汽、高压蒸汽，然后 LV/MV/HV/EV/IV。
     */
    public static List<MachineLevel> all() {
        List<MachineLevel> levels = new ArrayList<>();
        for (SteamVariant variant : SteamVariant.ALL) {
            levels.add(steam(variant));
        }
        for (VoltageTier tier : VoltageTier.ELECTRIC) {
            levels.add(electric(tier));
        }
        return List.copyOf(levels);
    }

    /**
     * 该等级在机器内部 ID 中的前缀，例如 {@code lp_steam}、{@code lv}。
     */
    public String idPrefix() {
        return this.steam != null ? this.steam.idPrefix() : this.voltage.getSerializedName();
    }

    public boolean isSteam() {
        return this.steam != null;
    }

    /**
     * 该版本能接受的配方最低等级。蒸汽机器可以执行 ULV 与 LV 配方，更高等级不行。
     */
    public VoltageTier recipeTier() {
        return this.steam != null ? VoltageTier.LV : this.voltage;
    }

    /**
     * 机器的 FE 缓存上限；蒸汽机器为 0。
     */
    public long energyCapacity() {
        return this.voltage.energyCapacity();
    }

    /**
     * 机器的蒸汽缓存上限（mB）；电力机器为 0。
     */
    public long steamCapacity() {
        return this.voltage.steamCapacity();
    }

    /**
     * 配方时间倍率：电力机器为 1，低压蒸汽 ×2，高压蒸汽 ×0.5。
     */
    public double timeMultiplier() {
        return this.steam == null ? 1.0D : this.steam.timeMultiplier();
    }

    /**
     * 每 tick 能量（FE/t）换算成蒸汽消耗（mB/t）；电力机器返回 0。
     */
    public long steamPerTick(long fePerTick) {
        return this.steam == null ? 0L : this.steam.steamFor(fePerTick);
    }

    /**
     * 该版本的并行处理数：LV=1、MV=2、HV=4，之后每级翻倍；蒸汽为 1。
     */
    public int parallel() {
        return this.voltage.parallel();
    }

    /**
     * 排序：先按电压序号，再让同一等级的蒸汽版本排在电力版本之前。
     */
    @Override
    public int compareTo(MachineLevel other) {
        int byVoltage = Integer.compare(this.voltage.level(), other.voltage.level());
        if (byVoltage != 0) {
            return byVoltage;
        }
        int left = this.steam == null ? 1 : this.steam.ordinal();
        int right = other.steam == null ? 1 : other.steam.ordinal();
        return Integer.compare(left, right);
    }

    /**
     * 解析机器 JSON 的 {@code level} 字段。
     *
     * @param machineId 机器 ID，仅用于报错信息
     * @param element   {@code level} 字段；为 {@code null} 时按 {@code "all"} 处理
     * @return 去重并排好序的等级列表，至少包含一项
     */
    public static List<MachineLevel> parse(ResourceLocation machineId, @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return all();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            if (value.isEmpty() || value.equalsIgnoreCase(VoltageTier.ALL)) {
                return all();
            }
            return normalize(machineId, expand(machineId, value));
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Machine " + machineId + ": 'level' must be a string, an array or an object");
        }
        JsonArray array = element.getAsJsonArray();
        List<MachineLevel> levels = new ArrayList<>();
        for (JsonElement entry : array) {
            if (entry.isJsonObject()) {
                levels.addAll(expandRange(machineId, entry.getAsJsonObject()));
            } else {
                levels.addAll(expand(machineId, entry.getAsString().trim()));
            }
        }
        if (levels.isEmpty()) {
            return all();
        }
        return normalize(machineId, levels);
    }

    private static List<MachineLevel> normalize(ResourceLocation machineId, List<MachineLevel> levels) {
        List<MachineLevel> unique = new ArrayList<>(new LinkedHashSet<>(levels));
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Machine " + machineId + ": 'level' resolved to no machine variant");
        }
        unique.sort(Comparator.naturalOrder());
        return List.copyOf(unique);
    }

    private static List<MachineLevel> expand(ResourceLocation machineId, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals(VoltageTier.ALL)) {
            return all();
        }
        // 支持 "LV..EV" / "LV-EV" 这种范围简写
        int separator = normalized.indexOf("..");
        if (separator > 0) {
            return expandRange(machineId, normalized.substring(0, separator).trim(), normalized.substring(separator + 2).trim());
        }
        separator = normalized.indexOf('-');
        if (separator > 0) {
            return expandRange(machineId, normalized.substring(0, separator).trim(), normalized.substring(separator + 1).trim());
        }
        return single(machineId, normalized);
    }

    private static List<MachineLevel> single(ResourceLocation machineId, String name) {
        VoltageTier tier = VoltageTier.find(name);
        if (tier != null) {
            if (tier == VoltageTier.ULV) {
                // "ULV" 代表蒸汽等级，展开成低压与高压两个版本
                return List.of(steam(SteamVariant.LOW), steam(SteamVariant.HIGH));
            }
            return List.of(electric(tier));
        }
        SteamVariant steam = SteamVariant.find(name);
        if (steam != null) {
            return List.of(steam(steam));
        }
        throw new IllegalArgumentException("Machine " + machineId + ": unknown level '" + name + "'");
    }

    private static List<MachineLevel> expandRange(ResourceLocation machineId, JsonObject object) {
        if (!object.has("from") || !object.has("to")) {
            throw new IllegalArgumentException("Machine " + machineId + ": level range needs 'from' and 'to'");
        }
        return expandRange(machineId, object.get("from").getAsString().trim(), object.get("to").getAsString().trim());
    }

    private static List<MachineLevel> expandRange(ResourceLocation machineId, String fromName, String toName) {
        VoltageTier from = VoltageTier.find(fromName);
        VoltageTier to = VoltageTier.find(toName);
        if (from == null || to == null) {
            throw new IllegalArgumentException("Machine " + machineId + ": level range '" + fromName + "'..'" + toName + "' contains an unknown tier");
        }
        // ULV 是蒸汽等级，范围里只展开成两个蒸汽版本
        boolean includeSteam = from == VoltageTier.ULV || to == VoltageTier.ULV;
        int min = Math.min(from.level(), to.level());
        int max = Math.max(from.level(), to.level());
        List<MachineLevel> levels = new ArrayList<>();
        if (includeSteam) {
            levels.addAll(List.of(steam(SteamVariant.LOW), steam(SteamVariant.HIGH)));
        }
        for (VoltageTier tier : VoltageTier.ELECTRIC) {
            if (tier.level() >= min && tier.level() <= max) {
                levels.add(electric(tier));
            }
        }
        return levels;
    }
}
