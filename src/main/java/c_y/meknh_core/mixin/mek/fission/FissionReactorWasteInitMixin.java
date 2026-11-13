package c_y.meknh_core.mixin.mek.fission;

import java.util.function.Predicate;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registries.MekanismChemicals;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FissionReactorMultiblockData.class)
public abstract class FissionReactorWasteInitMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/capabilities/chemical/VariableCapacityChemicalTank;output(Lmekanism/common/lib/multiblock/MultiblockData;Ljava/util/function/LongSupplier;Ljava/util/function/Predicate;Lmekanism/api/chemical/attribute/ChemicalAttributeValidator;Lmekanism/api/IContentsListener;)Lmekanism/api/chemical/IChemicalTank;"
            ),
            index = 2
    )
    private static Predicate<ChemicalStack> modifyWastePredicate(Predicate<ChemicalStack> original) {
        // 原版限定 NUCLEAR_WASTE，这里追加 POLONIUM，使核废料与钋都能存入废料槽
        return stack -> original.test(stack) || stack.is(MekanismChemicals.POLONIUM);
    }
}
