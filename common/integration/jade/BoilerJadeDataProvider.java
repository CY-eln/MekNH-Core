package c_y.meknh_core.common.integration.jade;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.generator.boiler.BoilerBlockEntity;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.integration.lookingat.jade.JadeLookingAtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 把锅炉的水、燃料、蒸汽和温度转换成 Mekanism Jade 渲染器认识的 {@code MEK_DATA}。
 *
 * <p>机器侧通过能力让 Mekanism 自带的 Jade provider 读取；锅炉不是
 * {@code TileEntityUpdateable}，所以这里用同样的 {@link JadeLookingAtHelper} +
 * {@link mekanism.common.integration.lookingat.jade.JadeTooltipRenderer} 数据格式桥接，
 * 客户端仍由 Mekanism 的 Jade 渲染器绘制。</p>
 */
public final class BoilerJadeDataProvider implements IServerDataProvider<BlockAccessor> {

    public static final BoilerJadeDataProvider INSTANCE = new BoilerJadeDataProvider();

    private static final ResourceLocation UID = MekNHCore.rl("boiler_data");

    private BoilerJadeDataProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BoilerBlockEntity boiler)) {
            return;
        }
        JadeLookingAtHelper helper = new JadeLookingAtHelper(accessor.getLevel().registryAccess());

        helper.addText(Component.translatable("gui.meknh_core.jade.water"));
        helper.addFluidElement(new FluidElement(boiler.waterFluidDisplay(), boiler.waterCapacity()));
        FluidStack fuelFluid = boiler.fuelFluidDisplay();
        ChemicalStack fuelChemical = boiler.fuelChemicalDisplay();
        if (!fuelFluid.isEmpty()) {
            helper.addText(Component.translatable("gui.meknh_core.jade.fuel"));
            helper.addFluidElement(new FluidElement(fuelFluid, boiler.fuelCapacity()));
        } else if (!fuelChemical.isEmpty()) {
            helper.addText(Component.translatable("gui.meknh_core.jade.fuel"));
            helper.addChemicalElement(new ChemicalElement(fuelChemical, boiler.fuelCapacity()));
        }
        helper.addText(Component.translatable("gui.meknh_core.jade.steam"));
        helper.addChemicalElement(new ChemicalElement(boiler.outputChemicalDisplay(), boiler.steamCapacity()));
        helper.addText(Component.translatable("gui.meknh_core.jade.temperature", boiler.temperature()));
        if (boiler.isBlocked()) {
            helper.addText(Component.translatable("gui.meknh_core.jade.blocked"));
        } else if (boiler.isActive()) {
            helper.addText(Component.translatable("gui.meknh_core.jade.active"));
        } else {
            helper.addText(Component.translatable("gui.meknh_core.jade.inactive"));
        }
        helper.finalizeData(data);
    }
}
