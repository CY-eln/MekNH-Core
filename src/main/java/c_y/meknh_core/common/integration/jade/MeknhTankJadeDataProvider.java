package c_y.meknh_core.common.integration.jade;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import c_y.meknh_core.common.coke.CokeOvenControllerBlockEntity;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.integration.lookingat.jade.JadeLookingAtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 通用 Jade 数据桥：把本模组的流体/化学品罐转换成 Mekanism Jade 渲染器认识的 {@code MEK_DATA}。
 *
 * <p>之前只有锅炉注册了 provider，水泵仓以及普通机器里的罐体没有数据，所以 Jade 会显示为空。
 * 这里统一覆盖所有自带非物品罐的方块实体。</p>
 */
public final class MeknhTankJadeDataProvider implements IServerDataProvider<BlockAccessor> {

    public static final MeknhTankJadeDataProvider INSTANCE = new MeknhTankJadeDataProvider();

    private static final ResourceLocation UID = MekNHCore.rl("tank_data");

    private MeknhTankJadeDataProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        JadeLookingAtHelper helper = new JadeLookingAtHelper(accessor.getLevel().registryAccess());
        boolean added;
        if (blockEntity instanceof MultiblockPartBlockEntity part) {
            added = addPartTanks(helper, part);
        } else if (blockEntity instanceof MachineBlockEntity machine) {
            added = addMachineTanks(helper, machine);
        } else if (blockEntity instanceof CokeOvenControllerBlockEntity oven) {
            added = addFluid(helper, oven.creosoteTank());
        } else {
            return;
        }
        if (added) {
            helper.finalizeData(data);
        }
    }

    private static boolean addPartTanks(JadeLookingAtHelper helper, MultiblockPartBlockEntity part) {
        boolean added = false;
        FluidTank fluidTank = part.fluidTank();
        IChemicalTank chemicalTank = part.chemicalTank();
        if (fluidTank != null && !fluidTank.isEmpty()) {
            added |= addFluid(helper, fluidTank);
        }
        if (chemicalTank != null && chemicalTank.getStored() > 0L) {
            added |= addChemical(helper, chemicalTank);
        }
        // 空罐也提供一个容量条，避免 Jade 看起来什么都没有。
        if (!added && fluidTank != null && fluidTank.getCapacity() > 0) {
            added = addFluid(helper, fluidTank);
        }
        return added;
    }

    private static boolean addMachineTanks(JadeLookingAtHelper helper, MachineBlockEntity machine) {
        boolean added = false;
        if (machine.inputFluidTank() != null && !machine.inputFluidTank().isEmpty()) {
            added |= addFluid(helper, machine.inputFluidTank());
        }
        if (machine.outputFluidTank() != null && !machine.outputFluidTank().isEmpty()) {
            added |= addFluid(helper, machine.outputFluidTank());
        }
        if (machine.inputGasTank() != null && machine.inputGasTank().getStored() > 0L) {
            added |= addChemical(helper, machine.inputGasTank());
        }
        if (machine.outputGasTank() != null && machine.outputGasTank().getStored() > 0L) {
            added |= addChemical(helper, machine.outputGasTank());
        }
        if (machine.steamTank() != null && machine.steamTank().getStored() > 0L) {
            added |= addChemical(helper, machine.steamTank());
        }
        if (!added) {
            if (machine.inputFluidTank() != null) {
                added = addFluid(helper, machine.inputFluidTank());
            } else if (machine.inputGasTank() != null) {
                added = addChemical(helper, machine.inputGasTank());
            }
        }
        return added;
    }

    private static boolean addFluid(JadeLookingAtHelper helper, FluidTank tank) {
        if (tank == null || tank.getCapacity() <= 0) {
            return false;
        }
        FluidStack stack = tank.getFluid();
        helper.addFluidElement(new FluidElement(stack, tank.getCapacity()));
        return true;
    }

    private static boolean addChemical(JadeLookingAtHelper helper, IChemicalTank tank) {
        if (tank == null || tank.getCapacity() <= 0L) {
            return false;
        }
        ChemicalStack stack = tank.getStack();
        helper.addChemicalElement(new ChemicalElement(stack, tank.getCapacity()));
        return true;
    }
}
