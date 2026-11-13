package c_y.meknh_core.api.generator.boiler;

import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * 数据驱动锅炉定义，JSON 位于 {@code data/addon/generator/boiler/*.json}。
 */
public record BoilerDefinition(ResourceLocation id, Type type, String overlay, int itemInputSlots,
                               int fluidInputSlots, int chemicalInputSlots, int steamCapacity,
                               int steamPerTick, int highPressureMultiplier, String level,
                               int temperatureMax) {

    public enum Type {
        SOLID, LIQUID, SOLAR;

        public static Type parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public BoilerDefinition {
        if (steamCapacity < 1) {
            throw new IllegalArgumentException("Boiler " + id + ": steam_capacity must be at least 1");
        }
        if (steamPerTick < 1) {
            throw new IllegalArgumentException("Boiler " + id + ": steam_per_tick must be at least 1");
        }
        if (highPressureMultiplier < 1) {
            throw new IllegalArgumentException("Boiler " + id + ": high_pressure_multiplier must be at least 1");
        }
        if (temperatureMax < 1) {
            temperatureMax = 1000;
        }
    }

    public static BoilerDefinition parse(ResourceLocation id, JsonObject json) {
        String level = json.has("level") ? json.get("level").getAsString() : "ULV";
        Type type = json.has("type") ? Type.parse(json.get("type").getAsString()) : null;

        int itemSlots = 0;
        int fluidSlots = 0;
        int chemicalSlots = 0;
        int temperatureMax = json.has("temperature_max") ? json.get("temperature_max").getAsInt() : 1000;
        int capacityFromOutput = 0;

        if (json.has("input") && json.get("input").isJsonArray()) {
            for (var entry : json.getAsJsonArray("input")) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject group = entry.getAsJsonObject();
                String groupType = group.has("type") ? group.get("type").getAsString().toLowerCase(Locale.ROOT) : "item";
                int slots = group.has("slots") ? group.get("slots").getAsInt() : 1;
                int limit = group.has("stack_limit") ? group.get("stack_limit").getAsInt() : 64;
                switch (groupType) {
                    case "item" -> itemSlots += slots;
                    case "fluid" -> fluidSlots += slots;
                    case "mek" -> {
                        // 复合槽：同时支持流体与化学品输入。
                        fluidSlots += slots;
                        chemicalSlots += slots;
                    }
                    case "gas", "chemical", "化学品", "化学" -> chemicalSlots += slots;
                    case "heat", "temperature", "temperature_slot", "温度" -> temperatureMax = Math.max(1, limit);
                    default -> {
                    }
                }
            }
        } else {
            itemSlots = json.has("item_input_slots") ? json.get("item_input_slots").getAsInt()
                    : (type == Type.SOLID ? 1 : 0);
            fluidSlots = json.has("fluid_input_slots") ? json.get("fluid_input_slots").getAsInt()
                    : (type == Type.LIQUID ? 1 : 0);
            chemicalSlots = json.has("chemical_input_slots") ? json.get("chemical_input_slots").getAsInt()
                    : (type == Type.LIQUID ? 1 : 0);
        }

        if (json.has("output") && json.get("output").isJsonArray()) {
            for (var entry : json.getAsJsonArray("output")) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject group = entry.getAsJsonObject();
                String groupType = group.has("type") ? group.get("type").getAsString().toLowerCase(Locale.ROOT) : "";
                int slots = group.has("slots") ? group.get("slots").getAsInt() : 1;
                int limit = group.has("stack_limit") ? group.get("stack_limit").getAsInt() : 1;
                if (groupType.equals("gas") || groupType.equals("chemical")
                        || groupType.equals("化学品") || groupType.equals("化学")) {
                    capacityFromOutput += slots * limit * 1000;
                }
            }
        }

        if (type == null) {
            type = itemSlots > 0 ? Type.SOLID : (fluidSlots > 0 || chemicalSlots > 0 ? Type.LIQUID : Type.SOLAR);
        }
        String overlay = json.has("overlay") ? json.get("overlay").getAsString() : switch (type) {
            case SOLID -> "coal";
            case LIQUID -> "lava";
            case SOLAR -> "solar";
        };
        int capacity = json.has("steam_capacity") ? json.get("steam_capacity").getAsInt()
                : (capacityFromOutput > 0 ? capacityFromOutput : 32000);
        int perTick = json.has("steam_per_tick") ? json.get("steam_per_tick").getAsInt()
                : (json.has("rate") ? json.get("rate").getAsInt() : 20);
        int highMultiplier = json.has("high_pressure_multiplier")
                ? json.get("high_pressure_multiplier").getAsInt() : 2;
        return new BoilerDefinition(id, type, overlay, itemSlots, fluidSlots, chemicalSlots,
                capacity, perTick, highMultiplier, level, temperatureMax);
    }
}
