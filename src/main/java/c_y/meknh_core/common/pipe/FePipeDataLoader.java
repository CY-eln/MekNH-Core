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
 * 扫描所有模组里的 {@code data/addon/pipe/fe/*.json}，每份 JSON 注册一种 FE 电缆。
 *
 * <p>扫描发生在模组构造阶段（注册表事件之前），这样电缆方块/物品能进入静态注册表。</p>
 */
public final class FePipeDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JSON_SUFFIX = ".json";

    private FePipeDataLoader() {
    }

    public static int loadFromMods() {
        int loaded = 0;
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile modFile = info.getFile();
            Path directory = modFile.getSecureJar().getRootPath()
                    .resolve("data")
                    .resolve(FePipeRegistration.ADDON_DATA_DIRECTORY)
                    .resolve(FePipeRegistration.PIPE_DIRECTORY);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            // UnionFileSystem 不支持 glob（getPathMatcher），改用谓词过滤。
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory,
                    file -> file.getFileName().toString().endsWith(JSON_SUFFIX) && Files.isRegularFile(file))) {
                for (Path file : files) {
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        String name = file.getFileName().toString();
                        ResourceLocation fileId = ResourceLocation.tryBuild(MekNHCore.MODID,
                                name.substring(0, name.length() - JSON_SUFFIX.length()));
                        if (fileId == null) {
                            throw new IllegalArgumentException("Invalid cable filename: " + name);
                        }
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        FePipeDefinition definition = FePipeDefinition.parse(fileId, json);
                        if (FePipeRegistration.register(definition)) {
                            loaded++;
                        }
                    } catch (IOException | RuntimeException e) {
                        LOGGER.error("Skipping invalid FE cable definition {}: {}", file, e.toString());
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to scan FE cable directory in {}: {}", modFile.getFileName(), e.toString());
            }
        }
        LOGGER.info("Loaded {} FE cable definition(s)", loaded);
        return loaded;
    }
}
