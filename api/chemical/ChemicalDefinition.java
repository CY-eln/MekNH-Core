package c_y.meknh_core.api.chemical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Objects;
import mekanism.api.chemical.ChemicalBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 一份由数据包 JSON 描述的 Mekanism 化学品定义（含属性）。
 *
 * <p>每个化学品一个文件：{@code data/addon/chemicals/<名称>.json}。</p>
 *
 * <pre>{@code
 * {
 *   "id": "meknh_core:supercritical_cryo_ice_solution",
 *   "fluid": false,
 *   "texture": "meknh_core:liquid/supercritical_cryo_ice_solution",
 *   "tint": "#7FD4FF",
 *   "gaseous": false,
 *   "attributes": {
 *     "fuel": { "burn_time": 40, "energy": 705 },
 *     "radioactivity": { "radioactivity": 0.01 },
 *     "solid_tag": { "representation": "c:ores/copper" },
 *     "cooled_coolant": {
 *       "hot_variant": "mekanism:steam",
 *       "thermal_enthalpy": 10000000.0,
 *       "conductivity": 1.0
 *     },
 *     "heated_coolant": {
 *       "cool_variant": "mekanism:sodium",
 *       "thermal_enthalpy": 5.0,
 *       "conductivity": 0.4,
 *       "temperature": 100000.0
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>{@code fluid=true} 时不注册化学品，而是把该定义注册成一个新的 NeoForge 流体
 * （FluidType + source + flowing），属性保存到 {@link FluidAttributeRegistry}。
 * 流体 id 必须使用本模组命名空间。</p>
 *
 * <p>{@code attributes} 支持 Mekanism 的全部化学品数据映射：
 * {@code fuel}、{@code radioactivity}、{@code solid_tag}、{@code cooled_coolant}、
 * {@code heated_coolant}；{@code fuel.energy} 为每 tick 能量，也可用
 * {@code fuel.energy_density} 表示每 mB 总能量。</p>
 *
 * @param id            注册名/流体 id
 * @param fluid         是否把属性挂载到流体而不是注册化学品
 * @param texture       贴图位置
 * @param tint          RGB 颜色
 * @param gaseous       是否为气态
 * @param solid         固体物品标签（例如 {@code c:ores/copper}），可为空
 * @param fuel          燃料属性，可为空
 * @param cooledCoolant 冷却剂属性（输出热变体），可为空
 * @param heatedCoolant 加热冷却剂属性（输出冷变体），可为空
 * @param radioactivity 放射性，可为空
 */
public record ChemicalDefinition(ResourceLocation id, boolean fluid, ResourceLocation texture, int tint,
                                 boolean gaseous, @Nullable ResourceLocation solid,
                                 @Nullable FuelData fuel, @Nullable CoolantData cooledCoolant,
                                 @Nullable CoolantData heatedCoolant, @Nullable Double radioactivity) {

    /**
     * 未指定颜色时使用的默认值（纯白，表示不做着色）。
     */
    public static final int DEFAULT_TINT = 0xFFFFFF;

    private static final Logger LOGGER = LogUtils.getLogger();

    public ChemicalDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texture, "texture");
        tint &= 0xFFFFFF;
    }

    /**
     * 根据注册名推导默认贴图：{@code <命名空间>:liquid/<名称>}。
     */
    public static ResourceLocation defaultTexture(ResourceLocation id) {
        return id.withPath("liquid/" + id.getPath());
    }

    /**
     * 解析单个化学品 JSON。
     *
     * @param id   化学品注册名（由文件名推导）
     * @param json 化学品 JSON 对象
     * @return 解析后的定义
     * @throws IllegalArgumentException 当字段格式不合法时
     */
    public static ChemicalDefinition parse(ResourceLocation fileId, JsonObject json) {
        ResourceLocation id = json.has("id")
                ? parseResourceLocation(fileId, "id", json.get("id"))
                : fileId;
        boolean fluid = parseBoolean(fileId, "fluid", json.get("fluid"), false);
        JsonElement textureElement = first(json, "texture", "贴图");
        ResourceLocation texture = textureElement != null
                ? parseResourceLocation(fileId, "texture", textureElement)
                : defaultTexture(id);
        JsonElement tintElement = first(json, "tint", "颜色");
        int tint = tintElement != null ? parseTint(fileId, tintElement) : DEFAULT_TINT;
        boolean gaseous = parseBoolean(fileId, "gaseous", json.get("gaseous"), false);
        JsonObject attributes = firstObject(json, "attributes", "属性");
        ResourceLocation solid = parseSolidTag(fileId, json, attributes);

        FuelData fuel = null;
        CoolantData cooledCoolant = null;
        CoolantData heatedCoolant = null;
        Double radioactivity = null;

        if (attributes != null) {
            if (attributes.has("fuel")) {
                fuel = parseFuel(fileId, attributes.getAsJsonObject("fuel"), fluid);
            }
            if (attributes.has("radioactivity")) {
                radioactivity = parseRadioactivity(fileId, attributes.get("radioactivity"));
            }
            if (attributes.has("cooled_coolant")) {
                cooledCoolant = parseCoolant(fileId, "cooled_coolant", attributes.getAsJsonObject("cooled_coolant"), "hot_variant", 1.0D, false);
            }
            if (attributes.has("heated_coolant")) {
                heatedCoolant = parseCoolant(fileId, "heated_coolant", attributes.getAsJsonObject("heated_coolant"), "cool_variant", 0.4D, true);
            }
            for (String key : attributes.keySet()) {
                if (!key.equals("fuel") && !key.equals("radioactivity") && !key.equals("cooled_coolant")
                        && !key.equals("heated_coolant") && !key.equals("solid") && !key.equals("solid_tag")) {
                    LOGGER.warn("Chemical {}: unknown attribute '{}', ignored", fileId, key);
                }
            }
        }

        return new ChemicalDefinition(id, fluid, texture, tint, gaseous, solid, fuel, cooledCoolant, heatedCoolant, radioactivity);
    }

    private static JsonElement first(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                return json.get(key);
            }
        }
        return null;
    }

    private static JsonObject firstObject(JsonObject json, String... keys) {
        JsonElement element = first(json, keys);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean parseBoolean(ResourceLocation id, String field, JsonElement element, boolean fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            String value = primitive.getAsString().trim().toLowerCase(Locale.ROOT);
            if (value.equals("true") || value.equals("yes") || value.equals("是") || value.equals("1")) {
                return true;
            }
            if (value.equals("false") || value.equals("no") || value.equals("否") || value.equals("0")) {
                return false;
            }
        }
        throw new IllegalArgumentException("Chemical " + id + ": field '" + field + "' must be a boolean");
    }

    private static ResourceLocation parseSolidTag(ResourceLocation id, JsonObject json, @Nullable JsonObject attributes) {
        JsonElement element = attributes == null ? null : first(attributes, "solid", "solid_tag");
        if (element == null) {
            element = json.get("solid");
        }
        if (element == null) {
            return null;
        }
        if (element.isJsonObject()) {
            element = first(element.getAsJsonObject(), "representation", "tag");
        }
        return element == null ? null : parseResourceLocation(id, "solid", element);
    }

    private static Double parseRadioactivity(ResourceLocation id, JsonElement element) {
        if (element.isJsonObject()) {
            element = first(element.getAsJsonObject(), "radioactivity", "value");
        }
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Chemical " + id + ": 'radioactivity' must be a number or an object with 'radioactivity'");
        }
        double value = element.getAsDouble();
        if (value <= 0.0D) {
            throw new IllegalArgumentException("Chemical " + id + ": 'radioactivity' must be greater than zero");
        }
        return value;
    }

    /**
     * 化学品构造器，只包含贴图与颜色；属性由 {@link ChemicalDataMaps} 注入 Mekanism 数据映射。
     */
    public ChemicalBuilder baseBuilder() {
        return ChemicalBuilder.builder(texture).tint(tint);
    }

    private static FuelData parseFuel(ResourceLocation id, JsonObject json, boolean fluid) {
        int burnTime = json.has("burn_time") ? json.get("burn_time").getAsInt() : 1;
        if (burnTime < 1) {
            throw new IllegalArgumentException("Chemical " + id + ": 'fuel.burn_time' must be at least 1");
        }
        long energyPerTick;
        if (json.has("energy_density")) {
            long density = json.get("energy_density").getAsLong();
            if (density < 1L) {
                throw new IllegalArgumentException("Chemical " + id + ": 'fuel.energy_density' must be greater than zero");
            }
            energyPerTick = Math.max(1L, Math.round(density / (double) burnTime));
        } else if (json.has("energy")) {
            energyPerTick = json.get("energy").getAsLong();
            if (energyPerTick < 1L) {
                throw new IllegalArgumentException("Chemical " + id + ": 'fuel.energy' must be greater than zero");
            }
        } else if (fluid) {
            // 流体只要求燃烧时间；能量字段仅对化学品数据映射是必需的。
            energyPerTick = 0L;
        } else {
            throw new IllegalArgumentException("Chemical " + id + ": 'fuel.energy' or 'fuel.energy_density' is required");
        }
        return new FuelData(burnTime, energyPerTick);
    }

    private static CoolantData parseCoolant(ResourceLocation id, String type, JsonObject json,
                                            String variantKey, double defaultConductivity, boolean heated) {
        ResourceLocation variant = parseResourceLocation(id, type + "." + variantKey, json.get(variantKey));
        if (!json.has("thermal_enthalpy")) {
            throw new IllegalArgumentException("Chemical " + id + ": '" + type + ".thermal_enthalpy' is required");
        }
        double thermalEnthalpy = json.get("thermal_enthalpy").getAsDouble();
        if (thermalEnthalpy <= 0.0D) {
            throw new IllegalArgumentException("Chemical " + id + ": '" + type + ".thermal_enthalpy' must be greater than zero");
        }
        double conductivity = json.has("conductivity") ? json.get("conductivity").getAsDouble() : defaultConductivity;
        if (conductivity <= 0.0D || conductivity > 1.0D) {
            throw new IllegalArgumentException("Chemical " + id + ": '" + type + ".conductivity' must be in (0, 1]");
        }
        double temperature = 0.0D;
        if (heated) {
            temperature = json.has("temperature") ? json.get("temperature").getAsDouble() : 100_000.0D;
            if (temperature <= 0.0D || temperature > 1_000_000.0D) {
                throw new IllegalArgumentException("Chemical " + id + ": '" + type + ".temperature' must be in (0, 1000000]");
            }
        }
        return new CoolantData(variant, thermalEnthalpy, conductivity, temperature);
    }

    private static ResourceLocation parseResourceLocation(ResourceLocation id, String field, JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Chemical " + id + ": field '" + field + "' must be a string");
        }
        ResourceLocation location = ResourceLocation.tryParse(element.getAsString());
        if (location == null) {
            throw new IllegalArgumentException("Chemical " + id + ": invalid resource location in field '" + field + "': " + element.getAsString());
        }
        return location;
    }

    private static int parseTint(ResourceLocation id, JsonElement element) {
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Chemical " + id + ": field 'tint' must be a color string or an integer");
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() & 0xFFFFFF;
        }
        String value = element.getAsString().trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x")) {
            value = value.substring(2);
        }
        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Chemical " + id + ": invalid color in field 'tint': " + element.getAsString(), e);
        }
    }

    /**
     * 燃料属性：{@code burn_time} 为燃烧时长（tick），{@code energy} 为每 tick 能量。
     */
    public record FuelData(int burnTime, long energyPerTick) {
    }

    /**
     * 冷却剂属性。
     *
     * @param variant         冷却/加热后的产物
     * @param thermalEnthalpy 热焓
     * @param conductivity    导热系数
     * @param temperature     温度（仅加热冷却剂使用）
     */
    public record CoolantData(ResourceLocation variant, double thermalEnthalpy, double conductivity, double temperature) {
    }
}
