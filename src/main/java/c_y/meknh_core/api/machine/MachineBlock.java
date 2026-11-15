package c_y.meknh_core.api.machine;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.gui.menu.MachineMenu;
import c_y.meknh_core.decor.description.MachineNames;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
 * 数据驱动机器的方块。
 *
 * <p>方块状态只有 {@code facing} 与 {@code active} 两个属性：
 * 前者决定正面朝向（覆盖层贴图方向），后者决定使用激活/未激活覆盖层
 * （{@code 应用模型贴图.txt} 的 multipart 规则）。</p>
 *
 * <p>界面走原版容器机制（和 Mekanism 的机器一样）：服务端
 * {@code player.openMenu(...)} 带上方块坐标，客户端用
 * {@link MachineRegistration#MACHINE_MENU} 的工厂建 {@link MachineMenu}，
 * 再由 {@code MachineScreen} 渲染。</p>
 */
public class MachineBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final MachineDefinition definition;
    private final MachineLevel level;

    public MachineBlock(Properties properties, MachineDefinition definition, MachineLevel level) {
        super(properties);
        this.definition = definition;
        this.level = level;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new MachineBlock(properties, this.definition, this.level));
    }

    public MachineDefinition definition() {
        return this.definition;
    }

    public MachineLevel level() {
        return this.level;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * 右键打开机器容器。方块坐标作为附加数据发给客户端，供菜单找回方块实体。
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            if (!machine.canUse(serverPlayer)) {
                serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("gui.meknh_core.security.denied"), true);
                return InteractionResult.FAIL;
            }
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (windowId, inventory, p) -> new MachineMenu(windowId, inventory, machine),
                            MachineNames.displayName(this.definition.id(), this.level)),
                    buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * 放下时记录主人，供「安全」tab 的私有模式使用。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.setOwner(player);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        BlockEntityType<MachineBlockEntity> type = MachineRegistration.getBlockEntityType(this.definition.id());
        return type == null ? null : new MachineBlockEntity(type, pos, state, this.definition, this.level);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof MachineBlockEntity machine) {
                machine.serverTick();
            }
        };
    }
}
