package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块控制器方块。
 *
 * <p>三个方块状态：{@code facing}（结构朝向）、{@code formed}（是否成型）、
 * {@code active}（是否正在处理配方）。右键打印结构诊断，不打开 GUI。</p>
 */
public class MultiblockControllerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final MultiblockDefinition definition;
    private final MachineLevel level;

    public MultiblockControllerBlock(Properties properties, MultiblockDefinition definition, MachineLevel level) {
        super(properties);
        this.definition = definition;
        this.level = level;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(ACTIVE, false));
    }

    public MultiblockDefinition definition() {
        return this.definition;
    }

    public MachineLevel level() {
        return this.level;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new MultiblockControllerBlock(properties, this.definition, this.level));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, ACTIVE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // 必须走注册的控制器工厂：放置时 LevelChunk 用的是 Block#newBlockEntity，
        // 不经过 BlockEntityType 的工厂，直接 new 会得到基类（焦炉就没有 GUI/容器）。
        return MultiblockRegistration.createController(
                MultiblockRegistration.getBlockEntityType(this.definition.id()),
                pos,
                state,
                this.definition,
                this.level
        );
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof MultiblockControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    /**
     * 相邻方块变化时让控制器尽快复检，而不是等下一个周期。
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.invalidateStructure();
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MultiblockControllerGui gui
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && gui.openControllerGui(serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.sendStatus(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * 桶/容器交互：实现了 {@link BucketFluidController} 的控制器可以直接用桶取流体。
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                                 BlockPos pos, Player player,
                                                                 net.minecraft.world.InteractionHand hand,
                                                                 BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof BucketFluidController buckets
                && net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(
                        player, hand, buckets.bucketFluidHandler())) {
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.onControllerRemoved();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
