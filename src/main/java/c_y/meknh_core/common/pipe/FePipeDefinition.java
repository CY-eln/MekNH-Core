package c_y.meknh_core.common.pipe;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * 一根 FE 电缆的数据定义（{@code data/addon/pipe/fe/<id>.json}）。
 *
 * <p>JSON 字段：</p>
 * <pre>{@code
 * {
 *   "id": "copper_cable",      // 可省略；省略时用文件名
 *   "level": "MV",             // 最高可承受电压（ULV/LV/MV/HV/EV/IV）
 *   "Amperage": 2,             // 每 tick 传输的能量包数量
 *   "textures": "meknh_core:block/pipe/fe/copper_cable" // 可省略，默认按 id 推
 * }
 * }</pre>
 *
 * <p>包模型：1 个能量包 = 电压对应的 FE；电缆每 tick 最多传 {@code Amperage} 个包。</p>
 *
 * @param tier     电缆等级
 * @param amperage 每 tick 最多传输的能量包数
 * @param texture  电缆贴图（方块纹理）
 */
public record FePipeDefinition(ResourceLocation id, VoltageTier tier, int amperage, ResourceLocation texture) {

    public static final int DEFAULT_AMPERAGE = 1;
    public static final int MAX_AMPERAGE = 64;

    public FePipeDefinition {
        if (tier.isSteam()) {
            throw new IllegalArgumentException("FE cable " + id + ": level must not be a steam tier");
        }
        if (amperage < 1 || amperage > MAX_AMPERAGE) {
            throw new IllegalArgumentException("FE cable " + id + ": Amperage must be between 1 and " + MAX_AMPERAGE);
        }
    }

    /**
     * 每 tick 最大传输量（FE）。
     */
    public long maxTransfer() {
        return this.tier.voltage() * this.amperage;
    }

    public static FePipeDefinition parse(ResourceLocation fileId, JsonObject json) {
        ResourceLocation id = fileId;
        if (json.has("id") && json.get("id").isJsonPrimitive()) {
            String raw = json.get("id").getAsString().trim();
            ResourceLocation parsed = raw.contains(":")
                    ? ResourceLocation.tryParse(raw)
                    : ResourceLocation.tryBuild(fileId.getNamespace(), raw);
            if (parsed == null) {
                throw new IllegalArgumentException("FE cable " + fileId + ": invalid 'id' value '" + raw + "'");
            }
            id = parsed;
        }

        JsonElement levelElement = json.get("level");
        if (levelElement == null || !levelElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("FE cable " + id + ": missing 'level'");
        }
        String levelName = levelElement.getAsString().trim().toUpperCase(Locale.ROOT);
        VoltageTier tier = VoltageTier.find(levelName.toLowerCase(Locale.ROOT));
        if (tier == null) {
            tier = VoltageTier.find(levelName);
        }
        if (tier == null) {
            throw new IllegalArgumentException("FE cable " + id + ": unknown level '" + levelName + "'");
        }

        int amperage = DEFAULT_AMPERAGE;
        JsonElement ampElement = json.has("Amperage") ? json.get("Amperage")
                : json.get("amperage");
        if (ampElement != null && ampElement.isJsonPrimitive()) {
            amperage = ampElement.getAsInt();
        }

        ResourceLocation texture = id.withPath("block/pipe/fe/" + id.getPath());
        if (json.has("textures") && json.get("textures").isJsonPrimitive()) {
            String raw = json.get("textures").getAsString().trim();
            if (!raw.isEmpty()) {
                ResourceLocation parsed = raw.contains(":")
                        ? ResourceLocation.tryParse(raw)
                        : ResourceLocation.tryBuild(MekNHCore.MODID, raw);
                if (parsed == null) {
                    throw new IllegalArgumentException("FE cable " + id + ": invalid textures value '" + raw + "'");
                }
                texture = parsed;
            }
        }
        return new FePipeDefinition(id, tier, amperage, texture);
    }
}
