package c_y.meknh_core.mixin.mek.fission;

import c_y.meknh_core.api.chemical.Chemical;
import java.util.function.Predicate;
import mekanism.api.chemical.ChemicalStack;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FissionReactorMultiblockData.class)
public abstract class FissionReactorFuelInitMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/capabilities/chemical/VariableCapacityChemicalTank;input(Lmekanism/common/lib/multiblock/MultiblockData;Ljava/util/function/LongSupplier;Ljava/util/function/Predicate;Lmekanism/api/chemical/attribute/ChemicalAttributeValidator;Lmekanism/api/IContentsListener;)Lmekanism/api/chemical/IChemicalTank;"
            ),
            index = 2
    )
    private static Predicate<ChemicalStack> modifyFuelPredicate(Predicate<ChemicalStack> original) {
        // 原版限定 FISSILE_FUEL，这里追加自定义燃料，二者皆允许输入
        return stack -> original.test(stack) || Chemical.is(stack, Chemical.MAGIC_ACTIVATED_FISSILE_FUEL);
    }
}
