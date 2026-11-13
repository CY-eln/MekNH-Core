package c_y.meknh_core.client.editor;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.gui.layout.MachineGuiLayout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * 机器界面布局的本地存档（客户端）。
 *
 * <p>文件：{@code config/meknh_core/gui_layouts.json}，结构是
 * {@code {"meknh_core:<机器ID>": [元素, ...]}}。编辑器保存时写这里，
 * 以后正式界面可以从同一个文件读取布局。</p>
 *
 * <p>布局按「机器定义」存一份（不区分电压/蒸汽版本），因为槽位数量只和定义有关；
 * 能量与热量元素由界面在渲染时再决定具体显示什么。</p>
 */
public final class MachineUiLayouts {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, List<UiElement>>>() {
    }.getType();
    private static final Type LIST_TYPE = new TypeToken<List<UiElement>>() {
    }.getType();

    private static final Map<String, List<UiElement>> LAYOUTS = new LinkedHashMap<>();
    private static boolean loaded;

    private MachineUiLayouts() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("meknh_core").resolve("gui_layouts.json");
    }

    /**
     * 已保存的布局；没有返回 {@code null}。
     */
    public static List<UiElement> saved(ResourceLocation machineId) {
        load();
        List<UiElement> elements = LAYOUTS.get(machineId.toString());
        return elements == null ? null : copy(elements);
    }

    /**
     * 直接拿内部列表，供每帧渲染读取（不要修改）。
     */
    @Nullable
    public static List<UiElement> current(ResourceLocation machineId) {
        load();
        return LAYOUTS.get(machineId.toString());
    }

    /**
     * 已保存布局的机器 ID 列表（config + data/gui 文件），供「从其他机器复制」用。
     */
    public static List<ResourceLocation> availableLayouts() {
        load();
        List<ResourceLocation> ids = new ArrayList<>();
        for (String key : LAYOUTS.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                ids.add(id);
            }
        }
        ids.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return ids;
    }

    /**
     * 打开编辑器用：有存档用存档，没有（或存档是空列表）就按机器定义生成默认元素。
     */
    public static List<UiElement> layout(MachineDefinition definition, @Nullable MachineLevel level) {
        List<UiElement> saved = saved(definition.id());
        return saved != null && !saved.isEmpty() ? saved : defaults(definition, level);
    }

    public static void save(ResourceLocation machineId, List<UiElement> elements) {
        load();
        LAYOUTS.put(machineId.toString(), copy(elements));
        flush();
    }

    /**
     * 「保存」按钮：除了更新运行时的 config 缓存，还把布局导出成资源文件。
     *
     * <p>导出目录优先用开发环境的 {@code src/main/resources/data/gui/}，
     * 找不到（正式游戏环境）就退回 {@code <游戏目录>/data/gui/}。</p>
     *
     * @return 实际写出的文件
     */
    public static Path export(ResourceLocation machineId, List<UiElement> elements) {
        Path directory = exportDirectory();
        Path target = directory.resolve(exportName(machineId) + ".json");
        try {
            Files.createDirectories(directory);
            Files.writeString(target, GSON.toJson(copy(elements), LIST_TYPE), StandardCharsets.UTF_8);
            MekNHCore.LOGGER.info("Exported GUI layout for {} to {}", machineId, target);
        } catch (IOException exception) {
            MekNHCore.LOGGER.warn("Failed to export GUI layout for {} to {}", machineId, target, exception);
        }
        return target;
    }

    private static Path exportDirectory() {
        Path gameDirectory = FMLPaths.GAMEDIR.get().toAbsolutePath();
        Path project = gameDirectory.getParent();
        if (project != null && Files.isDirectory(project.resolve("src").resolve("main").resolve("resources"))) {
            return project.resolve("src").resolve("main").resolve("resources").resolve("data").resolve("gui");
        }
        return gameDirectory.resolve("data").resolve("gui");
    }

    /**
     * 导出的文件名：本模组的机器直接用 {@code <路径>.json}，别的命名空间（例如 Mekanism 的
     * {@code mekanism:electric_furnace}）用 {@code <命名空间>__<路径>.json}，避免重名。
     */
    public static String exportName(ResourceLocation machineId) {
        return machineId.getNamespace().equals(MekNHCore.MODID)
                ? machineId.getPath()
                : machineId.getNamespace() + "__" + machineId.getPath();
    }

    /**
     * 文件名反解成机器 ID（与 {@link #exportName(ResourceLocation)} 对应）。
     */
    private static ResourceLocation machineIdFromFile(String fileName) {
        String name = fileName.substring(0, fileName.length() - ".json".length());
        int separator = name.indexOf("__");
        if (separator > 0) {
            return ResourceLocation.tryParse(name.substring(0, separator) + ":" + name.substring(separator + 2));
        }
        return ResourceLocation.fromNamespaceAndPath(MekNHCore.MODID, name);
    }

    /**
     * 默认元素，完全照 Mekanism 电力机器的界面排：
     * 输入槽 (64,17)、输出槽 (116,35)、能量槽 (64,53)、向上箭头 (68,38)、
     * 进度条 (86,38)、竖向能量条 (164,16)。
     *
     * <p>蒸汽机器是另一套界面：没有能量槽和能量条，右侧换成蒸汽仪表。</p>
     */
    public static List<UiElement> defaults(MachineDefinition definition, @Nullable MachineLevel level) {
        List<UiElement> elements = new ArrayList<>();

        int inputSlots = definition.itemInputSlots();
        int outputSlots = definition.itemOutputSlots();
        int gaugeCount = MachineGuiLayout.gauges(definition, level).size();

        int inputTop = MachineGuiLayout.columnTop(inputSlots);
        for (int index = 0; index < inputSlots; index++) {
            elements.add(new UiElement(UiElementKind.ITEM_INPUT,
                    MachineGuiLayout.INPUT_COLUMN_X, inputTop + index * MachineGuiLayout.SLOT_STEP, 18, 18));
        }

        int outputX = MachineGuiLayout.outputColumnX(gaugeCount);
        int outputTop = MachineGuiLayout.outputTop(outputSlots);
        for (int index = 0; index < outputSlots; index++) {
            elements.add(new UiElement(UiElementKind.ITEM_OUTPUT,
                    outputX, outputTop + index * MachineGuiLayout.SLOT_STEP, 18, 18));
        }

        // 进度条 + 左边的向上箭头（Mekanism 的 25x9 / 8x10）
        elements.add(new UiElement(UiElementKind.PROGRESS,
                MachineGuiLayout.progressX(gaugeCount), MachineGuiLayout.PROGRESS_Y, 25, 9));
        elements.add(new UiElement(UiElementKind.ARROW,
                MachineGuiLayout.progressX(gaugeCount) - 18, MachineGuiLayout.PROGRESS_Y, 8, 10));

        List<MachineGuiLayout.Gauge> gauges = MachineGuiLayout.gauges(definition, level);
        for (int index = 0; index < gauges.size(); index++) {
            UiElementKind kind = switch (gauges.get(index)) {
                case FLUID_IN -> UiElementKind.FLUID_IN;
                case FLUID_OUT -> UiElementKind.FLUID_OUT;
                case GAS_IN -> UiElementKind.GAS_IN;
                case GAS_OUT -> UiElementKind.GAS_OUT;
                case STEAM -> UiElementKind.STEAM;
                case ENERGY -> UiElementKind.ENERGY;
            };
            elements.add(new UiElement(kind, MachineGuiLayout.gaugeX(index, gauges.size()),
                    MachineGuiLayout.GAUGE_TOP, kind == UiElementKind.ENERGY ? 6 : 16, MachineGuiLayout.GAUGE_HEIGHT));
        }
        return elements;
    }

    private static List<UiElement> copy(List<UiElement> elements) {
        List<UiElement> copy = new ArrayList<>(elements.size());
        for (UiElement element : elements) {
            copy.add(element.copy());
        }
        return copy;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        // 先读 config 里的旧缓存（兼容以前编辑过的布局）
        Path path = file();
        if (Files.isRegularFile(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                Map<String, List<UiElement>> parsed = GSON.fromJson(json, TYPE);
                if (parsed != null) {
                    LAYOUTS.putAll(parsed);
                }
            } catch (IOException | RuntimeException exception) {
                MekNHCore.LOGGER.warn("Failed to read GUI layouts from {}", path, exception);
            }
        }

        // 再读导出的资源文件：data/gui/<机器ID>.json，同名时以资源文件为准
        Path directory = exportDirectory();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(entry -> entry.getFileName().toString().endsWith(".json"))
                    .forEach(entry -> {
                        String fileName = entry.getFileName().toString();
                        ResourceLocation machineId = machineIdFromFile(fileName);
                        try {
                            List<UiElement> parsed = GSON.fromJson(
                                    Files.readString(entry, StandardCharsets.UTF_8), LIST_TYPE);
                            if (parsed != null) {
                                LAYOUTS.put(machineId.toString(), parsed);
                            }
                        } catch (IOException | RuntimeException exception) {
                            MekNHCore.LOGGER.warn("Failed to read GUI layout from {}", entry, exception);
                        }
                    });
        } catch (IOException exception) {
            MekNHCore.LOGGER.warn("Failed to list GUI layouts in {}", directory, exception);
        }
    }

    private static void flush() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(LAYOUTS, TYPE), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            MekNHCore.LOGGER.warn("Failed to write GUI layouts to {}", path, exception);
        }
    }
}
