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
        java.util.List<MultiblockDefinition> definitions = new java.util.ArrayList<>();
        java.util.List<ControllerBindingPlan.Request> requests = new java.util.ArrayList<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile modFile = info.getFile();
            Path directory = modFile.getSecureJar().getRootPath().resolve(DATA_ROOT)
                    .resolve(MultiblockRegistration.ADDON_DATA_DIRECTORY)
                    .resolve(MultiblockRegistration.MULTIBLOCK_DIRECTORY);
            if (!Files.isDirectory(directory)) continue;
            try (DirectoryStream<Path> files = MultiblockDefinitionFiles.open(directory)) {
                for (Path file : files) {
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        String name = file.getFileName().toString();
                        ResourceLocation fileId = ResourceLocation.tryBuild(MekNHCore.MODID,
                                name.substring(0, name.length() - JSON_SUFFIX.length()));
                        if (fileId == null) throw new IllegalArgumentException("Invalid definition filename ResourceLocation: " + name);
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        MultiblockDefinition definition = MultiblockDefinition.parse(fileId, json);
                        String controller = null;
                        if (json.has("controller_block")) {
                            JsonElement value = json.get("controller_block");
                            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                                throw new IllegalArgumentException("controller_block must be a namespace:block string");
                            }
                            controller = value.getAsString();
                            if (!controller.contains(":") || ResourceLocation.tryParse(controller) == null) {
                                throw new IllegalArgumentException("Invalid controller_block ResourceLocation: " + controller);
                            }
                        }
                        definitions.add(definition);
                        requests.add(new ControllerBindingPlan.Request(definition.id().toString(), definition.noVoltage(),
                                controller, MultiblockRegistration.generatedControllerIds(definition)));
                    } catch (IOException | RuntimeException e) {
                        errors.add(file + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                errors.add(modFile.getFileName() + ": " + e.getMessage());
            }
        }
        // Fail closed: no registry writes (including legacy fallback) after any invalid explicit declaration.
        java.util.Map<String, String> plan = null;
        try {
            plan = ControllerBindingPlan.plan(requests, MultiblockRegistration.controllerCandidates(),
                    MultiblockRegistration.reservedControllerBlockIds());
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }
        if (!errors.isEmpty()) {
            java.util.Collections.sort(errors);
            throw new IllegalArgumentException("Multiblock preflight failed before registration: " + String.join("; ", errors));
        }
        MultiblockRegistration.applyControllerPlan(plan);
        definitions.sort(java.util.Comparator.comparing(d -> d.id().toString()));
        for (MultiblockDefinition definition : definitions) MultiblockRegistration.register(definition);
        LOGGER.info("Loaded {} data-driven multiblock definition(s)", definitions.size());
        return definitions.size();
    }
}
