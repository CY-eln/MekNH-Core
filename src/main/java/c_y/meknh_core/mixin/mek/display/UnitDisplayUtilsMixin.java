package c_y.meknh_core.mixin.mek.display;

import c_y.meknh_core.common.util.EnergyText;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 Mek 的能量短单位显示改成 1 位小数。
 *
 * <p>Mek 的 {@code getDisplayShort(double, EnergyUnit)} 内部固定
 * {@code decimalPlaces = 2}，而本模组规范是 1 位小数（{@link EnergyText}）。
 * 这里直接接管这个方法，Jade 的储能条、机器 GUI 的储能提示都会跟着统一。</p>
 *
 * <p>只改能量这一个入口，不动 {@code getDisplayBase}，避免影响温度/辐射等其它单位。</p>
 */
@Mixin(value = UnitDisplayUtils.class, remap = false)
public abstract class UnitDisplayUtilsMixin {

    @Inject(
            method = "getDisplayShort(DLmekanism/common/util/UnitDisplayUtils$EnergyUnit;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void meknh_core$shortEnergyOneDecimal(double value,
                                                         UnitDisplayUtils.EnergyUnit unit,
                                                         CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(EnergyText.formatUnit(value, unit));
    }
}
