package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.MekPipeDefinition;
import c_y.meknh_core.common.pipe.MekPipeDefinitionHolder;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collections;

/**
 * 让 Mek 机械管道的容量/抽取速率读取我们 JSON 里的值。
 *
 * <p>只对实现了 {@link MekPipeDefinitionHolder} 的方块生效，Mek 原版管道不受影响。</p>
 */
@Mixin(MechanicalPipe.class)
public abstract class MechanicalPipeMixin {

    @Inject(method = "getFluidTanks", at = @At("HEAD"), cancellable = true)
    private void meknh_core$fluidExclusion(net.minecraft.core.Direction side,
                                           CallbackInfoReturnable<java.util.List<mekanism.api.fluid.IExtendedFluidTank>> cir) {
        MechanicalPipe self = (MechanicalPipe) (Object) this;
        if (c_y.meknh_core.common.pipe.MekPipeContents.ofNetwork(self)
                == c_y.meknh_core.common.pipe.MekPipeContents.CHEMICAL) {
            cir.setReturnValue(Collections.emptyList());
        }
    }

    @Inject(method = "getCapacity", at = @At("RETURN"), cancellable = true)
    private void meknh_core$capacity(CallbackInfoReturnable<Long> cir) {
        MekPipeDefinition definition = meknh_core$definition();
        if (definition != null) {
            cir.setReturnValue((long) definition.capacity());
        }
    }

    @Inject(method = "getAvailablePull", at = @At("RETURN"), cancellable = true)
    private void meknh_core$pull(CallbackInfoReturnable<Integer> cir) {
        MekPipeDefinition definition = meknh_core$definition();
        if (definition != null) {
            MechanicalPipe self = (MechanicalPipe) (Object) this;
            cir.setReturnValue(Math.min(definition.transfer(), self.buffer.getNeeded()));
        }
    }

    private MekPipeDefinition meknh_core$definition() {
        MechanicalPipe self = (MechanicalPipe) (Object) this;
        Block block = self.getTransmitterTile().getBlockState().getBlock();
        return block instanceof MekPipeDefinitionHolder holder ? holder.meknh_core$definition() : null;
    }
}
