package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.FeCableAccess;
import c_y.meknh_core.common.pipe.FeCableRules;
import c_y.meknh_core.common.pipe.FeCableStorage;
import c_y.meknh_core.common.pipe.FePipeDefinition;
import c_y.meknh_core.common.pipe.FePipeDefinitionHolder;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Mek 通用线缆上注入本模组 FE 缓存与能量包规则。 */
@Mixin(TileEntityUniversalCable.class)
public abstract class UniversalCableMixin implements FeCableAccess {

    @Unique
    private FeCableStorage meknh_core$storage;

    @Override
    public FePipeDefinition meknh_core$feDefinition() {
        Block block = ((TileEntityUniversalCable) (Object) this).getBlockState().getBlock();
        return block instanceof FePipeDefinitionHolder holder ? holder.meknh_core$feDefinition() : null;
    }

    @Override
    public net.neoforged.neoforge.energy.IEnergyStorage meknh_core$feStorage() {
        if (this.meknh_core$storage == null) {
            FePipeDefinition definition = meknh_core$feDefinition();
            if (definition != null) {
                this.meknh_core$storage = new FeCableStorage(definition);
            }
        }
        return this.meknh_core$storage;
    }

    @Inject(method = "onUpdateServer", at = @At("TAIL"))
    private void meknh_core$tickRules(CallbackInfo ci) {
        TileEntityUniversalCable self = (TileEntityUniversalCable) (Object) this;
        if (self.getLevel() instanceof ServerLevel serverLevel && meknh_core$feDefinition() != null) {
            FeCableRules.tick(serverLevel, self.getBlockPos());
        }
    }
}
