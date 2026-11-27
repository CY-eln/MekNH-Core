package c_y.meknh_core.api.machine;

import java.util.List;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 把输入罐与输出罐合并成一个 {@link IFluidHandler}，用于注册方块实体能力。
 *
 * <p><b>只有输出罐可以被抽取</b>：输入罐排在前面，{@code firstDrainable} 指向第一个输出罐，
 * {@link #drain} 只从这个下标往后找。这样管道 / 漏斗不可能把燃料、水这类输入抽走
 * （表现为「输入槽能被抽干、却没法自动弹出」）。</p>
 */
public final class CombinedFluidHandler implements IFluidHandler {

    private final List<FluidTank> tanks;
    private final int firstDrainable;

    public CombinedFluidHandler(FluidTank... tanks) {
        this.tanks = List.of(tanks);
        this.firstDrainable = 0;
    }

    /**
     * @param firstDrainable 第一个允许抽取的罐下标；等于输入罐数量
     * @param tanks          罐列表，顺序必须是「先输入、后输出」，与能力暴露的顺序一致
     */
    public CombinedFluidHandler(int firstDrainable, FluidTank... tanks) {
        this.tanks = List.of(tanks);
        this.firstDrainable = clamp(firstDrainable, this.tanks.size());
    }

    /**
     * 全部罐都可抽取（用于「这些罐本身就是输出」的场景，例如多方块输入聚合）。
     */
    public CombinedFluidHandler(List<FluidTank> tanks) {
        this(tanks, 0);
    }

    public CombinedFluidHandler(int firstDrainable, List<FluidTank> tanks) {
        this(tanks, firstDrainable);
    }

    public CombinedFluidHandler(List<FluidTank> tanks, int firstDrainable) {
        this.tanks = List.copyOf(tanks);
        this.firstDrainable = clamp(firstDrainable, this.tanks.size());
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size));
    }

    public List<FluidTank> tanks() {
        return this.tanks;
    }

    /**
     * 第一个允许抽取的罐下标；外部可用于 UI/调试。
     */
    public int firstDrainableTank() {
        return this.firstDrainable;
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
        for (int tank = this.firstDrainable; tank < this.tanks.size(); tank++) {
            FluidTank target = this.tanks.get(tank);
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
        for (int tank = this.firstDrainable; tank < this.tanks.size(); tank++) {
            FluidTank target = this.tanks.get(tank);
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
