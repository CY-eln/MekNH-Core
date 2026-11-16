package c_y.meknh_core.common.recipe;

import c_y.meknh_core.api.machine.MachineItemHandler;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** 仅写入副本的累计输出预留，绝不修改实际库存。 */
public final class OutputReservation {

    private OutputReservation() {
    }

    public static MachineOutput snapshot(MachineItemHandler items, @Nullable FluidTank fluid,
                                         @Nullable IChemicalTank chemical) {
        return snapshot(items, fluid, chemical, false);
    }

    /** 多方块 MEK 仓室的流体/化学品共用一个逻辑槽。 */
    public static MachineOutput snapshotHatch(@Nullable MachineItemHandler items, @Nullable FluidTank fluid,
                                              @Nullable IChemicalTank chemical) {
        return snapshot(items, fluid, chemical, true);
    }

    private static MachineOutput snapshot(@Nullable MachineItemHandler items, @Nullable FluidTank fluid,
                                          @Nullable IChemicalTank chemical, boolean shared) {
        List<Integer> limits = new ArrayList<>();
        if (items != null) {
            for (int slot = items.inputSlots(); slot < items.getSlots(); slot++) {
                limits.add(items.getSlotLimit(slot));
            }
        }
        MachineItemHandler copy = new MachineItemHandler(List.of(), limits, () -> {});
        if (items != null) {
            for (int slot = 0; slot < limits.size(); slot++) {
                copy.setStackInSlot(slot, items.getStackInSlot(items.inputSlots() + slot).copy());
            }
        }
        FluidTank fluidCopy = fluid == null ? null : new FluidTank(fluid.getCapacity(), fluid::isFluidValid);
        if (fluidCopy != null) {
            fluidCopy.setFluid(fluid.getFluid().copy());
        }
        IChemicalTank chemicalCopy = chemical == null ? null : BasicChemicalTank.createModern(
                chemical.getCapacity(), stack -> true,
                stack -> chemical.isValid(stack), () -> {});
        if (chemicalCopy != null) {
            chemicalCopy.setStack(chemical.getStack().copy());
        }
        return new MachineOutput() {
            @Override
            public MachineOutput outputSnapshot() {
                return snapshot(copy, fluidCopy, chemicalCopy, shared);
            }

            @Override
            public int insertItem(ItemStack stack, boolean simulate) {
                return copy.insertOutput(stack, simulate);
            }

            @Override
            public long insertFluid(FluidStack stack, boolean simulate) {
                if (fluidCopy == null || (shared && chemicalCopy != null && chemicalCopy.getStored() > 0L)) {
                    return 0L;
                }
                return fluidCopy.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            }

            @Override
            public long insertChemical(ChemicalStack stack, boolean simulate) {
                if (chemicalCopy == null || (shared && fluidCopy != null && !fluidCopy.isEmpty())) {
                    return 0L;
                }
                // Preserve any source insertion restrictions, without executing against it.
                long allowed = stack.getAmount() - chemical.insert(stack, Action.SIMULATE, AutomationType.INTERNAL).getAmount();
                ChemicalStack request = stack.copyWithAmount(allowed);
                return allowed - chemicalCopy.insert(request, simulate ? Action.SIMULATE : Action.EXECUTE,
                        AutomationType.INTERNAL).getAmount();
            }
        };
    }

    /** 按真实输出仓顺序组合已独立复制的影子。 */
    public static MachineOutput combine(List<MachineOutput> snapshots) {
        List<MachineOutput> outputs = List.copyOf(snapshots);
        return new MachineOutput() {
            @Override
            public MachineOutput outputSnapshot() {
                return combine(outputs.stream().map(MachineOutput::outputSnapshot).toList());
            }

            @Override
            public int insertItem(ItemStack stack, boolean simulate) {
                int remaining = stack.getCount();
                for (MachineOutput output : outputs) {
                    if (remaining <= 0) break;
                    remaining -= output.insertItem(stack.copyWithCount(remaining), simulate);
                }
                return stack.getCount() - remaining;
            }

            @Override
            public long insertFluid(FluidStack stack, boolean simulate) {
                int remaining = stack.getAmount();
                for (MachineOutput output : outputs) {
                    if (remaining <= 0) break;
                    remaining -= (int) output.insertFluid(stack.copyWithAmount(remaining), simulate);
                }
                return stack.getAmount() - remaining;
            }

            @Override
            public long insertChemical(ChemicalStack stack, boolean simulate) {
                long remaining = stack.getAmount();
                for (MachineOutput output : outputs) {
                    if (remaining <= 0L) break;
                    remaining -= output.insertChemical(stack.copyWithAmount(remaining), simulate);
                }
                return stack.getAmount() - remaining;
            }
        };
    }
}
