package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.MekPipeBlock;
import c_y.meknh_core.common.pipe.MekPipeContents;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import mekanism.common.content.network.transmitter.Transmitter;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Mek 把相邻化学品容器视为合法 acceptor，否则 PULL/PUSH 会被回退、模型不更新。 */
@Mixin(Transmitter.class)
public abstract class TransmitterAcceptorMixin {

    @Inject(method = "getPossibleTransmitterConnection", at = @At("HEAD"), cancellable = true)
    private void meknh_core$contentMismatch(Direction side, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MechanicalPipe pipe) {
            var tile = pipe.getTransmitterTile();
            if (tile.getBlockState().getBlock() instanceof MekPipeBlock
                    && tile.getLevel() != null
                    && tile.getLevel().getBlockEntity(tile.getBlockPos().relative(side))
                            instanceof mekanism.common.tile.transmitter.TileEntityMechanicalPipe other
                    && other.getBlockState().getBlock() instanceof MekPipeBlock) {
                int a = MekPipeContents.ofNetwork(pipe);
                int b = MekPipeContents.ofNetwork(other.getTransmitter());
                if (a != MekPipeContents.EMPTY && b != MekPipeContents.EMPTY && a != b) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "getPossibleAcceptorConnection", at = @At("HEAD"), cancellable = true)
    private void meknh_core$chemicalAcceptor(Direction side, boolean markDirty,
                                             CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MechanicalPipe pipe) {
            var tile = pipe.getTransmitterTile();
            if (tile.getBlockState().getBlock() instanceof MekPipeBlock) {
                var level = tile.getLevel();
                if (level != null) {
                    var handler = level.getCapability(
                            mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                            tile.getBlockPos().relative(side), side.getOpposite());
                    int selfType = MekPipeContents.ofNetwork(pipe);
                    if (handler != null) {
                        // 网络是流体时，明确拒绝化学品容器；否则允许
                        cir.setReturnValue(selfType != MekPipeContents.FLUID);
                        return;
                    }
                    var fluid = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            tile.getBlockPos().relative(side), side.getOpposite());
                    if (fluid != null) {
                        // 网络是化学品时，明确拒绝流体容器
                        cir.setReturnValue(selfType != MekPipeContents.CHEMICAL);
                    }
                }
            }
        }
    }
}
