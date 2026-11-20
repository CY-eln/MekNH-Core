package c_y.meknh_core.common.integration.jade;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineBlockEntity;
import c_y.meknh_core.common.pipe.MekPipeBlock;
import c_y.meknh_core.common.pipe.MekPipeChemicalAccess;
import c_y.meknh_core.common.pipe.MekPipeDefinitionHolder;
import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import c_y.meknh_core.common.coke.CokeOvenControllerBlockEntity;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.integration.lookingat.EnergyElement;
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
        // 注意：本 provider 和 Mek 自带的 JadeDataProvider 都往 MEK_DATA 这一把 key 写，
        // 后写的会覆盖先写的。所以这里必须把能量也一起写进来，否则有储罐的机器
        // （压缩机等）会只剩空储罐、能量条消失。
        boolean added = addEnergy(helper, blockEntity);
        if (blockEntity instanceof MultiblockPartBlockEntity part) {
            added |= addPartTanks(helper, part);
        } else if (blockEntity instanceof MachineBlockEntity machine) {
            added |= addMachineTanks(helper, machine);
        } else if (blockEntity instanceof CokeOvenControllerBlockEntity oven) {
            added |= addFluid(helper, oven.creosoteTank());
        } else if (blockEntity instanceof mekanism.common.tile.transmitter.TileEntityMechanicalPipe pipe
                && pipe.getBlockState().getBlock() instanceof MekPipeBlock) {
            added |= addPipeSlot(helper, pipe);
        }
        if (!added) {
            return;
        }
        if (added) {
            helper.finalizeData(data);
        }
    }

    /**
     * 机器能量条：Mek 的 EnergyElement 用 J，这里按 1 FE = 2.5 J 换算。
     */
    private static boolean addEnergy(JadeLookingAtHelper helper, BlockEntity blockEntity) {
        if (blockEntity instanceof MachineBlockEntity machine && machine.energyStorage() != null) {
            long stored = Math.round(machine.energyStorage().getEnergyStored() * 2.5D);
            long capacity = Math.round(machine.energyStorage().getMaxEnergyStored() * 2.5D);
            helper.addEnergyElement(new EnergyElement(stored, capacity));
            return true;
        }
        return false;
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

    /** 管道只显示一个“mek 槽位”：有化学品显示化学品，否则显示流体（空也显示一个空槽）。 */
    private static boolean addPipeSlot(JadeLookingAtHelper helper,
                                       mekanism.common.tile.transmitter.TileEntityMechanicalPipe pipe) {
        long capacity = pipe.getBlockState().getBlock() instanceof MekPipeDefinitionHolder holder
                ? holder.meknh_core$definition().capacity() : 0L;
        var network = c_y.meknh_core.common.pipe.MekPipeChemicalNetworkHandler.of(pipe);
        if (network.getChemicalTanks() > 0 && network.stored() > 0L) {
            helper.addChemicalElement(new ChemicalElement(
                    network.getChemicalInTank(0).copyWithAmount(network.stored()), network.capacity()));
            return true;
        }
        FluidStack fluid = pipe.getTransmitter().getBufferWithFallback();
        helper.addFluidElement(new FluidElement(fluid, (int) capacity));
        return true;
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
