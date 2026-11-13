package c_y.meknh_core.trigger.eco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;

public final class MultiblockReplacementTask {

    private final BlockPos controllerPos;
    private final List<MultiblockPattern.Step> steps;
    private int cursor;

    public MultiblockReplacementTask(BlockPos controllerPos, List<MultiblockPattern.Step> steps) {
        this.controllerPos = controllerPos.immutable();
        this.steps = steps;
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    public int total() {
        return steps.size();
    }

    public int done() {
        return cursor;
    }

    public boolean isFinished() {
        return cursor >= steps.size();
    }

    public void tick(ServerLevel level) {
        if (isFinished()) {
            return;
        }
        MultiblockPattern.Step step = steps.get(cursor++);
        BlockPos pos = step.pos();
        BlockState oldState = level.getBlockState(pos);
        BlockState newState = withOutwardFacing(step.newBlock().defaultBlockState(), step.facing());
        level.setBlock(pos, newState, Block.UPDATE_ALL);
        if (!oldState.isAir()) {
            // 2001 = 破坏方块粒子与音效，用来给每个方块的替换提供反馈
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(oldState));
        }
    }

    private static BlockState withOutwardFacing(BlockState state, Direction facing) {
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            if (!"facing".equals(property.getName()) || property.getValueClass() != Direction.class) {
                continue;
            }
            result = setFacing(result, property, facing);
            break;
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setFacing(BlockState state, Property<?> property, Direction facing) {
        if (!property.getPossibleValues().contains(facing)) {
            return state;
        }
        return state.setValue((Property) property, facing);
    }
}
