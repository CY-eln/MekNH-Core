package c_y.meknh_core.mixin.mek.fission;

import c_y.meknh_core.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.registration.impl.DeferredChemical;
import mekanism.common.registries.MekanismChemicals;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FissionReactorMultiblockData.class)
public abstract class FissionReactorBurnMixin {

    @Shadow
    @Final
    private IChemicalTank fuelTank;

    @Redirect(
            method = "burnFuel",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/registration/impl/DeferredChemical;asStack(J)Lmekanism/api/chemical/ChemicalStack;"
            )
    )
    private ChemicalStack redirectWasteStack(DeferredChemical<mekanism.api.chemical.Chemical> chemical, long amount) {
        ChemicalStack fuel = this.fuelTank.getStack();
        if (!fuel.isEmpty() && Chemical.is(fuel, Chemical.MAGIC_ACTIVATED_FISSILE_FUEL)) {
            return MekanismChemicals.POLONIUM.asStack(amount);
        }
        return chemical.asStack(amount);
    }
}
