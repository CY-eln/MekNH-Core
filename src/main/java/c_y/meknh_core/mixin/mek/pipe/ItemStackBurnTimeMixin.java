package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.fuel.FuelRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 data/addon/fuel/*.json 的物品燃料生效。 */
@Mixin(ItemStack.class)
public abstract class ItemStackBurnTimeMixin {

    @Inject(method = "getBurnTime", at = @At("RETURN"), cancellable = true)
    private void meknh_core$fuel(RecipeType<?> type, CallbackInfoReturnable<Integer> cir) {
        int burn = FuelRegistry.burnTime((ItemStack) (Object) this);
        if (burn > 0) {
            cir.setReturnValue(burn);
        }
    }
}
