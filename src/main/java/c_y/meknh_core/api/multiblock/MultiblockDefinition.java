package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.recipe.MachineRecipe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 一份多方块定义，对应 {@code data/addon/multiblock/<ID>.json}。
 *
 * <pre>{@code
 * {
 *   "id": "my_multiblock",
 *   "level": "all",
 *   "time": 200,
 *   "energy": 1.0,
 *   "结构": [
 *     ["aaa", "a@a", "aaa"],
 *     ["aaa", "aHa", "aaa"]
 *   ],
 *   "组成": {
 *     "a": "meknh_core:lv_machine_casing",
 *     "H": "part:item_input"
 *   }
 * }
 * }</pre>
 *
 * <p>一个定义会在 {@link #levels()} 列出的每个电压等级上各注册一个控制器方块，
 * 命名规则与单方块机器一致（{@code lv_<id>}）。</p>
 *
 * @param id        多方块 ID，同时是配方的 {@code id}
 * @param levels    要注册的控制器等级（电力 LV..IV，或蒸汽 lp_steam/hp_steam）
 * @param structure 结构
 * @param time      默认配方时间（tick）
 * @param energy    默认每 tick 电流（A）
 * @param noVoltage 无电压模式：不注册等级前缀、不需要能源仓、不超频
 */
public record MultiblockDefinition(ResourceLocation id, List<MachineLevel> levels,
                                   MultiblockStructure structure, int time, double energy,
                                   boolean noVoltage) {

    /**
     * 结构 JSON 的时间默认值（tick）。
     */
    public static final int DEFAULT_TIME = 200;

    /**
     * 结构 JSON 的电流默认值（A/t）。
     */
    public static final double DEFAULT_ENERGY = 1.0D;

    public MultiblockDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(structure, "structure");
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Multiblock " + id + ": 'level' must contain at least one controller variant");
        }
        if (noVoltage && levels.size() != 1) {
            throw new IllegalArgumentException("Multiblock " + id
                    + ": no-voltage mode must register exactly one controller");
        }
        if (time < 1) {
            throw new IllegalArgumentException("Multiblock " + id + ": 'time' must be at least 1 tick");
        }
        if (energy < 0.0D) {
            throw new IllegalArgumentException("Multiblock " + id + ": 'energy' must not be negative");
        }
        levels = List.copyOf(levels);
    }

    /**
     * 由配方或结构默认值解析实际处理时间（tick）。
     */
    public int resolveTime(@Nullable MachineRecipe recipe) {
        return recipe == null || recipe.time() < 0 ? this.time : recipe.time();
    }

    /**
     * 由配方或结构默认值解析实际电流（A/t）。
     */
    public double resolveEnergy(@Nullable MachineRecipe recipe) {
        return recipe == null || recipe.energy() < 0.0D ? this.energy : recipe.energy();
    }

    /**
     * 解析多方块 JSON。
     *
     * @param fileId 由文件名推导的 ID
     * @param json   结构 JSON
     */
    public static MultiblockDefinition parse(ResourceLocation fileId, JsonObject json) {
        ResourceLocation id = fileId;
        if (json.has("id") && json.get("id").isJsonPrimitive()) {
            String raw = json.get("id").getAsString().trim();
            ResourceLocation parsed = raw.contains(":")
                    ? ResourceLocation.tryParse(raw)
                    : ResourceLocation.tryBuild(fileId.getNamespace(), raw);
            if (parsed == null) {
                throw new IllegalArgumentException("Multiblock " + fileId + ": invalid 'id' value '" + raw + "'");
            }
            id = parsed;
        }

        JsonElement levelElement = json.get("level");
        boolean noVoltage = isNoVoltage(json, levelElement);
        List<MachineLevel> levels = noVoltage
                ? List.of(MachineLevel.electric(VoltageTier.LV))
                : MachineLevel.parse(id, levelElement);
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Multiblock " + id + ": 'level' resolved to no controller variant");
        }

        MultiblockStructure structure = MultiblockStructure.parse(id, json);

        int time = DEFAULT_TIME;
        if (json.has("time")) {
            JsonElement element = json.get("time");
            time = element.getAsJsonPrimitive().isNumber()
                    ? element.getAsInt()
                    : MachineRecipe.parseTicks(element.getAsString());
        }
        double energy = json.has("energy") ? json.get("energy").getAsDouble() : DEFAULT_ENERGY;

        return new MultiblockDefinition(id, levels, structure, Math.max(1, time), energy, noVoltage);
    }

    /**
     * 判断是否“无电压等级”模式。
     *
     * <p>支持两种写法：{@code "level": "none"}（也接受 {@code no_voltage}、{@code 无}、{@code 无电压}、
     * 空字符串），或 {@code "无电压": true}。</p>
     */
    private static boolean isNoVoltage(JsonObject json, @Nullable JsonElement levelElement) {
        if (json.has("无电压") && json.get("无电压").isJsonPrimitive()
                && json.get("无电压").getAsJsonPrimitive().isBoolean()
                && json.get("无电压").getAsBoolean()) {
            return true;
        }
        if (levelElement == null || !levelElement.isJsonPrimitive()) {
            return false;
        }
        String value = levelElement.getAsString().trim().toLowerCase(Locale.ROOT);
        return value.isEmpty()
                || value.equals("none")
                || value.equals("no_voltage")
                || value.equals("novoltage")
                || value.equals("无")
                || value.equals("无电压");
    }
}
