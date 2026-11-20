package c_y.meknh_core.common.pipe;

import mekanism.common.block.transmitter.BlockLargeTransmitter;
import mekanism.common.registries.MekanismBlockTypes;
import mekanism.common.tile.transmitter.TileEntityMechanicalPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * “mek 槽位”管道：直接继承 Mek 的大型机械管道方块。
 *
 * <p>方块状态、连接形状、PULL/PUSH/NONE、扳手配置和模型加载器全部来自 Mek；
 * 容量/速率由 {@code MechanicalPipeMixin} 从 {@link MekPipeDefinition} 注入。</p>
 */
public class MekPipeBlock extends BlockLargeTransmitter<TileEntityMechanicalPipe>
        implements MekPipeDefinitionHolder {

    private final MekPipeDefinition definition;

    public MekPipeBlock(MekPipeDefinition definition) {
        super(MekanismBlockTypes.BASIC_MECHANICAL_PIPE);
        this.definition = definition;
    }

    @Override
    public MekPipeDefinition meknh_core$definition() {
        return this.definition;
    }

    @Override
    public TileEntityMechanicalPipe newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityMechanicalPipe(BuiltInRegistries.BLOCK.wrapAsHolder(this), pos, state);
    }
}
