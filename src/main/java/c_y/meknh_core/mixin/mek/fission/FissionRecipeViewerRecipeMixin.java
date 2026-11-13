package c_y.meknh_core.mixin.mek.fission;

import mekanism.generators.client.recipe_viewer.recipe.FissionRecipeViewerRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修正 Mekanism 裂变 JEI 配方在高热焓冷却剂下的崩溃：
 * {@code Math.round(energyPerFuel / thermalEnthalpy)} 可能得到 0，
 * 而 {@code ChemicalStackIngredient} 要求数量必须大于 0。
 */
@Mixin(FissionRecipeViewerRecipe.class)
public abstract class FissionRecipeViewerRecipeMixin {

    @Redirect(
            method = "getFissionRecipes",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(D)J")
    )
    private static long meknh_core$clampRecipeAmount(double value) {
        return Math.max(1L, Math.round(value));
    }
}
