package c_y.meknh_core.api.multiblock;

import net.minecraft.core.BlockPos;import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 结构里一个位置允许的方块。
 *
 * <p>由 {@link MultiblockStructure} 在解析 JSON 时生成，匹配时只读世界，
 * 不修改任何状态。五种形态分别对应结构 JSON 的写法：</p>
 *
 * <table border="1">
 *     <caption>组成写法</caption>
 *     <tr><th>写法</th><th>含义</th></tr>
 *     <tr><td>{@code "meknh_core:lv_machine_casing"}</td><td>精确方块</td></tr>
 *     <tr><td>{@code "#c:stone"}</td><td>方块标签</td></tr>
 *     <tr><td>{@code "air"} / {@code "minecraft:air"}</td><td>必须是空气</td></tr>
 *     <tr><td>{@code "any"} / {@code "*"}</td><td>任意方块（含空气）</td></tr>
 *     <tr><td>{@code "part:item_input"}</td><td>指定类型的仓室</td></tr>
 *     <tr><td>{@code "controller"}</td><td>控制器方块本身</td></tr>
 * </table>
 */
public sealed interface MultiblockMatcher {

    /**
     * 判断该位置是否满足本规则。
     *
     * @param level 世界
     * @param pos   绝对坐标
     */
    boolean test(Level level, BlockPos pos);

    /**
     * 用于结构错误提示的简短描述。
     */
    String describe();

    /**
     * 精确匹配某个方块 ID。
     */
    record Exact(ResourceLocation blockId) implements MultiblockMatcher {

        @Override
        public boolean test(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            Block expected = this.resolve();
            return expected != Blocks.AIR && state.is(expected);
        }

        @Override
        public String describe() {
            return this.blockId.toString();
        }

        /**
         * 延迟解析：结构 JSON 在模组构造阶段读取，那时其它模组的方块还没进入注册表。
         */
        public Block resolve() {
            Block block = BuiltInRegistries.BLOCK.get(this.blockId);
            return block == null ? Blocks.AIR : block;
        }
    }

    /**
     * 匹配方块标签。
     */
    record Tag(TagKey<Block> tag) implements MultiblockMatcher {

        @Override
        public boolean test(Level level, BlockPos pos) {
            return level.getBlockState(pos).is(this.tag);
        }

        @Override
        public String describe() {
            return "#" + this.tag.location();
        }
    }

    /**
     * 匹配指定类型的多方块仓室。
     */
    record Part(MultiblockPartType type) implements MultiblockMatcher {

        @Override
        public boolean test(Level level, BlockPos pos) {
            return MultiblockRegistration.partType(level.getBlockState(pos).getBlock()) == this.type
                    && level.getBlockEntity(pos) instanceof MultiblockPart;
        }

        @Override
        public String describe() {
            return "part:" + this.type.id();
        }
    }

    /**
     * 必须是空气。
     */
    record Air() implements MultiblockMatcher {

        @Override
        public boolean test(Level level, BlockPos pos) {
            return level.getBlockState(pos).isAir();
        }

        @Override
        public String describe() {
            return "air";
        }
    }

    /**
     * 任意方块（包括空气）。
     */
    record Any() implements MultiblockMatcher {

        @Override
        public boolean test(Level level, BlockPos pos) {
            return true;
        }

        @Override
        public String describe() {
            return "any";
        }
    }

    /**
     * 控制器方块本身。
     */
    record Controller() implements MultiblockMatcher {

        @Override
        public boolean test(Level level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity;
        }

        @Override
        public String describe() {
            return "controller";
        }
    }

    /**
     * 从结构 JSON 的 {@code 组成} 值解析规则。
     *
     * @param namespace 默认命名空间（结构 JSON 所在命名空间）
     * @param value     映射值
     */
    static MultiblockMatcher parse(String namespace, String value) {
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty block mapping");
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("controller")) {
            return new Controller();
        }
        if (lower.equals("air") || lower.equals("minecraft:air")) {
            return new Air();
        }
        if (lower.equals("any") || lower.equals("*")) {
            return new Any();
        }
        if (lower.startsWith("part:")) {
            MultiblockPartType type = MultiblockPartType.byId(text.substring("part:".length()));
            if (type == null) {
                throw new IllegalArgumentException("unknown part type '" + text + "'");
            }
            return new Part(type);
        }
        if (text.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(qualify(namespace, text.substring(1)));
            if (tagId == null) {
                throw new IllegalArgumentException("invalid block tag '" + text + "'");
            }
            return new Tag(TagKey.create(Registries.BLOCK, tagId));
        }
        ResourceLocation id = ResourceLocation.tryParse(qualify(namespace, text));
        if (id == null) {
            throw new IllegalArgumentException("invalid block id '" + text + "'");
        }
        return new Exact(id);
    }

    /**
     * 补全命名空间：{@code stone} → {@code <namespace>:stone}。
     */
    static String qualify(String namespace, String id) {
        return id.contains(":") ? id : namespace + ":" + id;
    }

    /**
     * 方便在调试信息里拿到方块 ID。
     */
    static ResourceLocation blockId(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }
}
