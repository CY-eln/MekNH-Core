package c_y.meknh_core.api.chemical;

import c_y.meknh_core.MekNHCore;
import com.google.gson.JsonElement;
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
 * 扫描所有模组中的 {@code data/addon/chemicals/*.json}，
 * 每个文件同时描述一个化学品的注册信息与属性，并交给 {@link ChemicalRegistration} 注册。
 *
 * <p>扫描发生在模组构造阶段，也就是 {@code RegisterEvent} 之前，因此注册出来的化学品
 * 能像手写 Java 注册一样进入 Mekanism 的静态化学品注册表，客户端与服务端保持一致。
 * 化学品注册名统一为本模组命名空间：{@code meknh_core:<文件名>}。</p>
 */
public final class ChemicalDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_ROOT = "data";
    private static final String JSON_SUFFIX = ".json";

    private ChemicalDataLoader() {
    }

    /**
     * 扫描并注册所有模组中的化学品定义。
     *
     * @return 成功注册的化学品数量
     */
    public static int loadFromMods() {
        int loaded = 0;
        for (IModFileInfo modFileInfo : ModList.get().getModFiles()) {
            IModFile modFile = modFileInfo.getFile();
            try {
                loaded += loadFromModFile(modFile);
            } catch (Exception e) {
                LOGGER.error("Failed to scan chemical definitions in mod file {}", modFile.getFileName(), e);
            }
        }
        LOGGER.info("Loaded {} data-driven Mekanism chemical(s)", loaded);
        return loaded;
    }

    private static int loadFromModFile(IModFile modFile) {
        // UnionPath（开发环境的合并文件系统）不支持带 glob 的 newDirectoryStream，这里手动过滤文件名
        Path directory = modFile.getSecureJar().getRootPath()
                .resolve(DATA_ROOT)
                .resolve(ChemicalRegistration.ADDON_DATA_DIRECTORY)
                .resolve(ChemicalRegistration.CHEMICAL_DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int loaded = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!file.getFileName().toString().endsWith(JSON_SUFFIX)) {
                    continue;
                }
                if (loadChemical(file)) {
                    loaded++;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan chemical definitions in mod file {}", modFile.getFileName(), e);
        }
        return loaded;
    }

    private static boolean loadChemical(Path file) {
        String fileName = file.getFileName().toString();
        String path = fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
        ResourceLocation id = ResourceLocation.tryBuild(MekNHCore.MODID, path);
        if (id == null) {
            LOGGER.error("Skipping chemical definition with invalid id: {} ({})", path, file);
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                LOGGER.error("Chemical definition {} must be a JSON object ({})", id, file);
                return false;
            }
            ChemicalDefinition definition = ChemicalDefinition.parse(id, element.getAsJsonObject());
            if (definition.fluid()) {
                FluidRegistration.register(definition);
                LOGGER.debug("Loaded data-driven fluid {} from {}", definition.id(), file);
            } else {
                if (ChemicalRegistration.isRegistered(definition.id())) {
                    LOGGER.warn("Skipping duplicate chemical definition {} ({})", definition.id(), file);
                    return false;
                }
                ChemicalRegistration.register(definition);
                LOGGER.debug("Loaded data-driven chemical {} from {}", definition.id(), file);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load chemical definition {} from {}", id, file, e);
            return false;
        }
    }
}
