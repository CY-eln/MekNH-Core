package c_y.meknh_core.api.machine;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.recipe.MachineRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 一份机器定义，对应 {@code data/addon/machine/<机器ID>.json}（{@code 批量注册机器.txt}）。
 *
 * <pre>{@code
 * {
 *   "id": "alloy_smelter",
 *   "level": "all",
 *   "input":  [ { "type": "item", "slots": 2, "stack_limit": 64 } ],
 *   "output": [ { "type": "item", "slots": 1, "stack_limit": 64 } ],
 *   "time": "400",
 *   "energy": 0.25,
 *   "overlay": "alloy_smelter"
 * }
 * }</pre>
 *
 * <p>一个定义会在 {@link #levels()} 列出的每个等级上各注册一个机器方块，
 * 内部 ID 由 {@code c_y.meknh_core.decor.description.MachineNames} 拼出。</p>
 *
 * @param id      机器 ID（命名空间固定为本模组）
 * @param levels  要注册的机器等级
 * @param inputs  输入槽位组
 * @param outputs 输出槽位组
 * @param time    默认配方时间（tick），配方 JSON 写了 {@code time} 时以配方为准
 * @param energy  默认每 tick 电流（A），配方 JSON 写了 {@code energy} 时以配方为准
 * @param overlay 正面覆盖层名，对应 {@code textures/block/machine/<overlay>/}
 */
public record MachineDefinition(ResourceLocation id, List<MachineLevel> levels, List<MachineSlotGroup> inputs,
                                List<MachineSlotGroup> outputs, int time, double energy, String overlay) {

    /**
     * 机器 JSON 的时间默认值（tick）。
     */
    public static final int DEFAULT_TIME = 200;

    /**
     * 机器 JSON 的电流默认值（A/t）。
     */
    public static final double DEFAULT_ENERGY = 1.0D;

    public MachineDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Machine " + id + ": 'level' must contain at least one machine variant");
        }
        if (time < 1) {
            throw new IllegalArgumentException("Machine " + id + ": 'time' must be at least 1 tick");
        }
        if (energy < 0.0D) {
            throw new IllegalArgumentException("Machine " + id + ": 'energy' must not be negative");
        }
        if (overlay == null || overlay.isBlank()) {
            throw new IllegalArgumentException("Machine " + id + ": 'overlay' must not be blank");
        }
        levels = List.copyOf(levels);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /**
     * 物品输入槽数量。
     */
    public int itemInputSlots() {
        return this.inputs.stream().mapToInt(MachineSlotGroup::itemSlots).sum();
    }

    /**
     * 物品输出槽数量。
     */
    public int itemOutputSlots() {
        return this.outputs.stream().mapToInt(MachineSlotGroup::itemSlots).sum();
    }

    /**
     * 每个物品输入槽的堆叠上限，顺序与槽位顺序一致。
     */
    public List<Integer> itemInputLimits() {
        List<Integer> limits = new ArrayList<>();
        for (MachineSlotGroup group : this.inputs) {
            if (group.itemSlots() > 0) {
                for (int slot = 0; slot < group.itemSlots(); slot++) {
                    limits.add(group.stackLimit());
                }
            }
        }
        return limits;
    }

    /**
     * 每个物品输出槽的堆叠上限。
     */
    public List<Integer> itemOutputLimits() {
        List<Integer> limits = new ArrayList<>();
        for (MachineSlotGroup group : this.outputs) {
            if (group.itemSlots() > 0) {
                for (int slot = 0; slot < group.itemSlots(); slot++) {
                    limits.add(group.stackLimit());
                }
            }
        }
        return limits;
    }

    /**
     * 输入流体容量（mB），0 表示没有流体输入。
     */
    public long fluidInputCapacity() {
        return this.inputs.stream().mapToLong(MachineSlotGroup::fluidCapacity).sum();
    }

    /**
     * 输入化学品容量（mB）。
     */
    public long chemicalInputCapacity() {
        return this.inputs.stream().mapToLong(MachineSlotGroup::chemicalCapacity).sum();
    }

    /**
     * 输出流体容量（mB）。
     */
    public long fluidOutputCapacity() {
        return this.outputs.stream().mapToLong(MachineSlotGroup::fluidCapacity).sum();
    }

    /**
     * 输出化学品容量（mB）。
     */
    public long chemicalOutputCapacity() {
        return this.outputs.stream().mapToLong(MachineSlotGroup::chemicalCapacity).sum();
    }

    /**
     * 由配方或机器默认值解析实际处理时间（tick）。
     */
    public int resolveTime(@Nullable MachineRecipe recipe) {
        return recipe == null || recipe.time() < 0 ? this.time : recipe.time();
    }

    /**
     * 由配方或机器默认值解析实际电流（A/t）。
     */
    public double resolveEnergy(@Nullable MachineRecipe recipe) {
        return recipe == null || recipe.energy() < 0.0D ? this.energy : recipe.energy();
    }

    /**
     * 解析机器 JSON。
     *
     * @param fileId 由文件名推导的机器 ID
     * @param json   机器 JSON
     */
    public static MachineDefinition parse(ResourceLocation fileId, JsonObject json) {
        ResourceLocation id = fileId;
        if (json.has("id")) {
            String raw = json.get("id").getAsString().trim();
            ResourceLocation parsed = raw.contains(":") ? ResourceLocation.tryParse(raw) : ResourceLocation.tryBuild(fileId.getNamespace(), raw);
            if (parsed == null) {
                throw new IllegalArgumentException("Machine " + fileId + ": invalid 'id' value '" + raw + "'");
            }
            id = parsed;
        }

        List<MachineLevel> levels = MachineLevel.parse(id, json.get("level"));
        List<MachineSlotGroup> inputs = parseSlots(id, "input", json.get("input"));
        List<MachineSlotGroup> outputs = parseSlots(id, "output", json.get("output"));

        int time = DEFAULT_TIME;
        if (json.has("time")) {
            JsonElement element = json.get("time");
            time = element.getAsJsonPrimitive().isNumber()
                    ? element.getAsInt()
                    : MachineRecipe.parseTicks(element.getAsString());
        }
        double energy = json.has("energy") ? json.get("energy").getAsDouble() : DEFAULT_ENERGY;
        String overlay = json.has("overlay") ? json.get("overlay").getAsString() : id.getPath();

        return new MachineDefinition(id, levels, inputs, outputs, Math.max(1, time), energy, overlay);
    }

    private static List<MachineSlotGroup> parseSlots(ResourceLocation machineId, String field, @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Machine " + machineId + ": '" + field + "' must be an array of slot groups");
        }
        JsonArray array = element.getAsJsonArray();
        List<MachineSlotGroup> groups = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()) {
                throw new IllegalArgumentException("Machine " + machineId + ": every '" + field + "' entry must be an object");
            }
            groups.add(MachineSlotGroup.parse(machineId, field, entry.getAsJsonObject()));
        }
        return groups;
    }
}
