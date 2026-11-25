package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.MekPipeBlock;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import mekanism.common.content.network.transmitter.Transmitter;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 对齐 Mek 原生行为：不再主动断开连接，只让化学品容器也能作为 acceptor。
 *
 * <p>流体的“不同种不合并”交给 Mek 自己的 CompatibleFluidTransmitterValidator；
 * 化学品不合并由我们的网络 handler 与插入校验负责。</p>
 */
@Mixin(Transmitter.class)
public abstract class TransmitterAcceptorMixin {

    @Inject(method = "getPossibleAcceptorConnection", at = @At("HEAD"), cancellable = true)
    private void meknh_core$chemicalAcceptor(Direction side, boolean markDirty,
                                             CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MechanicalPipe pipe) {
            var tile = pipe.getTransmitterTile();
            if (tile.getBlockState().getBlock() instanceof MekPipeBlock && tile.getLevel() != null) {
                var handler = tile.getLevel().getCapability(
                        mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                        tile.getBlockPos().relative(side), side.getOpposite());
                if (handler != null) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
