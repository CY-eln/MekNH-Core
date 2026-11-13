package c_y.meknh_core.decor.supercritical_ice.mixin;

import c_y.meknh_core.api.chemical.Chemical;
import c_y.meknh_core.decor.supercritical_ice.DecorText;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(mekanism.api.chemical.Chemical.class)
public abstract class CoolantTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void MeknhCore$decorateCoolantTooltip(ChemicalStack stack, Item.TooltipContext context,
                                                 List<Component> tooltips, TooltipFlag flag, CallbackInfo ci) {
        if (Chemical.is(stack, Chemical.SUPERCRITICAL_CRYO_ICE_SOLUTION)) {
            tooltips.add(DecorText.coolantLine("chemical.meknh_core.decor.coolant.efficiency"));
            tooltips.add(DecorText.coolantLine("chemical.meknh_core.decor.coolant.enthalpy"));
            ci.cancel();
        }
    }
}
