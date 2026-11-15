package c_y.meknh_core.common.casing;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import c_y.meknh_core.api.multiblock.MultiblockDefinition;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Existing primitive_pump ID and visuals with real structure checking, but no water production. */
public final class PrimitivePumpControllerBlock extends MultiblockControllerBlock {
    /** Fallback definition ID used when the explicit controller binding is unavailable. */
    public static final ResourceLocation STRUCTURE_ID = MekNHCore.rl("layout_mtesteamwaterpump");
    public static final ResourceLocation BLOCK_ID = MekNHCore.rl("primitive_pump");

    public PrimitivePumpControllerBlock(Properties properties) {
        super(properties, definitionRequired(), definitionRequired().levels().getFirst());
    }

    private static MultiblockDefinition definitionRequired() {
        MultiblockDefinition selected = MultiblockRegistration.controllerDefinition(BLOCK_ID);
        if (selected != null) {
            return selected;
        }
        // 绑定预检失败时仍允许方块注册：优先找水泵定义，再退回任意已加载定义。
        MultiblockDefinition fallback = MultiblockRegistration.definition(STRUCTURE_ID);
        if (fallback != null) {
            MekNHCore.LOGGER.warn("primitive_pump is not explicitly bound; falling back to {}", STRUCTURE_ID);
            return fallback;
        }
        return MultiblockRegistration.definitions().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "primitive_pump has no multiblock definition; add waterpump.json or remove the block"));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
