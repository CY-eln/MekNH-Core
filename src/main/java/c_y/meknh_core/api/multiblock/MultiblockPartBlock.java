package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块仓室方块。
 *
 * <p>仓室有水平朝向，只有正面显示功能覆盖层（物品/流体/化学品/能量图标）；
 * 功能由 {@link MultiblockPartType} 与电压等级决定。
 * 右键可以手动放入/取出物品、用桶交互流体，没有 GUI（内核不提供界面）。</p>
 */
public class MultiblockPartBlock extends Block implements EntityBlock {

    /**
     * 正面朝向；覆盖层画在模型的北面，blockstate 按此属性旋转。
     */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final MultiblockPartType partType;
    private final MachineLevel level;
    private final int amp;

    public MultiblockPartBlock(Properties properties, MultiblockPartType partType, MachineLevel level) {
        this(properties, partType, level, 2);
    }

    public MultiblockPartBlock(Properties properties, MultiblockPartType partType, MachineLevel level, int amp) {
        super(properties);
        this.partType = partType;
        this.level = level;
        this.amp = amp;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public MultiblockPartType partType() {
        return this.partType;
    }

    public MachineLevel level() {
        return this.level;
    }

    /**
     * 能源仓的安培数；非能源仓固定为 2。
     */
    public int amp() {
        return this.amp;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new MultiblockPartBlock(properties, this.partType, this.level));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(
                MultiblockRegistration.getPartBlockEntityType(this.partType),
                pos,
                state,
                this.partType,
                this.level
        );
    }

    /**
     * 右键交互：物品仓手动存取、流体仓用桶交互。没有 GUI。
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) {
            return part.usePart(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }
}
