package c_y.meknh_core.client.render.preview;

import c_y.meknh_core.api.multiblock.MultiblockMatcher;
import c_y.meknh_core.api.multiblock.MultiblockPartBlock;
import c_y.meknh_core.api.multiblock.MultiblockPartType;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.api.multiblock.MultiblockStructure;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@link MultiblockStructure} 翻译成「世界坐标 + 要显示的 BlockState + 层号」。
 *
 * <p>纯数据，不碰渲染也不碰世界；控制器自身不产出条目（真实方块就摆在那里）。</p>
 */
public final class MultiblockPreviewShape {

    /**
     * 一个要画的格子。
     *
     * @param pos   世界坐标（结构应该出现的位置）
     * @param state 显示用的方块状态（仓室已切成成型外观）
     * @param layer 相对高度层号，0 = 最低层
     */
    public record Entry(BlockPos pos, BlockState state, int layer) {}

    private final List<Entry> entries;
    private final int layerCount;

    private MultiblockPreviewShape(List<Entry> entries, int layerCount) {
        this.entries = List.copyOf(entries);
        this.layerCount = layerCount;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    /**
     * 层数（≥1）。
     */
    public int layerCount() {
        return this.layerCount;
    }

    /**
     * 按控制器朝向把结构铺到世界里。
     *
     * @param structure    结构
     * @param controllerPos 控制器坐标
     * @param facing       控制器正面朝向（水平四向）
     * @param tier         控制器等级（决定仓室外壳与等级）
     * @param formedCasing 是否给仓室套「已成型外壳」（无电压多方块为 false，与游戏内一致）
     */
    public static MultiblockPreviewShape of(MultiblockStructure structure, BlockPos controllerPos,
                                            Direction facing, MachineLevel tier, boolean formedCasing) {
        int minDy = Integer.MAX_VALUE;
        int maxDy = Integer.MIN_VALUE;
        for (MultiblockStructure.Cell cell : structure.cells()) {
            minDy = Math.min(minDy, cell.dy());
            maxDy = Math.max(maxDy, cell.dy());
        }

        List<Entry> entries = new ArrayList<>();
        for (MultiblockStructure.Cell cell : structure.cells()) {
            if (cell.matcher() instanceof MultiblockMatcher.Controller) {
                continue;
            }
            BlockPos relative = MultiblockStructure.offset(BlockPos.ZERO, cell.dx(), cell.dy(), cell.dz(), facing);
            BlockState state = displayState(cell.matcher(), relative, facing, tier, formedCasing);
            if (state == null || state.isAir()) {
                continue;
            }
            BlockPos world = MultiblockStructure.offset(controllerPos, cell.dx(), cell.dy(), cell.dz(), facing);
            entries.add(new Entry(world, state, cell.dy() - minDy));
        }
        int layerCount = Math.max(1, maxDy - minDy + 1);
        return new MultiblockPreviewShape(entries, layerCount);
    }

    @Nullable
    private static BlockState displayState(MultiblockMatcher matcher, BlockPos relative, Direction facing,
                                           MachineLevel tier, boolean formedCasing) {
        if (matcher instanceof MultiblockMatcher.Exact exact) {
            Block block = exact.resolve();
            return block == Blocks.AIR ? null : block.defaultBlockState();
        }
        if (matcher instanceof MultiblockMatcher.Tag tag) {
            return BuiltInRegistries.BLOCK.getTag(tag.tag())
                    .flatMap(set -> set.stream().findFirst())
                    .map(holder -> holder.value().defaultBlockState())
                    .orElse(null);
        }
        if (matcher instanceof MultiblockMatcher.Part part) {
            return hatchState(part.type(), tier, facing, relative, formedCasing);
        }
        // Air / Any / Controller：没有东西可画
        return null;
    }

    @Nullable
    private static BlockState hatchState(MultiblockPartType type, MachineLevel tier, Direction facing,
                                         BlockPos relative, boolean formedCasing) {
        MultiblockRegistration.PartBlockInfo info = pickPart(type, tier);
        if (info == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(info.id());
        if (!(block instanceof MultiblockPartBlock part)) {
            return null;
        }
        BlockState state = part.defaultBlockState();
        if (formedCasing) {
            state = state.setValue(MultiblockPartBlock.FORMED, true)
                    .setValue(MultiblockPartBlock.CASING, MultiblockPartBlock.casingIndex(tier));
        }
        return state.setValue(MultiblockPartBlock.FACING, outward(type, relative, facing));
    }

    /**
     * 同类型里优先取与控制器同等级的仓室，找不到退回该类型第一个。
     */
    @Nullable
    private static MultiblockRegistration.PartBlockInfo pickPart(MultiblockPartType type, MachineLevel tier) {
        MultiblockRegistration.PartBlockInfo fallback = null;
        for (MultiblockRegistration.PartBlockInfo info : MultiblockRegistration.partBlockInfos()) {
            if (info.type() != type) {
                continue;
            }
            if (info.level().equals(tier)) {
                return info;
            }
            if (fallback == null) {
                fallback = info;
            }
        }
        return fallback;
    }

    /**
     * 仓室覆盖层朝结构外面，看起来最自然；水泵仓恒朝上（与成型逻辑一致）。
     */
    private static Direction outward(MultiblockPartType type, BlockPos relative, Direction controllerFacing) {
        if (type == MultiblockPartType.PUMP_HATCH) {
            return Direction.UP;
        }
        int dx = relative.getX();
        int dz = relative.getZ();
        if (dx == 0 && dz == 0) {
            return controllerFacing;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
