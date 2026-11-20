package c_y.meknh_core.common.pipe;

import c_y.meknh_core.MekNHCore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

/**
 * 扫描 {@code data/addon/pipe/mek/*.json}，注册本模组“mek 槽位”管道。
 */
public final class MekPipeDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JSON_SUFFIX = ".json";

    private MekPipeDataLoader() {
    }

    public static int loadFromMods() {
        int loaded = 0;
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile modFile = info.getFile();
            Path directory = modFile.getSecureJar().getRootPath()
                    .resolve("data")
                    .resolve(MekPipeRegistration.ADDON_DATA_DIRECTORY)
                    .resolve(MekPipeRegistration.PIPE_DIRECTORY);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory,
                    file -> file.getFileName().toString().endsWith(JSON_SUFFIX) && Files.isRegularFile(file))) {
                for (Path file : files) {
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        String name = file.getFileName().toString();
                        ResourceLocation fileId = ResourceLocation.tryBuild(MekNHCore.MODID,
                                name.substring(0, name.length() - JSON_SUFFIX.length()));
                        if (fileId == null) {
                            throw new IllegalArgumentException("Invalid pipe filename: " + name);
                        }
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (MekPipeRegistration.register(MekPipeDefinition.parse(fileId, json))) {
                            loaded++;
                        }
                    } catch (IOException | RuntimeException e) {
                        LOGGER.error("Skipping invalid mek pipe definition {}: {}", file, e.toString());
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to scan mek pipe directory in {}: {}", modFile.getFileName(), e.toString());
            }
        }
        LOGGER.info("Loaded {} mek pipe definition(s)", loaded);
        return loaded;
    }
}
