package c_y.meknh_core.api.machine;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 一组槽位：类型 + 数量 + 每个槽位的堆叠上限（{@code 批量注册机器.txt}）。
 *
 * <p>JSON 写法：</p>
 *
 * <pre>{@code
 * "input": [
 *   { "type": "item",  "slots": 2, "stack_limit": 64 },
 *   { "type": "fluid", "slots": 1, "stack_limit": 8 }
 * ]
 * }</pre>
 *
 * <p>堆叠上限对物品是「每格多少个」，对流体/化学品是「每槽多少桶」，
 * 换算成容量时乘 1000 mB。多个同类型槽位会合并成同一个罐，
 * 容量 = 槽位数 × 每槽上限 × 1000 mB。</p>
 */
public record MachineSlotGroup(MachineSlotType type, int slots, int stackLimit) {

    /**
     * 默认每个槽位的堆叠上限。
     */
    public static final int DEFAULT_STACK_LIMIT = 64;

    /**
     * 一桶 = 1000 mB。
     */
    public static final int BUCKET = 1000;

    public MachineSlotGroup {
        if (type == null) {
            throw new IllegalArgumentException("Machine slot group requires a type");
        }
        if (slots < 1) {
            throw new IllegalArgumentException("Machine slot group '" + type.getSerializedName() + "': slots must be at least 1");
        }
        if (stackLimit < 1) {
            throw new IllegalArgumentException("Machine slot group '" + type.getSerializedName() + "': stack_limit must be at least 1");
        }
    }

    /**
     * 该组占用的物品槽数量。
     */
    public int itemSlots() {
        return this.type.isItem() ? this.slots : 0;
    }

    /**
     * 该组提供的流体容量（mB）。
     */
    public long fluidCapacity() {
        return this.type.isFluid() ? (long) this.slots * this.stackLimit * BUCKET : 0L;
    }

    /**
     * 该组提供的化学品容量（mB）。
     */
    public long chemicalCapacity() {
        return this.type.isChemical() ? (long) this.slots * this.stackLimit * BUCKET : 0L;
    }

    /**
     * 解析一组槽位描述。
     *
     * @param machineId 机器 ID，仅用于报错
     * @param field     字段名（{@code input} / {@code output}）
     * @param json      槽位对象
     */
    public static MachineSlotGroup parse(ResourceLocation machineId, String field, JsonObject json) {
        if (!json.has("type")) {
            throw new IllegalArgumentException("Machine " + machineId + ": '" + field + "' entry needs a 'type'");
        }
        MachineSlotType type = MachineSlotType.find(json.get("type").getAsString());
        if (type == null) {
            throw new IllegalArgumentException("Machine " + machineId + ": unknown slot type '" + json.get("type").getAsString()
                    + "' in '" + field + "' (expected item/fluid/gas/mek)");
        }
        int slots = json.has("slots") ? json.get("slots").getAsInt() : 1;
        int stackLimit = json.has("stack_limit") ? json.get("stack_limit").getAsInt() : DEFAULT_STACK_LIMIT;
        if (slots < 1) {
            throw new IllegalArgumentException("Machine " + machineId + ": '" + field + "." + type.getSerializedName() + ".slots' must be at least 1");
        }
        if (stackLimit < 1) {
            throw new IllegalArgumentException("Machine " + machineId + ": '" + field + "." + type.getSerializedName() + ".stack_limit' must be at least 1");
        }
        return new MachineSlotGroup(type, slots, Mth.clamp(stackLimit, 1, 1_000_000));
    }
}
