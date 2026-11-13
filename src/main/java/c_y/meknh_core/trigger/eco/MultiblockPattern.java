package c_y.meknh_core.trigger.eco;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MultiblockPattern {

    private static final Logger LOGGER = LogUtils.getLogger();

    public record Mapping(char symbol, ResourceLocation oldBlock, ResourceLocation newBlock) {
    }

    public record Cell(int x, int y, int z, char symbol) {
    }

    public record Placement(BlockPos origin, MultiblockOrientation orientation) {
    }

    public record Step(BlockPos pos, Block newBlock, Direction facing) {
    }

    public record Diagnosis(int matched, int total, String detail) {
    }

    private final String id;
    private final char[][][] grid;
    private final List<Cell> cells;
    private final Map<Character, Mapping> mappings;
    private final char[] anchorSymbols;
    private final List<Cell> anchorCells;

    private final Map<Character, int[]> facingOverrides;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;


    private volatile Map<Character, Block> oldBlocks;

    private volatile Map<Character, Block> newBlocks;

    private MultiblockPattern(Builder builder) {
        if (builder.layers.isEmpty()) {
            throw new IllegalStateException("结构 " + builder.id + " 没有声明任何层");
        }

        this.id = builder.id;
        this.mappings = Map.copyOf(builder.mappings);
        this.anchorSymbols = builder.anchors.clone();
        this.facingOverrides = Map.copyOf(builder.facingOverrides);
        this.sizeY = builder.layers.size();
        this.sizeZ = builder.layers.get(0).length;
        this.sizeX = this.sizeZ == 0 ? 0 : builder.layers.get(0)[0].length();
        if (this.sizeX == 0 || this.sizeZ == 0) {
            throw new IllegalStateException("结构 " + id + " 的尺寸不能为 0");
        }
        if (this.anchorSymbols.length == 0) {
            throw new IllegalStateException("结构 " + id + " 没有声明锚点字符");
        }
        for (char symbol : this.anchorSymbols) {
            if (!mappings.containsKey(symbol)) {
                throw new IllegalStateException("结构 " + id + " 的锚点字符 '" + symbol + "' 没有定义方块映射");
            }
        }
        for (Map.Entry<Character, int[]> entry : this.facingOverrides.entrySet()) {
            if (!mappings.containsKey(entry.getKey())) {
                throw new IllegalStateException("结构 " + id + " 的朝向字符 '" + entry.getKey() + "' 没有定义方块映射");
            }
            int[] dir = entry.getValue();
            if (Math.abs(dir[0]) + Math.abs(dir[1]) != 1) {
                throw new IllegalStateException("结构 " + id + " 的朝向字符 '" + entry.getKey()
                        + "' 必须指定一个水平单位方向");
            }
        }

        this.grid = new char[sizeY][sizeZ][sizeX];
        List<Cell> allCells = new ArrayList<>();
        List<Cell> anchors = new ArrayList<>();

        for (int y = 0; y < sizeY; y++) {
            String[] rows = builder.layers.get(y);
            if (rows.length != sizeZ) {
                throw new IllegalStateException("结构 " + id + " 的第 " + y + " 层有 " + rows.length
                        + " 行，但第一层有 " + sizeZ + " 行");
            }
            for (int z = 0; z < sizeZ; z++) {
                String row = rows[z];
                if (row.length() != sizeX) {
                    throw new IllegalStateException("结构 " + id + " 的第 " + y + " 层第 " + z
                            + " 行长度为 " + row.length() + "，应为 " + sizeX);
                }
                for (int x = 0; x < sizeX; x++) {
                    char symbol = row.charAt(x);
                    if (!mappings.containsKey(symbol)) {
                        throw new IllegalStateException("结构 " + id + " 中字符 '" + symbol
                                + "'（Y=" + y + ", Z=" + z + ", X=" + x + "）没有定义方块映射");
                    }
                    grid[y][z][x] = symbol;
                    Cell cell = new Cell(x, y, z, symbol);
                    allCells.add(cell);
                    if (contains(this.anchorSymbols, symbol)) {
                        anchors.add(cell);
                    }
                }
            }
        }

        if (anchors.isEmpty()) {
            throw new IllegalStateException("结构 " + id + " 的字符图里没有出现任何锚点字符");
        }
        this.cells = List.copyOf(allCells);
        this.anchorCells = List.copyOf(anchors);
    }

    private static boolean contains(char[] symbols, char symbol) {
        for (char value : symbols) {
            if (value == symbol) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int cellCount() {
        return cells.size();
    }

    /** 锚点格子的数量（可右键的位置种类）。 */
    public int anchorCount() {
        return anchorCells.size();
    }

    /**
     * 以「被右键的方块的世界坐标」为锚点，尝试找出一个完整匹配的结构。
     * <p>
     * 对每种朝向、每个锚点格子都会反推出结构原点并整结构校验；返回第一个完全匹配的结果。
     */
    public Optional<Placement> findPlacement(Level level, BlockPos clickedPos) {
        Set<Long> tried = new LinkedHashSet<>();
        for (MultiblockOrientation orientation : MultiblockOrientation.ALL) {
            for (Cell anchor : anchorCells) {
                BlockPos offset = orientation.transform(anchor.x(), anchor.y(), anchor.z(), sizeX, sizeZ);
                BlockPos origin = clickedPos.subtract(offset);
                if (!tried.add(originKey(origin, orientation))) {
                    continue;
                }
                Placement placement = new Placement(origin, orientation);
                if (matches(level, placement)) {
                    return Optional.of(placement);
                }
            }
        }
        return Optional.empty();
    }

    private static long originKey(BlockPos origin, MultiblockOrientation orientation) {
        return origin.asLong() * 31L + (orientation.quarterTurns() * 2L + (orientation.isMirrored() ? 1 : 0));
    }

    /** 校验整个结构：每一个格子都必须是期望方块。 */
    public boolean matches(Level level, Placement placement) {
        Map<Character, Block> expected = oldBlocks();
        if (expected.isEmpty()) {
            return false;
        }
        for (Cell cell : cells) {
            Block block = expected.get(cell.symbol());
            if (block == null) {
                return false;
            }
            if (!level.getBlockState(worldPos(placement, cell)).is(block)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 生成替换计划。
     * <p>
     * 顺序为「从下到上、从左到右」：先按世界 Y 升序，再按世界 Z 升序（一层里的两行），
     * 最后按世界 X 升序（行内从左到右）。
     */
    public List<Step> plan(Placement placement) {
        Map<Character, Block> targets = newBlocks();
        if (targets.isEmpty()) {
            return List.of();
        }
        List<Step> steps = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            Block target = targets.get(cell.symbol());
            if (target == null) {
                continue;
            }
            steps.add(new Step(worldPos(placement, cell), target, outwardFacing(placement, cell)));
        }
        return sortByWorldOrder(steps);
    }

    private static List<Step> sortByWorldOrder(List<Step> steps) {
        steps.sort(Comparator.comparingInt((Step step) -> step.pos().getY())
                .thenComparingInt(step -> step.pos().getZ())
                .thenComparingInt(step -> step.pos().getX()));
        return List.copyOf(steps);
    }

    /** 读取某个格子的字符（局部坐标，主要用于调试与日志）。 */
    public char symbolAt(int x, int y, int z) {
        return grid[y][z][x];
    }

    /**
     * 某个格子应该让新方块朝向的世界方向。
     * <p>
     * 默认规则：背离结构内侧（也就是朝向结构外侧的空气一侧）。
     * 结构在 Z 方向只有 {@code sizeZ} 格厚，靠 Z=0 那侧的格子朝局部 -Z，靠 Z=sizeZ-1 那侧的格子朝局部 +Z，
     * 这样方块的背面永远贴着对面那一排方块，正面朝着外面，不会出现「正面顶着隔壁方块背面」的情况。
     * <p>
     * 例外：用 {@code Builder#facing(char, int, int)} 指定了固定局部方向的字符（比如 C9 尾巴上的冷却控制器，
     * 要朝着「离开控制器」的方向），就按那个局部方向算。最后再按结构朝向旋转/镜像成世界方向。
     */
    public Direction outwardFacing(Placement placement, Cell cell) {
        int[] override = facingOverrides.get(cell.symbol());
        if (override != null) {
            return placement.orientation().transformDirection(override[0], override[1]);
        }
        // 离哪一侧更近就朝哪一侧：sizeZ=2 时 z=0 朝 -Z、z=1 朝 +Z
        int sign = cell.z() < sizeZ - 1 - cell.z() ? -1 : 1;
        return placement.orientation().transformDirection(0, sign);
    }

    /** 把结构中的一个格子换算成世界坐标。 */
    public BlockPos worldPos(Placement placement, Cell cell) {
        return placement.origin().offset(
                placement.orientation().transform(cell.x(), cell.y(), cell.z(), sizeX, sizeZ));
    }

    /** 这个方块是不是本结构的锚点方块（用来判断玩家点得对不对）。 */
    public boolean isAnchorBlock(Block block) {
        Map<Character, Block> expected = oldBlocks();
        for (char symbol : anchorSymbols) {
            if (expected.get(symbol) == block) {
                return true;
            }
        }
        return false;
    }

    /** 锚点方格期望的方块列表，用于日志。 */
    public List<Block> anchorBlocks() {
        Map<Character, Block> expected = oldBlocks();
        List<Block> out = new ArrayList<>();
        for (char symbol : anchorSymbols) {
            Block block = expected.get(symbol);
            if (block != null) {
                out.add(block);
            }
        }
        return out;
    }

    /**
     * 匹配失败时的诊断：对 8 种朝向各统计一次「命中的格子数」，
     * 并输出最接近的朝向里具体是哪些格子对不上（期望方块 / 实际方块）。
     */
    public Diagnosis diagnose(Level level, BlockPos clickedPos) {
        StringBuilder detail = new StringBuilder();
        detail.append("多方块 ").append(id).append(" 检测失败：点击位置 ")
                .append(clickedPos.toShortString())
                .append("（").append(blockId(level.getBlockState(clickedPos))).append("）");

        Map<Character, Block> expected = oldBlocks();
        if (expected.isEmpty()) {
            detail.append("\n  期望方块解析失败，无法比对（详见上方 WARN）");
            return new Diagnosis(0, cells.size(), detail.toString());
        }

        int bestMatched = -1;
        MultiblockOrientation bestOrientation = null;
        List<Cell> bestWrongCells = List.of();

        for (MultiblockOrientation orientation : MultiblockOrientation.ALL) {
            for (Cell anchor : anchorCells) {
                BlockPos offset = orientation.transform(anchor.x(), anchor.y(), anchor.z(), sizeX, sizeZ);
                Placement placement = new Placement(clickedPos.subtract(offset), orientation);
                int matched = 0;
                List<Cell> wrong = new ArrayList<>();
                for (Cell cell : cells) {
                    Block block = expected.get(cell.symbol());
                    if (block != null && level.getBlockState(worldPos(placement, cell)).is(block)) {
                        matched++;
                    } else if (wrong.size() < 8) {
                        wrong.add(cell);
                    }
                }
                if (matched > bestMatched) {
                    bestMatched = matched;
                    bestOrientation = orientation;
                    bestWrongCells = wrong;
                }
            }
        }

        detail.append("\n  最优朝向 ").append(bestOrientation).append(" 命中 ")
                .append(bestMatched).append('/').append(cells.size());
        if (bestOrientation != null && !bestWrongCells.isEmpty()) {
            Cell anchor = anchorCells.get(0);
            BlockPos offset = bestOrientation.transform(anchor.x(), anchor.y(), anchor.z(), sizeX, sizeZ);
            Placement placement = new Placement(clickedPos.subtract(offset), bestOrientation);
            detail.append("，前几处不匹配（最多 8 条）：");
            for (Cell cell : bestWrongCells) {
                BlockPos world = worldPos(placement, cell);
                detail.append("\n    局部(X=").append(cell.x()).append(",Y=").append(cell.y())
                        .append(",Z=").append(cell.z()).append(") 世界").append(world.toShortString())
                        .append(" 期望 ").append(mappingOf(cell.symbol()))
                        .append(" 实际 ").append(blockId(level.getBlockState(world)));
            }
        }
        return new Diagnosis(Math.max(bestMatched, 0), cells.size(), detail.toString());
    }

    private String mappingOf(char symbol) {
        Mapping mapping = mappings.get(symbol);
        return mapping == null ? ("字符'" + symbol + "'未定义") : mapping.oldBlock().toString();
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private Map<Character, Block> oldBlocks() {
        Map<Character, Block> cache = this.oldBlocks;
        if (cache != null) {
            return cache;
        }
        Map<Character, Block> resolved = new LinkedHashMap<>();
        for (Mapping mapping : mappings.values()) {
            Block block = resolveBlock(mapping.oldBlock(), "期望方块");
            if (block == null) {
                return Map.of();
            }
            resolved.put(mapping.symbol(), block);
        }
        cache = Map.copyOf(resolved);
        this.oldBlocks = cache;
        return cache;
    }

    private Map<Character, Block> newBlocks() {
        Map<Character, Block> cache = this.newBlocks;
        if (cache != null) {
            return cache;
        }
        Map<Character, Block> resolved = new LinkedHashMap<>();
        for (Mapping mapping : mappings.values()) {
            Block block = resolveBlock(mapping.newBlock(), "替换方块");
            if (block == null) {
                continue;
            }
            resolved.put(mapping.symbol(), block);
        }
        cache = Map.copyOf(resolved);
        this.newBlocks = cache;
        return cache;
    }

    private Block resolveBlock(ResourceLocation id, String role) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) {
            // 注册表里查不到就先不缓存，等模组注册完成后重试
            LOGGER.warn("[{}] 无法解析{} {}，结构检测将跳过该结构", this.id, role, id);
            return null;
        }
        return block;
    }

    /** 结构模板的构造器：按 Y 从低到高逐层追加字符图。 */
    public static final class Builder {

        private final String id;
        private final List<String[]> layers = new ArrayList<>();
        private final Map<Character, Mapping> mappings = new LinkedHashMap<>();
        private final Map<Character, int[]> facingOverrides = new LinkedHashMap<>();
        private char[] anchors = new char[0];

        private Builder(String id) {
            this.id = id;
        }

        /**
         * 追加一层字符图。
         *
         * @param rows 该层的各行，第 0 行对应 Z=0，第 1 行对应 Z=1……
         */
        public Builder layer(String... rows) {
            this.layers.add(rows.clone());
            return this;
        }

        /** 声明一个字符对应的「旧方块 → 新方块」。 */
        public Builder map(char symbol, String oldBlock, String newBlock) {
            ResourceLocation oldId = parse(symbol, oldBlock);
            ResourceLocation newId = parse(symbol, newBlock);
            this.mappings.put(symbol, new Mapping(symbol, oldId, newId));
            return this;
        }

        /**
         * 指定锚点字符：只有这些字符对应的格子可以被右键触发。
         * 一般写成「唯一的那个功能方块 + ae2:controller」，两个都点得到。
         */
        public Builder anchors(char... symbols) {
            this.anchors = symbols.clone();
            return this;
        }

        /**
         * 给某个字符的替换方块指定固定朝向（结构局部坐标下的水平方向，例如 {@code (1, 0)} 表示局部 +X）。
         * <p>
         * 不指定时按「朝结构外侧」处理；这些特殊方块不跟随「外侧」规则。
         */
        public Builder facing(char symbol, int localDx, int localDz) {
            this.facingOverrides.put(symbol, new int[]{localDx, localDz});
            return this;
        }

        public MultiblockPattern build() {
            return new MultiblockPattern(this);
        }

        private ResourceLocation parse(char symbol, String value) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                throw new IllegalArgumentException("结构 " + this.id + " 中字符 '" + symbol
                        + "' 的方块 ID 非法：" + value);
            }
            return id;
        }
    }
}
