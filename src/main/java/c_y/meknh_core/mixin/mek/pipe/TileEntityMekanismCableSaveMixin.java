package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.FeCableAccess;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FE 电缆缓存 NBT 持久化。 */
@Mixin(TileEntityMekanism.class)
public abstract class TileEntityMekanismCableSaveMixin {

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void meknh_core$saveFe(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof FeCableAccess access) {
            var storage = access.meknh_core$feStorage();
            if (storage != null && storage.getEnergyStored() > 0) {
                tag.putInt("MeknhFeEnergy", storage.getEnergyStored());
            }
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void meknh_core$loadFe(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof FeCableAccess access && tag.contains("MeknhFeEnergy")) {
            var storage = access.meknh_core$feStorage();
            if (storage instanceof c_y.meknh_core.common.pipe.FeCableStorage cableStorage) {
                cableStorage.forceSet(tag.getInt("MeknhFeEnergy"));
            }
        }
    }
}
