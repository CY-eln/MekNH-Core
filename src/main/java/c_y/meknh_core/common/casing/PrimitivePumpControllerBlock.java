package c_y.meknh_core.common.casing;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import c_y.meknh_core.api.multiblock.MultiblockDefinition;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Existing primitive_pump ID and visuals with real structure checking, but no water production. */
public final class PrimitivePumpControllerBlock extends MultiblockControllerBlock {
    /** Fallback definition ID used when the explicit controller binding is unavailable. */
    public static final ResourceLocation STRUCTURE_ID = MekNHCore.rl("primitive_pump");
    public static final ResourceLocation BLOCK_ID = MekNHCore.rl("primitive_pump");

    public PrimitivePumpControllerBlock(Properties properties) {
        super(properties, definitionRequired(), definitionRequired().levels().getFirst());
    }

    private static MultiblockDefinition definitionRequired() {
        // 只有绑定预检选中 primitive_pump 时 CasingRegistration 才会构造这个类。
        return java.util.Objects.requireNonNull(MultiblockRegistration.controllerDefinition(BLOCK_ID),
                "Missing selected controller definition for block: " + BLOCK_ID);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        // 原始水泵没有 GUI：成型后用「空手潜行右键」循环安全等级（唯一入口）。
        // 未成型时保留这个手势给多方块预览，与其它控制器一致。
        if (!level.isClientSide && state.getValue(FORMED)
                && c_y.meknh_core.common.security.SecurityInteractions.tryCycle(level, pos, player)) {
            return InteractionResult.CONSUME;
        }
        // 其余情况仍然不产生右键事件。
        return InteractionResult.PASS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
