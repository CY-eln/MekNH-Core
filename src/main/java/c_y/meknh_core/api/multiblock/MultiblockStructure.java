package c_y.meknh_core.api.multiblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 一份多方块结构：由若干相对控制器偏移的 {@link Cell} 组成。
 *
 * <p>结构来自 JSON 的 {@code 结构} 与 {@code 组成} 两个字段，
 * 解析规则见 {@code MULTIBLOCK_JSON.md}。本类只负责几何与匹配，不持有世界状态，
 * 因此可以被多个控制器共享。</p>
 *
 * <p>坐标系：{@code x} 是行内字符方向，{@code z} 是行方向（深度），{@code y} 是层方向（向上）。
 * 所有偏移都相对控制器方块，并按控制器朝向整体旋转。</p>
 */
public final class MultiblockStructure {

    /**
     * 一个结构位置。
     *
     * @param dx      相对控制器的 x 偏移
     * @param dy      相对控制器的 y 偏移
     * @param dz      相对控制器的 z 偏移
     * @param matcher 该位置允许的方块
     */
    public record Cell(int dx, int dy, int dz, MultiblockMatcher matcher) {
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

    private MultiblockStructure(List<Cell> cells, int sizeX, int sizeY, int sizeZ) {
        this.cells = List.copyOf(cells);
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
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
        return new MultiblockStructure(cells, sizeX, sizeY, sizeZ);
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
        List<List<String>> layers = readLayers(id, structureElement);
        if (layers.isEmpty() || layers.getFirst().isEmpty()) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' must not be empty");
        }

        int sizeY = layers.size();
        int sizeZ = layers.getFirst().size();
        int sizeX = 0;
        for (int layer = 0; layer < sizeY; layer++) {
            List<String> rows = layers.get(layer);
            if (rows.size() != sizeZ) {
                throw new IllegalArgumentException("Multiblock " + id + ": layer " + layer + " has "
                        + rows.size() + " row(s), expected " + sizeZ);
            }
            for (String row : rows) {
                sizeX = Math.max(sizeX, row.length());
            }
        }
        if (sizeX == 0) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' rows must not be empty");
        }
        // 较短的行在右侧补空格（空格 = 任意方块），方便写逐层收窄的结构。
        for (List<String> rows : layers) {
            for (int row = 0; row < rows.size(); row++) {
                String value = rows.get(row);
                if (value.length() < sizeX) {
                    rows.set(row, value + " ".repeat(sizeX - value.length()));
                }
            }
        }

        JsonObject mapping = json.has("组成") && json.get("组成").isJsonObject()
                ? json.getAsJsonObject("组成")
                : new JsonObject();

        List<Cell> cells = new ArrayList<>();
        int controllerX = Integer.MIN_VALUE;
        int controllerY = 0;
        int controllerZ = 0;
        int controllers = 0;

        for (int y = 0; y < sizeY; y++) {
            List<String> rows = layers.get(y);
            for (int z = 0; z < sizeZ; z++) {
                String row = rows.get(z);
                for (int x = 0; x < sizeX; x++) {
                    char symbol = row.charAt(x);
                    MultiblockMatcher matcher = resolve(id, mapping, symbol);
                    if (matcher instanceof MultiblockMatcher.Controller) {
                        controllers++;
                        controllerX = x;
                        controllerY = y;
                        controllerZ = z;
                    }
                    cells.add(new Cell(x, y, z, matcher));
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
            relative.add(new Cell(cell.dx() - controllerX, cell.dy() - controllerY,
                    cell.dz() - controllerZ, cell.matcher()));
        }
        return new MultiblockStructure(relative, sizeX, sizeY, sizeZ);
    }

    private static List<List<String>> readLayers(ResourceLocation id, JsonElement element) {
        List<List<String>> layers = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            // 多行字符串：一行 = 一层（y0 在最上面那行，向上递增）
            addLineLayers(layers, element.getAsString());
            return layers;
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Multiblock " + id + ": '结构' must be a string or an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0) {
            return layers;
        }
        if (array.get(0).isJsonArray()) {
            // 数组套数组：外层每项是一层，内层每个字符串是这一层的一行（z 方向）
            for (JsonElement layerElement : array) {
                if (!layerElement.isJsonArray()) {
                    throw new IllegalArgumentException("Multiblock " + id + ": every layer must be an array of rows");
                }
                List<String> rows = new ArrayList<>();
                for (JsonElement rowElement : layerElement.getAsJsonArray()) {
                    rows.add(stripCarriageReturn(rowElement.getAsString()));
                }
                layers.add(rows);
            }
        } else {
            // 字符串数组：每个字符串是一层（同样支持字符串内部换行）
            for (JsonElement layerElement : array) {
                addLineLayers(layers, layerElement.getAsString());
            }
        }
        return layers;
    }

    /**
     * 把一段文本按换行拆成若干“单行层”，每层只有一行（z=0）。
     */
    private static void addLineLayers(List<List<String>> layers, String text) {
        String[] lines = text.split("\n", -1);
        int last = lines.length - 1;
        for (int index = 0; index <= last; index++) {
            // 末尾换行不算一层
            if (index == last && lines[index].isEmpty() && last > 0) {
                break;
            }
            List<String> rows = new ArrayList<>(1);
            rows.add(stripCarriageReturn(lines[index]));
            layers.add(rows);
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
    private static MultiblockMatcher resolve(ResourceLocation id, JsonObject mapping, char symbol) {
        return switch (symbol) {
            case '@' -> new MultiblockMatcher.Controller();
            case '#' -> new MultiblockMatcher.Air();
            case '.', '_', ' ' -> new MultiblockMatcher.Any();
            default -> {
                String key = String.valueOf(symbol);
                if (!mapping.has(key)) {
                    throw new IllegalArgumentException("Multiblock " + id + ": symbol '" + key
                            + "' is used in '结构' but has no entry in '组成'");
                }
                JsonElement value = mapping.get(key);
                if (!value.isJsonPrimitive()) {
                    throw new IllegalArgumentException("Multiblock " + id + ": '组成." + key + "' must be a string");
                }
                yield MultiblockMatcher.parse(id.getNamespace(), value.getAsString());
            }
        };
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
            if (!cell.matcher().test(level, pos)) {
                return MatchResult.failure(pos, cell.matcher().describe(), actualBlock(level, pos));
            }
            if (cell.matcher() instanceof MultiblockMatcher.Part partMatcher) {
                if (!(level.getBlockEntity(pos) instanceof MultiblockPart part)) {
                    return MatchResult.failure(pos, partMatcher.describe(), actualBlock(level, pos));
                }
                BlockPos owner = part.controllerPos();
                if (owner != null && !owner.equals(controllerPos)) {
                    return MatchResult.failure(pos, partMatcher.describe(), "已被其它控制器占用");
                }
                parts.add(part);
            }
        }
        return MatchResult.success(parts);
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
