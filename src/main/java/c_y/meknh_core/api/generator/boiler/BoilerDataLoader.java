package c_y.meknh_core.api.generator.boiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;

/**
 * 扫描 {@code data/addon/generator/boiler/*.json}。
 */
public final class BoilerDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "generator/boiler";

    private BoilerDataLoader() {
    }

    public static void loadFromMods() {
        int loaded = 0;
        for (IModFileInfo modFileInfo : ModList.get().getModFiles()) {
            Path directory = modFileInfo.getFile().getSecureJar().getRootPath()
                    .resolve("data").resolve("addon").resolve(DIRECTORY);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".json")) {
                        continue;
                    }
                    ResourceLocation id = ResourceLocation.tryBuild("meknh_core",
                            fileName.substring(0, fileName.length() - 5));
                    if (id != null) {
                        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            JsonElement element = JsonParser.parseReader(reader);
                            BoilerRegistration.register(BoilerDefinition.parse(id, element.getAsJsonObject()));
                            loaded++;
                        } catch (IOException | RuntimeException exception) {
                            LOGGER.error("Failed to load boiler definition {}", id, exception);
                        }
                    }
                }
            } catch (IOException exception) {
                LOGGER.error("Failed to scan boiler definitions", exception);
            }
        }
        LOGGER.info("Registered {} data-driven boiler definition(s)", loaded);
    }
}
