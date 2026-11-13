package c_y.meknh_core.api.machine;

import java.util.List;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 把输入罐与输出罐合并成一个 {@link IFluidHandler}，用于注册方块实体能力。
 */
public final class CombinedFluidHandler implements IFluidHandler {

    private final List<FluidTank> tanks;

    public CombinedFluidHandler(FluidTank... tanks) {
        this.tanks = List.of(tanks);
    }

    public CombinedFluidHandler(List<FluidTank> tanks) {
        this.tanks = List.copyOf(tanks);
    }

    public List<FluidTank> tanks() {
        return this.tanks;
    }

    private FluidTank tank(int index) {
        if (index < 0 || index >= this.tanks.size()) {
            throw new IndexOutOfBoundsException("Fluid tank index " + index + " out of range " + this.tanks.size());
        }
        return this.tanks.get(index);
    }

    @Override
    public int getTanks() {
        return this.tanks.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.tank(tank).getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.tank(tank).getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.tank(tank).isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (int tank = 0; tank < this.tanks.size(); tank++) {
            FluidTank target = this.tanks.get(tank);
            if (!target.isFluidValid(resource)) {
                continue;
            }
            if (!target.isEmpty() && target.getFluid().getFluid() != resource.getFluid()) {
                continue;
            }
            int filled = target.fill(resource, action);
            if (filled > 0) {
                return filled;
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (FluidTank target : this.tanks) {
            if (target.isEmpty() || target.getFluid().getFluid() != resource.getFluid()) {
                continue;
            }
            FluidStack drained = target.drain(resource, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (FluidTank target : this.tanks) {
            if (target.isEmpty()) {
                continue;
            }
            FluidStack drained = target.drain(maxDrain, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }
}
