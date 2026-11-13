package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.recipe.MachineRecipe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
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
 */
public record MultiblockDefinition(ResourceLocation id, List<MachineLevel> levels,
                                   MultiblockStructure structure, int time, double energy) {

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
            throw new IllegalArgumentException("Multiblock " + id + ": 'level' must contain at least one electric tier");
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

        List<MachineLevel> levels = MachineLevel.parse(id, json.get("level"));
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

        return new MultiblockDefinition(id, levels, structure, Math.max(1, time), energy);
    }
}
