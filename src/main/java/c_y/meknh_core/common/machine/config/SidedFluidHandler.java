package c_y.meknh_core.common.machine.config;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 按侧面配置限制插入/抽取方向的流体视图（与 {@link SidedChemicalHandler} 对称）。
 *
 * <p>NONE 面应该在注册能力时直接返回 {@code null}；这里只负责把 INPUT/OUTPUT/BOTH
 * 翻译成「能不能填 / 能不能抽」。</p>
 */
public final class SidedFluidHandler implements IFluidHandler {

    private final IFluidHandler delegate;
    private final boolean insert;
    private final boolean extract;

    public SidedFluidHandler(IFluidHandler delegate, boolean insert, boolean extract) {
        this.delegate = delegate;
        this.insert = insert;
        this.extract = extract;
    }

    @Override
    public int getTanks() {
        return this.delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.insert && this.delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return this.insert ? this.delegate.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return this.extract ? this.delegate.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return this.extract ? this.delegate.drain(maxDrain, action) : FluidStack.EMPTY;
    }
}
