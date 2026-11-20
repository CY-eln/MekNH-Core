package c_y.meknh_core.common.pipe;

import mekanism.common.block.transmitter.BlockSmallTransmitter;
import mekanism.common.registries.MekanismBlockTypes;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** FE 电缆：直接继承 Mek 小型通用线缆方块，模型/连接/扳手配置全部走 Mek。 */
public class FeCableBlock extends BlockSmallTransmitter<TileEntityUniversalCable>
        implements FePipeDefinitionHolder {

    private final FePipeDefinition definition;

    public FeCableBlock(FePipeDefinition definition) {
        super(MekanismBlockTypes.BASIC_UNIVERSAL_CABLE);
        this.definition = definition;
    }

    @Override
    public FePipeDefinition meknh_core$feDefinition() {
        return this.definition;
    }

    @Override
    public TileEntityUniversalCable newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityUniversalCable(BuiltInRegistries.BLOCK.wrapAsHolder(this), pos, state);
    }
}
