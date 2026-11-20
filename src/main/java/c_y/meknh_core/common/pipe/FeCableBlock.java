package c_y.meknh_core.common.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

/**
 * FE 电缆方块：6 个方向自动连接相邻电缆 / 能量方块。
 *
 * <p>模型由 {@code MachineModelResources} 运行时生成：核心 6×6×6，臂按连接方向伸出，
 * 与 Mekanism 的小型线缆尺寸一致。</p>
 */
public class FeCableBlock extends Block implements EntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape ARM_NORTH = Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 6.0D);
    private static final VoxelShape ARM_SOUTH = Block.box(5.0D, 5.0D, 10.0D, 11.0D, 11.0D, 16.0D);
    private static final VoxelShape ARM_WEST = Block.box(0.0D, 5.0D, 5.0D, 6.0D, 11.0D, 11.0D);
    private static final VoxelShape ARM_EAST = Block.box(10.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D);
    private static final VoxelShape ARM_UP = Block.box(5.0D, 10.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape ARM_DOWN = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);

    private final FePipeDefinition definition;

    public FeCableBlock(Properties properties, FePipeDefinition definition) {
        super(properties);
        this.definition = definition;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    public FePipeDefinition definition() {
        return this.definition;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, ARM_NORTH);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, ARM_SOUTH);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, ARM_WEST);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, ARM_EAST);
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, ARM_UP);
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, ARM_DOWN);
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeCableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FeCableBlockEntity cable) {
                FeCableBlockEntity.tick(tickLevel, pos, cable);
            }
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            updateConnections(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            updateConnections(level, pos);
        }
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos,
                                                 int flags, int recursionLeft) {
        // 连接状态由 neighborChanged 处理。
    }

    /**
     * 重新计算 6 个方向的连接状态。
     */
    public static void updateConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FeCableBlock)) {
            return;
        }
        BlockState updated = state
                .setValue(NORTH, canConnect(level, pos, Direction.NORTH))
                .setValue(SOUTH, canConnect(level, pos, Direction.SOUTH))
                .setValue(EAST, canConnect(level, pos, Direction.EAST))
                .setValue(WEST, canConnect(level, pos, Direction.WEST))
                .setValue(UP, canConnect(level, pos, Direction.UP))
                .setValue(DOWN, canConnect(level, pos, Direction.DOWN));
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    /**
     * 相邻方块是否应该显示连接：同类电缆，或带 FE 能力的方块。
     */
    public static boolean canConnect(Level level, BlockPos pos, Direction side) {
        BlockPos target = pos.relative(side);
        if (!level.isLoaded(target)) {
            return false;
        }
        if (level.getBlockState(target).getBlock() instanceof FeCableBlock) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity == null) {
            return false;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite()) != null;
    }
}
