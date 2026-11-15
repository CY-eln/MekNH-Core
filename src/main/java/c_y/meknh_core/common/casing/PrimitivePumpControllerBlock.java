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
    /** Legacy fallback only; the constructor uses the final binding selected for BLOCK_ID. */
    public static final ResourceLocation STRUCTURE_ID = MekNHCore.rl("gtnh_layout_mtesteamwaterpump_main_133");
    public static final ResourceLocation BLOCK_ID = MekNHCore.rl("primitive_pump");

    public PrimitivePumpControllerBlock(Properties properties) {
        super(properties, definitionRequired(), definitionRequired().levels().getFirst());
    }

    private static MultiblockDefinition definitionRequired() {
        return java.util.Objects.requireNonNull(MultiblockRegistration.controllerDefinition(BLOCK_ID),
                "Missing selected controller definition for block: " + BLOCK_ID);
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
