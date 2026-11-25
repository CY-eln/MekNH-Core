package c_y.meknh_core.common.fuel;

import c_y.meknh_core.api.chemical.FluidAttributeRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

/**
 * 扫描 {@code data/addon/fuel/*.json}：
 * <pre>{@code
 * { "id": { "fluid": "meknh_core:creosote" }, "time": 6400 }
 * { "id": { "item": "meknh_core:coke_gem" }, "time": 1600 }
 * }</pre>
 * 流体燃料会自动尝试给对应的 {@code <path>_bucket} 物品加同样燃烧时间，没有桶则跳过。
 */
public final class FuelDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JSON_SUFFIX = ".json";

    private FuelDataLoader() {
    }

    public static void initialize(IEventBus bus) {
        bus.addListener(FuelDataLoader::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FuelDataLoader::loadFromMods);
    }

    private static void loadFromMods() {
        int loaded = 0;
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile modFile = info.getFile();
            Path directory = modFile.getSecureJar().getRootPath()
                    .resolve("data").resolve("addon").resolve("fuel");
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory,
                    file -> file.getFileName().toString().endsWith(JSON_SUFFIX) && Files.isRegularFile(file))) {
                for (Path file : files) {
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (parse(json)) {
                            loaded++;
                        }
                    } catch (IOException | RuntimeException e) {
                        LOGGER.error("Skipping invalid fuel definition {}: {}", file, e.toString());
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to scan fuel directory in {}: {}", modFile.getFileName(), e.toString());
            }
        }
        LOGGER.info("Loaded {} fuel definition(s)", loaded);
    }

    private static boolean parse(JsonObject json) {
        JsonElement idElement = json.get("id");
        if (idElement == null || !idElement.isJsonObject()) {
            return false;
        }
        JsonObject id = idElement.getAsJsonObject();
        int time = json.has("time") ? json.get("time").getAsInt() : 0;
        if (time <= 0) {
            return false;
        }
        if (id.has("fluid")) {
            ResourceLocation fluidId = ResourceLocation.tryParse(id.get("fluid").getAsString());
            if (fluidId == null) {
                return false;
            }
            FluidAttributeRegistry.registerFuel(fluidId, time);
            ResourceLocation bucketId = ResourceLocation.fromNamespaceAndPath(fluidId.getNamespace(),
                    fluidId.getPath() + "_bucket");
            Item bucket = BuiltInRegistries.ITEM.get(bucketId);
            if (bucket != Items.AIR) {
                FuelRegistry.registerItem(bucketId, time);
                LOGGER.info("Fuel: fluid {} = {} ticks, bucket {} registered", fluidId, time, bucketId);
            } else {
                LOGGER.info("Fuel: fluid {} = {} ticks, no bucket item, skipped", fluidId, time);
            }
            return true;
        }
        if (id.has("item")) {
            ResourceLocation itemId = ResourceLocation.tryParse(id.get("item").getAsString());
            if (itemId == null) {
                return false;
            }
            FuelRegistry.registerItem(itemId, time);
            return true;
        }
        return false;
    }
}
