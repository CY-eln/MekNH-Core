package c_y.meknh_core.common.pipe;

import c_y.meknh_core.MekNHCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组“mek 槽位”管道的定义（{@code data/addon/pipe/mek/<id>.json}）。
 *
 * <p>“mek 槽位”= 管道内部缓冲：同一格既能装流体也能装化学品（二选一）。</p>
 *
 * <pre>{@code
 * {
 *   "id": "bronze_fluid_pipe",  // 可省略，默认文件名
 *   "capacity": 2000,           // 缓存 mB
 *   "transfer": 250             // 每 tick 传输 mB
 * }
 * }</pre>
 */
public record MekPipeDefinition(ResourceLocation id, int capacity, int transfer) {

    public MekPipeDefinition {
        if (capacity < 1) {
            throw new IllegalArgumentException("Mek pipe " + id + ": capacity must be at least 1");
        }
        if (transfer < 1) {
            throw new IllegalArgumentException("Mek pipe " + id + ": transfer must be at least 1");
        }
    }

    /**
     * 空贴图路径：故意指向不存在的纹理，先用紫黑格占位。
     */
    public ResourceLocation texture() {
        return this.id.withPath("block/pipe/mek/" + this.id.getPath());
    }

    public static MekPipeDefinition parse(ResourceLocation fileId, JsonObject json) {
        ResourceLocation id = fileId;
        if (json.has("id") && json.get("id").isJsonPrimitive()) {
            String raw = json.get("id").getAsString().trim();
            ResourceLocation parsed = raw.contains(":")
                    ? ResourceLocation.tryParse(raw)
                    : ResourceLocation.tryBuild(MekNHCore.MODID, raw);
            if (parsed == null) {
                throw new IllegalArgumentException("Mek pipe " + fileId + ": invalid id '" + raw + "'");
            }
            id = parsed;
        }
        int capacity = readInt(json, "capacity");
        int transfer = readInt(json, "transfer");
        return new MekPipeDefinition(id, capacity, transfer);
    }

    private static int readInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Mek pipe: missing '" + key + "'");
        }
        return element.getAsInt();
    }
}
