package c_y.meknh_core.api.multiblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 一份多方块结构：由若干相对控制器偏移的 {@link Cell} 组成。
 *
 * <p>结构来自 JSON 的 {@code 结构} 与 {@code 组成} 两个字段，
 * 解析规则见 {@code MULTIBLOCK_JSON.md}。本类只负责几何与匹配，不持有世界状态，
 * 因此可以被多个控制器共享。</p>
 *
 * <p>坐标系：{@code x} 是行内字符方向，{@code y} 是高度（向上），{@code z} 是深度
 * （数组外层，最后一个 {@code []} 是正面，第一个是背面）。JSON 的读取顺序是
 * {@code 结构[z][y][x]}，所有偏移都相对控制器方块，并按控制器朝向整体旋转。</p>
 */
public final class MultiblockStructure {

    /**
     * 一个结构位置。
     *
     * @param dx      相对控制器的 x 偏移
     * @param dy      相对控制器的 y 偏移
     * @param dz      相对控制器的 z 偏移
     * @param symbol  该位置在 {@code 结构} 里使用的字符
     * @param matcher 该位置允许的方块
     */
    public record Cell(int dx, int dy, int dz, char symbol, MultiblockMatcher matcher) {

        /**
         * 兼容旧调用：没有符号时使用空格（不参与按符号配置的仓室白名单）。
         */
        public Cell(int dx, int dy, int dz, MultiblockMatcher matcher) {
            this(dx, dy, dz, ' ', matcher);
        }
    }

    /**
     * 一次匹配的结果。
     *
     * @param formed    是否成型
     * @param parts     匹配到的仓室（未成型时为空）
     * @param failedPos 第一个不满足的位置
     * @param expected  该位置要求的方块描述
     * @param actual    该位置实际的方块 ID
     */
    public record MatchResult(boolean formed, List<MultiblockPart> parts,
                              @Nullable BlockPos failedPos, @Nullable String expected,
                              @Nullable String actual) {

        public static MatchResult success(List<MultiblockPart> parts) {
            return new MatchResult(true, List.copyOf(parts), null, null, null);
        }

        public static MatchResult failure(BlockPos pos, String expected, String actual) {
            return new MatchResult(false, List.of(), pos, expected, actual);
        }

        /**
         * 人类可读的失败原因，用于右键诊断。
         */
        public String message() {
            if (this.formed) {
                return "结构完整";
            }
            if (this.failedPos == null) {
                return "结构不完整";
            }
            return "位置 " + this.failedPos.toShortString() + " 需要 " + this.expected
                    + "，实际是 " + this.actual;
        }
    }

    private final List<Cell> cells;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Set<MultiblockPartType> globalHatchTypes;
    private final Set<ResourceLocation> globalHatchBlocks;
    private final Map<Character, Set<MultiblockPartType>> symbolHatchTypes;
    private final Map<Character, Set<ResourceLocation>> symbolHatchBlocks;

    private MultiblockStructure(List<Cell> cells, int sizeX, int sizeY, int sizeZ,
                                Set<MultiblockPartType> globalHatchTypes,
                                Set<ResourceLocation> globalHatchBlocks,
                                Map<Character, Set<MultiblockPartType>> symbolHatchTypes,
                                Map<Character, Set<ResourceLocation>> symbolHatchBlocks) {
        this.cells = List.copyOf(cells);
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.globalHatchTypes = Set.copyOf(globalHatchTypes);
        this.globalHatchBlocks = Set.copyOf(globalHatchBlocks);
        this.symbolHatchTypes = copySymbolMap(symbolHatchTypes);
        this.symbolHatchBlocks = copySymbolMap(symbolHatchBlocks);
    }

    private static <T> Map<Character, Set<T>> copySymbolMap(Map<Character, Set<T>> source) {
        Map<Character, Set<T>> copy = new HashMap<>();
        source.forEach((symbol, values) -> copy.put(symbol, Set.copyOf(values)));
        return Map.copyOf(copy);
    }

    /**
     * 全部结构位置。
     */
    public List<Cell> cells() {
        return this.cells;
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    /**
     * 结构包含的方块数量（含控制器与空气位）。
     */
    public int volume() {
        return this.cells.size();
    }

    /**
     * 以代码方式构造结构（相对控制器的偏移）。
     *
     * <p>控制器必须位于 {@code (0, 0, 0)}，与 JSON 解析结果保持一致。
     * 该入口供其它模组/附属在不写 JSON 的情况下注册结构。</p>
     *
     * @param cells 相对控制器的全部位置
     */
    public static MultiblockStructure of(List<Cell> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Multiblock structure must not be empty");
        }
        boolean controllerAtOrigin = false;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : cells) {
            minX = Math.min(minX, cell.dx());
            maxX = Math.max(maxX, cell.dx());
            maxY = Math.max(maxY, cell.dy());
            maxZ = Math.max(maxZ, cell.dz());
            if (cell.dx() == 0 && cell.dy() == 0 && cell.dz() == 0
                    && cell.matcher() instanceof MultiblockMatcher.Controller) {
                controllerAtOrigin = true;
            }
        }
        if (!controllerAtOrigin) {
            throw new IllegalArgumentException("Multiblock structure must contain a controller at (0, 0, 0)");
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY + 1;
        int sizeZ = maxZ + 1;
        return new MultiblockStructure(cells, sizeX, sizeY, sizeZ,
                Set.of(), Set.of(), Map.of(), Map.of());
    }

    /**
     * {@code 支持的仓室} 的解析结果。
     *
     * <p>{@code 支持的仓室} 可以是数组（全局白名单）或对象（按符号的白名单）。
     * 条目可以写仓室类型 ID（{@code item_input}），也可以写具体仓室方块 ID
     * （{@code meknh_core:lv_item_input_hatch}）。</p>
     */
    private record HatchFilter(Set<MultiblockPartType> globalTypes, Set<ResourceLocation> globalBlocks,
                               Map<Character, Set<MultiblockPartType>> symbolTypes,
                               Map<Character, Set<ResourceLocation>> symbolBlocks) {

        static HatchFilter empty() {
            return new HatchFilter(Set.of(), Set.of(), Map.of(), Map.of());
        }
    }

    /**
     * 解析结构 JSON。
     *
     * @param id   结构 ID，仅用于报错
     * @param json 多方块 JSON
     */
    public static MultiblockStructure parse(ResourceLocation id, JsonObject json) {
        JsonElement structureElement = json.get("结构");
        if (structureElement == null || structureElement.isJsonNull()) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' is required");
        }
        List<List<String>> slices = readSlices(id, structureElement);
        if (slices.isEmpty() || slices.getFirst().isEmpty()) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' must not be empty");
        }

        // 外层 [] = 深度 z（第一个是正面，往后是背面）；内层字符串 = 高度 y（第一个是底层）。
        int sizeZ = slices.size();
        int sizeY = slices.getFirst().size();
        int sizeX = 0;
        for (int slice = 0; slice < sizeZ; slice++) {
            List<String> levels = slices.get(slice);
            if (levels.size() != sizeY) {
                throw new IllegalArgumentException("Multiblock " + id + ": slice " + slice + " has "
                        + levels.size() + " height level(s), expected " + sizeY);
            }
            for (String level : levels) {
                sizeX = Math.max(sizeX, level.length());
            }
        }
        if (sizeX == 0) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' rows must not be empty");
        }
        // 较短的行在右侧补空格（空格 = 任意方块），方便写逐层收窄的结构。
        for (List<String> levels : slices) {
            for (int level = 0; level < levels.size(); level++) {
                String value = levels.get(level);
                if (value.length() < sizeX) {
                    levels.set(level, value + " ".repeat(sizeX - value.length()));
                }
            }
        }

        JsonObject mapping = json.has("组成") && json.get("组成").isJsonObject()
                ? json.getAsJsonObject("组成")
                : new JsonObject();
        HatchFilter hatchFilter = parseHatchFilter(id, json.get("支持的仓室"));
        Map<Character, Set<MultiblockPartType>> symbolHatchTypes =
                copyMutable(hatchFilter.symbolTypes());
        Map<Character, Set<ResourceLocation>> symbolHatchBlocks =
                copyMutable(hatchFilter.symbolBlocks());

        List<Cell> cells = new ArrayList<>();
        int controllerX = Integer.MIN_VALUE;
        int controllerY = 0;
        int controllerZ = 0;
        int controllers = 0;

        for (int z = 0; z < sizeZ; z++) {
            List<String> levels = slices.get(z);
            for (int y = 0; y < sizeY; y++) {
                String row = levels.get(y);
                for (int x = 0; x < sizeX; x++) {
                    char symbol = row.charAt(x);
                    MultiblockMatcher matcher = resolve(id, mapping, symbol, symbolHatchTypes, symbolHatchBlocks);
                    if (matcher instanceof MultiblockMatcher.Controller) {
                        controllers++;
                        controllerX = x;
                        controllerY = y;
                        controllerZ = z;
                    }
                    cells.add(new Cell(x, y, z, symbol, matcher));
                }
            }
        }

        if (controllers == 0) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' must contain exactly one controller symbol '@'");
        }
        if (controllers > 1) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' contains " + controllers
                    + " controller symbols, expected exactly one");
        }

        List<Cell> relative = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            // z 越大的 [] 越靠前：最后一个 [] 是正面，第一个是背面。
            relative.add(new Cell(cell.dx() - controllerX, cell.dy() - controllerY,
                    controllerZ - cell.dz(), cell.symbol(), cell.matcher()));
        }
        return new MultiblockStructure(relative, sizeX, sizeY, sizeZ,
                hatchFilter.globalTypes(), hatchFilter.globalBlocks(),
                symbolHatchTypes, symbolHatchBlocks);
    }

    private static <T> Map<Character, Set<T>> copyMutable(Map<Character, Set<T>> source) {
        Map<Character, Set<T>> copy = new HashMap<>();
        source.forEach((symbol, values) -> copy.put(symbol, new LinkedHashSet<>(values)));
        return copy;
    }

    /**
     * 解析 {@code 支持的仓室}。
     *
     * <p>数组 = 全局白名单；对象 = 按符号的白名单（键必须是单个字符）。
     * 条目可以是仓室类型 ID（{@code item_input}）或具体仓室方块 ID
     * （{@code meknh_core:lv_item_input_hatch}）。</p>
     */
    private static HatchFilter parseHatchFilter(ResourceLocation id, @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return HatchFilter.empty();
        }
        Set<MultiblockPartType> globalTypes = new LinkedHashSet<>();
        Set<ResourceLocation> globalBlocks = new LinkedHashSet<>();
        Map<Character, Set<MultiblockPartType>> symbolTypes = new HashMap<>();
        Map<Character, Set<ResourceLocation>> symbolBlocks = new HashMap<>();

        if (element.isJsonArray()) {
            parseHatchEntries(id, element.getAsJsonArray(), globalTypes, globalBlocks);
        } else if (element.isJsonPrimitive()) {
            parseHatchEntry(id, element.getAsString(), globalTypes, globalBlocks);
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if (key.length() != 1) {
                    throw new IllegalArgumentException("Multiblock " + id
                            + ": '支持的仓室' key '" + key + "' must be a single structure symbol");
                }
                char symbol = key.charAt(0);
                Set<MultiblockPartType> types = new LinkedHashSet<>();
                Set<ResourceLocation> blocks = new LinkedHashSet<>();
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    parseHatchEntries(id, value.getAsJsonArray(), types, blocks);
                } else if (value.isJsonPrimitive()) {
                    parseHatchEntry(id, value.getAsString(), types, blocks);
                } else {
                    throw new IllegalArgumentException("Multiblock " + id
                            + ": '支持的仓室." + key + "' must be a string or an array");
                }
                if (!types.isEmpty()) {
                    symbolTypes.put(symbol, types);
                }
                if (!blocks.isEmpty()) {
                    symbolBlocks.put(symbol, blocks);
                }
            }
        } else {
            throw new IllegalArgumentException("Multiblock " + id
                    + ": '支持的仓室' must be an array, an object or a string");
        }
        return new HatchFilter(globalTypes, globalBlocks, symbolTypes, symbolBlocks);
    }

    private static void parseHatchEntries(ResourceLocation id, JsonArray array,
                                          Set<MultiblockPartType> types, Set<ResourceLocation> blocks) {
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive()) {
                throw new IllegalArgumentException("Multiblock " + id
                        + ": every '支持的仓室' entry must be a string");
            }
            parseHatchEntry(id, entry.getAsString(), types, blocks);
        }
    }

    private static void parseHatchEntry(ResourceLocation id, String raw,
                                        Set<MultiblockPartType> types, Set<ResourceLocation> blocks) {
        String value = raw.trim();
        if (value.startsWith("part:")) {
            value = value.substring("part:".length()).trim();
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Multiblock " + id + ": empty '支持的仓室' entry");
        }
        if (value.contains(":")) {
            ResourceLocation blockId = ResourceLocation.tryParse(MultiblockMatcher.qualify(id.getNamespace(), value));
            if (blockId == null) {
                throw new IllegalArgumentException("Multiblock " + id
                        + ": invalid hatch block id '" + raw + "'");
            }
            blocks.add(blockId);
            return;
        }
        MultiblockPartType type = MultiblockPartType.byId(value);
        if (type == null) {
            throw new IllegalArgumentException("Multiblock " + id
                    + ": unknown hatch type '" + raw + "' (expected a part type like item_input, "
                    + "or a block id like meknh_core:lv_item_input_hatch)");
        }
        types.add(type);
    }

    /**
     * 读取结构为“深度切片”列表。
     *
     * <p>外层 {@code []} 是深度 z：第一个是正面；内层字符串是高度 y：第一个是底层。
     * 因此 JSON 的读取顺序是 {@code 结构[z][y][x]}。</p>
     */
    private static List<List<String>> readSlices(ResourceLocation id, JsonElement element) {
        List<List<String>> slices = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            slices.add(splitHeightLines(element.getAsString()));
            return slices;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' must be a string or an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0) {
            return slices;
        }
        if (array.get(0).isJsonArray()) {
            // 数组套数组：外层每一项是一个深度切片，内层字符串是这一切片的高度层。
            for (JsonElement sliceElement : array) {
                if (!sliceElement.isJsonArray()) {
                    throw new IllegalArgumentException("Multiblock " + id
                            + ": every depth slice must be an array of height levels");
                }
                List<String> levels = new ArrayList<>();
                for (JsonElement levelElement : sliceElement.getAsJsonArray()) {
                    addHeightLines(levels, levelElement.getAsString());
                }
                slices.add(levels);
            }
        } else {
            // 字符串数组：整个数组是一个深度切片，每个字符串是一层高度。
            List<String> levels = new ArrayList<>();
            for (JsonElement levelElement : array) {
                addHeightLines(levels, levelElement.getAsString());
            }
            slices.add(levels);
        }
        return slices;
    }

    private static List<String> splitHeightLines(String text) {
        List<String> levels = new ArrayList<>();
        addHeightLines(levels, text);
        return levels;
    }

    /**
     * 把一段文本按换行拆成高度层；末尾换行不算一层。
     */
    private static void addHeightLines(List<String> levels, String text) {
        String[] lines = text.split("\n", -1);
        int last = lines.length - 1;
        for (int index = 0; index <= last; index++) {
            if (index == last && lines[index].isEmpty() && last > 0) {
                break;
            }
            levels.add(stripCarriageReturn(lines[index]));
        }
    }

    private static String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 解析一个字符对应的匹配规则。
     *
     * <p>{@code @} 固定表示控制器；{@code #} 固定表示空气；{@code .}、{@code _} 与空格表示任意方块。
     * 其余字符必须在 {@code 组成} 里有映射。</p>
     */
    private static MultiblockMatcher resolve(ResourceLocation id, JsonObject mapping, char symbol,
                                             Map<Character, Set<MultiblockPartType>> symbolTypes,
                                             Map<Character, Set<ResourceLocation>> symbolBlocks) {
        switch (symbol) {
            case '@':
                return new MultiblockMatcher.Controller();
            case '#':
                return new MultiblockMatcher.Air();
            case '.', '_', ' ':
                return new MultiblockMatcher.Any();
            default:
                break;
        }
        String key = String.valueOf(symbol);
        if (!mapping.has(key)) {
            throw new IllegalArgumentException("Multiblock " + id + ": symbol '" + key
                    + "' is used in '结构' but has no entry in '组成'");
        }
        JsonElement value = mapping.get(key);
        MultiblockMatcher matcher = parseBaseMatcher(id, key, value);
        collectInlineAllow(id, key, value, symbol, symbolTypes, symbolBlocks);
        return matcher;
    }

    /**
     * 解析一个符号的基础方块。
     *
     * <p>支持三种写法：</p>
     * <pre>{@code
     * "b": "minecraft:iron_block"
     * "b": ["minecraft:iron_block", {"allow": "meknh_core:lv_item_input_hatch"}]
     * "b": {"block": "minecraft:iron_block", "allow": ["item_input"]}
     * }</pre>
     */
    private static MultiblockMatcher parseBaseMatcher(ResourceLocation id, String key, JsonElement value) {
        if (value.isJsonPrimitive()) {
            return MultiblockMatcher.parse(id.getNamespace(), value.getAsString());
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() == 0 || !array.get(0).isJsonPrimitive()) {
                throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key
                        + "' array must start with a block id");
            }
            return MultiblockMatcher.parse(id.getNamespace(), array.get(0).getAsString());
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement block = object.has("block") ? object.get("block")
                    : object.get("方块");
            if (block == null || !block.isJsonPrimitive()) {
                throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key
                        + "' object needs a 'block' string");
            }
            return MultiblockMatcher.parse(id.getNamespace(), block.getAsString());
        }
        throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key
                + "' must be a string, an array or an object");
    }

    /**
     * 收集 {@code 组成} 里的内联 {@code allow} 白名单。
     *
     * <p>数组形式：第一个元素是基础方块，其余元素是允许替换的仓室；
     * 对象形式：{@code "allow"} / {@code "允许"} 字段。</p>
     */
    private static void collectInlineAllow(ResourceLocation id, String key, JsonElement value,
                                           char symbol,
                                           Map<Character, Set<MultiblockPartType>> symbolTypes,
                                           Map<Character, Set<ResourceLocation>> symbolBlocks) {
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int index = 1; index < array.size(); index++) {
                collectAllowEntry(id, key, array.get(index), symbol, symbolTypes, symbolBlocks);
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement allow = object.has("allow") ? object.get("allow")
                    : object.get("允许");
            if (allow != null) {
                collectAllowEntry(id, key, allow, symbol, symbolTypes, symbolBlocks);
            }
        }
    }

    private static void collectAllowEntry(ResourceLocation id, String key, JsonElement element,
                                          char symbol,
                                          Map<Character, Set<MultiblockPartType>> symbolTypes,
                                          Map<Character, Set<ResourceLocation>> symbolBlocks) {
        if (element.isJsonPrimitive()) {
            addSymbolHatch(id, key, symbol, element.getAsString(), symbolTypes, symbolBlocks);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                collectAllowEntry(id, key, entry, symbol, symbolTypes, symbolBlocks);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement allow = object.has("allow") ? object.get("allow")
                    : object.get("允许");
            if (allow == null) {
                throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key
                        + "' allow entry must be a string or {\"allow\": ...}");
            }
            collectAllowEntry(id, key, allow, symbol, symbolTypes, symbolBlocks);
            return;
        }
        throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key
                + "' allow entry must be a string, an array or an object");
    }

    private static void addSymbolHatch(ResourceLocation id, String key, char symbol, String raw,
                                       Map<Character, Set<MultiblockPartType>> symbolTypes,
                                       Map<Character, Set<ResourceLocation>> symbolBlocks) {
        Set<MultiblockPartType> types = symbolTypes.computeIfAbsent(symbol, ignored -> new LinkedHashSet<>());
        Set<ResourceLocation> blocks = symbolBlocks.computeIfAbsent(symbol, ignored -> new LinkedHashSet<>());
        try {
            parseHatchEntry(id, raw, types, blocks);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key
                    + "' allow entry: " + error.getMessage(), error);
        }
    }

    /**
     * 在世界中按控制器位置与朝向匹配整份结构。
     *
     * @param level         世界
     * @param controllerPos 控制器坐标
     * @param facing        控制器正面朝向（水平方向）
     */
    public MatchResult match(Level level, BlockPos controllerPos, Direction facing) {
        List<MultiblockPart> parts = new ArrayList<>();
        for (Cell cell : this.cells) {
            BlockPos pos = offset(controllerPos, cell.dx(), cell.dy(), cell.dz(), facing);
            if (!level.isLoaded(pos)) {
                return MatchResult.failure(pos, cell.matcher().describe(), "未加载的区块");
            }
            MultiblockPart detected = null;
            if (cell.matcher().test(level, pos)) {
                if (cell.matcher() instanceof MultiblockMatcher.Part partMatcher) {
                    if (!(level.getBlockEntity(pos) instanceof MultiblockPart part)) {
                        return MatchResult.failure(pos, partMatcher.describe(), actualBlock(level, pos));
                    }
                    detected = part;
                } else {
                    // 标签里如果包含仓室，也要收集，否则仓室不会接到控制器上。
                    detected = this.supportedHatch(level, pos, cell);
                }
            } else {
                // 普通方块位置允许放 {支持的仓室} 里列出的仓室。
                detected = this.supportedHatch(level, pos, cell);
                if (detected == null) {
                    return MatchResult.failure(pos, cell.matcher().describe(), actualBlock(level, pos));
                }
            }
            if (detected != null) {
                BlockPos owner = detected.controllerPos();
                if (owner != null && !owner.equals(controllerPos)) {
                    return MatchResult.failure(pos, cell.matcher().describe(), "已被其它控制器占用");
                }
                parts.add(detected);
            }
        }
        return MatchResult.success(parts);
    }

    /**
     * 该位置是否已经满足这个格子（精确方块、标签，或白名单里的仓室）。
     *
     * <p>终端搭结构时用它跳过“已经对了”的位置，避免把已放好的仓室盖掉。</p>
     */
    public boolean accepts(Level level, BlockPos pos, Cell cell) {
        if (cell.matcher().test(level, pos)) {
            return true;
        }
        return this.supportedHatch(level, pos, cell) != null;
    }

    /**
     * 判断该位置是否放了白名单里的仓室。
     */
    @Nullable
    private MultiblockPart supportedHatch(Level level, BlockPos pos, Cell cell) {
        if (!(cell.matcher() instanceof MultiblockMatcher.Exact)
                && !(cell.matcher() instanceof MultiblockMatcher.Tag)) {
            return null;
        }
        Block block = level.getBlockState(pos).getBlock();
        MultiblockPartType type = MultiblockRegistration.partType(block);
        if (type == null || !this.allowsHatch(cell.symbol(), type, block)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof MultiblockPart part ? part : null;
    }

    private boolean allowsHatch(char symbol, MultiblockPartType type, Block block) {
        Set<MultiblockPartType> types = this.symbolHatchTypes.get(symbol);
        Set<ResourceLocation> blocks = this.symbolHatchBlocks.get(symbol);
        if (types == null && blocks == null) {
            types = this.globalHatchTypes;
            blocks = this.globalHatchBlocks;
        }
        if (types != null && types.contains(type)) {
            return true;
        }
        return blocks != null && blocks.contains(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static String actualBlock(Level level, BlockPos pos) {
        return MultiblockMatcher.blockId(level, pos).toString();
    }

    /**
     * 把结构内偏移按控制器朝向旋转到世界坐标。
     *
     * <p>结构按控制器朝北（{@code NORTH}）书写；朝东时整体顺时针旋转 90°，
     * 以此类推。{@code y} 始终向上。</p>
     */
    public static BlockPos offset(BlockPos controllerPos, int dx, int dy, int dz, Direction facing) {
        int x;
        int z;
        switch (facing) {
            case EAST -> {
                x = -dz;
                z = dx;
            }
            case SOUTH -> {
                x = -dx;
                z = -dz;
            }
            case WEST -> {
                x = dz;
                z = -dx;
            }
            default -> {
                x = dx;
                z = dz;
            }
        }
        return controllerPos.offset(x, dy, z);
    }

    /**
     * 给日志用的简短尺寸描述。
     */
    public String describeSize() {
        return this.sizeX + "x" + this.sizeY + "x" + this.sizeZ;
    }
}
