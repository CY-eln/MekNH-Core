package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.MekPipeChemicalAccess;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.transmitter.TileEntityMechanicalPipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 化学品“mek 槽位”的 NBT 持久化。 */
@Mixin(TileEntityMekanism.class)
public abstract class TileEntityMekanismChemSaveMixin {

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void meknh_core$saveChemical(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof TileEntityMechanicalPipe pipe
                && pipe instanceof MekPipeChemicalAccess access) {
            var tank = access.meknh_core$chemicalTank();
            if (tank != null && !tank.isEmpty()) {
                RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
                ChemicalStack.CODEC.encodeStart(ops, tank.getStack()).result()
                        .ifPresent(encoded -> tag.put("MeknhChem", encoded));
            }
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void meknh_core$loadChemical(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof TileEntityMechanicalPipe pipe
                && pipe instanceof MekPipeChemicalAccess access) {
            if (tag.contains("MeknhChem")) {
                RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
                ChemicalStack.CODEC.parse(ops, tag.get("MeknhChem")).result().ifPresent(stack -> {
                    var tank = access.meknh_core$chemicalTank();
                    if (tank != null) {
                        tank.setStack(stack);
                    }
                });
            }
        }
    }
}
