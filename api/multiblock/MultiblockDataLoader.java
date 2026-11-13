package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.MekNHCore;
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
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

/**
 * 扫描所有模组里的 {@code data/addon/multiblock/*.json}，每个文件描述一个多方块。
 *
 * <p>扫描发生在模组构造阶段（{@code RegisterEvent} 之前），因此控制器方块/物品
 * 能进入静态注册表，客户端与服务端保持一致。控制器注册名统一为本模组命名空间：
 * {@code <电压>_<文件名>}（例如 {@code lv_my_multiblock}）。</p>
 */
public final class MultiblockDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_ROOT = "data";
    private static final String JSON_SUFFIX = ".json";

    private MultiblockDataLoader() {
    }

    /**
     * 扫描并注册所有模组里的多方块定义。
     *
     * @return 成功注册的数量
     */
    public static int loadFromMods() {
        int loaded = 0;
        for (IModFileInfo modFileInfo : ModList.get().getModFiles()) {
            IModFile modFile = modFileInfo.getFile();
            try {
                loaded += loadFromModFile(modFile);
            } catch (Exception e) {
                LOGGER.error("Failed to scan multiblock definitions in mod file {}", modFile.getFileName(), e);
            }
        }
        LOGGER.info("Loaded {} data-driven multiblock definition(s)", loaded);
        return loaded;
    }

    private static int loadFromModFile(IModFile modFile) {
        Path directory = modFile.getSecureJar().getRootPath()
                .resolve(DATA_ROOT)
                .resolve(MultiblockRegistration.ADDON_DATA_DIRECTORY)
                .resolve(MultiblockRegistration.MULTIBLOCK_DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int loaded = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!file.getFileName().toString().endsWith(JSON_SUFFIX)) {
                    continue;
                }
                if (loadMultiblock(file)) {
                    loaded++;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan multiblock definitions in mod file {}", modFile.getFileName(), e);
        }
        return loaded;
    }

    private static boolean loadMultiblock(Path file) {
        String fileName = file.getFileName().toString();
        String path = fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
        ResourceLocation id = ResourceLocation.tryBuild(MekNHCore.MODID, path);
        if (id == null) {
            LOGGER.error("Skipping multiblock definition with invalid id: {} ({})", path, file);
            return false;
        }
        if (MultiblockRegistration.definition(id) != null) {
            LOGGER.warn("Skipping duplicate multiblock definition {} ({})", id, file);
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                LOGGER.error("Multiblock definition {} must be a JSON object ({})", id, file);
                return false;
            }
            JsonObject json = element.getAsJsonObject();
            MultiblockDefinition definition = MultiblockDefinition.parse(id, json);
            MultiblockRegistration.register(definition);
            LOGGER.debug("Loaded data-driven multiblock {} with {} controller variant(s), structure {}",
                    definition.id(), definition.levels().size(), definition.structure().describeSize());
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load multiblock definition {} from {}", id, file, e);
            return false;
        }
    }
}
