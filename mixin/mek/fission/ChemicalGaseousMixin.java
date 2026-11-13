package c_y.meknh_core.mixin.mek.fission;

import c_y.meknh_core.api.chemical.ChemicalRegistration;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 JSON 中声明为气态的化学品被 Mekanism 当作气态处理（等价于 {@code mekanism:gaseous} 标签）。
 */
@Mixin(Chemical.class)
public abstract class ChemicalGaseousMixin {

    @Inject(method = "isGaseous", at = @At("HEAD"), cancellable = true)
    private void meknh_core$isGaseous(CallbackInfoReturnable<Boolean> cir) {
        Chemical self = (Chemical) (Object) this;
        ResourceKey<Chemical> key = MekanismAPI.CHEMICAL_REGISTRY.getResourceKey(self).orElse(null);
        if (key != null && ChemicalRegistration.isGaseous(key.location())) {
            cir.setReturnValue(true);
        }
    }
}
