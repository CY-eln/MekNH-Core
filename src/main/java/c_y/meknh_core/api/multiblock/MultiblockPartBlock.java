package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import com.mojang.serialization.MapCodec;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块仓室方块。
 *
 * <p>仓室有朝向：功能覆盖层（物品/流体/化学品/能量图标）只画在 {@link #FACING} 那一面。
 * 默认放置时朝向玩家；手持 Mekanism 的配置器（任意「配置」模式）潜行右键任意一面，
 * 会把覆盖层搬到那一面，这一面同时是仓室的<b>吸入口/弹出口</b>（自动搬运方向）。
 * 配置器的扳手模式潜行右键则是拆进背包（同 Mek 机器）。</p>
 *
 * <p>功能由 {@link MultiblockPartType} 与电压等级决定。普通右键打开仓室 GUI。</p>
 */
public class MultiblockPartBlock extends Block implements EntityBlock {

    /**
     * 覆盖层所在面（也是自动弹出方向）；6 个方向都可能，模型按此属性旋转。
     */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

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
        // 放置时朝向玩家，但不允许上下，避免刚放下就贴在顶/底。
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
        return simpleCodec(properties -> new MultiblockPartBlock(properties, this.partType, this.level, this.amp));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(
                MultiblockRegistration.getPartBlockEntityType(this.partType),
                pos,
                state,
                this.partType,
                this.level,
                this.amp
        );
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof MultiblockPartBlockEntity part) {
                part.tick();
            }
        };
    }

    /**
     * 配置面交互层：Mekanism 配置器（任意「配置」模式）+ 潜行 + 右键某个面
     * → 覆盖层、吸入/弹出口换到该面。
     *
     * <p>为什么是配置器：Mek 里只有 {@code ItemConfigurator} 带「配置这一面」的语义，
     * 其余物品没有可复用的模式状态。模式上排除 EMPTY（清空）、ROTATE（旋转方块）、
     * WRENCH（拆装工具）——这三种在 Mek 里各有别的含义，只有 {@code CONFIGURATE_*}
     * 是「配置」语义，所以换面只认这 5 个模式。</p>
     */
    /**
     * 是否处于「可配置面」的配置器模式：除 EMPTY / ROTATE / WRENCH 之外的任意模式。
     *
     * <p>实际换面逻辑在 {@code WrenchInteractions#onRightClickBlock}：潜行时方块交互会被
     * NeoForge 跳过，只有事件层能稳定拿到这次右键。</p>
     */
    public static boolean isFaceConfigMode(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemConfigurator)) {
            return false;
        }
        ConfiguratorMode mode = stack.getOrDefault(MekanismDataComponents.CONFIGURATOR_MODE.get(),
                ConfiguratorMode.CONFIGURATE_ITEMS);
        return mode.isConfigurating();
    }

    /**
     * 右键交互：打开仓室 GUI。没有物品的手动存取逻辑（GUI 已覆盖）。
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (!this.partType.hasGui()) {
            // 焦炉仓这类专属仓室没有 GUI，只靠漏斗/管道交互。
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) {
                serverPlayer.openMenu(
                        new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, p) -> new c_y.meknh_core.common.gui.menu.HatchMenu(id, inventory, part),
                                new ItemStack(this).getHoverName()),
                        buf -> {
                            buf.writeBlockPos(pos);
                            buf.writeVarInt(part.items() == null ? 0 : part.items().getSlots());
                        });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
