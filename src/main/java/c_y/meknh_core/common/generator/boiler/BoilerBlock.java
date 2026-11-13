package c_y.meknh_core.common.generator.boiler;

import c_y.meknh_core.api.generator.boiler.BoilerDefinition;
import c_y.meknh_core.api.generator.boiler.BoilerRegistration;
import c_y.meknh_core.api.generator.boiler.BoilerTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

public class BoilerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    private final BoilerDefinition definition;
    private final BoilerTier tier;

    public BoilerBlock(BoilerDefinition definition, BoilerTier tier, Properties properties) {
        super(properties);
        this.definition = definition;
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public BoilerDefinition definition() {
        return this.definition;
    }

    public BoilerTier tier() {
        return this.tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!(level.getBlockEntity(pos) instanceof BoilerBlockEntity)) {
                // 旧存档或异常放置导致 BE 缺失时，打开界面即修复。
                BlockEntityType<BoilerBlockEntity> type = BoilerRegistration.typeFor(this);
                if (type != null) {
                    level.setBlockEntity(new BoilerBlockEntity(type, pos, state));
                }
            }
            if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
                if (!boiler.canAccess(player.getUUID())) {
                    return InteractionResult.FAIL;
                }
                player.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new BoilerMenu(id, inventory, boiler),
                        Component.translatable(this.getDescriptionId())), pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) == null) {
            BlockEntityType<BoilerBlockEntity> type = BoilerRegistration.typeFor(this);
            if (type != null) {
                level.setBlockEntity(new BoilerBlockEntity(type, pos, state));
            }
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // 某些情况下服务端放置不会自动附着 BE，这里显式补建。
        if (!level.isClientSide && level.getBlockEntity(pos) == null) {
            BlockEntityType<BoilerBlockEntity> type = BoilerRegistration.typeFor(this);
            if (type != null) {
                level.setBlockEntity(new BoilerBlockEntity(type, pos, state));
            }
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler
                && placer instanceof net.minecraft.server.level.ServerPlayer player) {
            boiler.setOwner(player.getUUID());
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        BlockEntityType<BoilerBlockEntity> type = BoilerRegistration.typeFor(this);
        return type == null ? null : new BoilerBlockEntity(type, pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof BoilerBlockEntity boiler) {
                boiler.serverTick();
            }
        };
    }
}
