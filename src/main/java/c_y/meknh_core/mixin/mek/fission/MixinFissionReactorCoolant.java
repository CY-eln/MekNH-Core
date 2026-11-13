package c_y.meknh_core.mixin.mek.fission;

import c_y.meknh_core.api.chemical.Chemical;
import c_y.meknh_core.api.chemical.ChemicalRegistration;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.attribute.CooledCoolant;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.registries.MekanismChemicals;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FissionReactorMultiblockData.class)
public abstract class MixinFissionReactorCoolant {

    @Shadow
    @Final
    private MergedTank coolantTank;

    @Shadow
    @Final
    private IChemicalTank heatedCoolantTank;

    @Shadow
    @Final
    private VariableHeatCapacitor heatCapacitor;

    @Shadow
    private double lastBurnRate;

    @Shadow
    private long lastBoilRate;

    @Inject(method = "handleCoolant", at = @At("HEAD"), cancellable = true)
    private void injectHandleCoolant(CallbackInfo ci) {
        // 步骤 A：放行判断 —— 仅化学冷却液且为本冷却液时接管
        if (this.coolantTank.getCurrentType() != MergedTank.CurrentType.CHEMICAL) {
            return; // 水冷，交给原版
        }
        IChemicalTank chemTank = this.coolantTank.getChemicalTank();
        ChemicalStack coolant = chemTank.getStack();
        if (coolant.isEmpty() || !Chemical.is(coolant, Chemical.SUPERCRITICAL_CRYO_ICE_SOLUTION)) {
            return; // 其他冷却液（钠等），交给原版
        }

        // 步骤 B：计算与消耗（绝对 1:1）
        long fuelBurned = (long) this.lastBurnRate;
        long stored = chemTank.getStored();
        if (fuelBurned <= 0 || stored <= 0) {
            this.lastBoilRate = 0L;
            ci.cancel();
            return;
        }
        long actualConsume = Math.min(fuelBurned, stored);
        chemTank.shrinkStack(actualConsume, Action.EXECUTE);

        // 步骤 C：产出被加热的冷却剂（每 1 mB 冷却液 -> 9750 mB，产物取自化学品的 cooled_coolant.hot_variant）
        long steamToProduce = actualConsume * 9750L;
        this.heatedCoolantTank.insert(heatedOutput(steamToProduce),
                Action.EXECUTE, AutomationType.INTERNAL);
        this.lastBoilRate = actualConsume;

        // 步骤 D：不产热 —— 抵消 burnFuel 已加入的热量，确保温度不上升
        this.heatCapacitor.handleHeat(-(this.lastBurnRate * MekanismGeneratorsConfig.generators.energyPerFissionFuel.get()));

        // 取消原方法，避免原版冷却液逻辑再次执行
        ci.cancel();
    }

    private static ChemicalStack heatedOutput(long amount) {
        CooledCoolant coolant = ChemicalRegistration.getHolder(Chemical.SUPERCRITICAL_CRYO_ICE_SOLUTION)
                .map(holder -> holder.getData(IMekanismDataMapTypes.INSTANCE.cooledChemicalCoolant()))
                .orElse(null);
        return coolant != null ? coolant.heat(amount) : MekanismChemicals.STEAM.asStack(amount);
    }
}
