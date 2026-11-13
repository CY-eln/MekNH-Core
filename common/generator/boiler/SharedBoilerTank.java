package c_y.meknh_core.common.generator.boiler;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 液体锅炉的共用燃料罐：同一格只能存流体或化学品。
 */
public final class SharedBoilerTank implements IFluidHandler, IChemicalHandler {

    private final int capacity;
    private final Runnable changed;
    private FluidStack fluid = FluidStack.EMPTY;
    private ChemicalStack chemical = ChemicalStack.EMPTY;

    public SharedBoilerTank(int capacity, Runnable changed) {
        this.capacity = capacity;
        this.changed = changed;
    }

    public int capacity() {
        return this.capacity;
    }

    public FluidStack fluid() {
        return this.fluid;
    }

    public ChemicalStack chemical() {
        return this.chemical;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!this.fluid.isEmpty()) {
            tag.put("shared_fluid", this.fluid.save(registries));
        }
        if (!this.chemical.isEmpty()) {
            tag.put("shared_chemical", this.chemical.save(registries));
        }
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.fluid = tag.contains("shared_fluid") ? FluidStack.parseOptional(registries, tag.getCompound("shared_fluid")) : FluidStack.EMPTY;
        this.chemical = tag.contains("shared_chemical") ? ChemicalStack.parseOptional(registries, tag.getCompound("shared_chemical")) : ChemicalStack.EMPTY;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.fluid;
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.capacity;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.chemical.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !this.chemical.isEmpty()
                || (!this.fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(this.fluid, resource))) {
            return 0;
        }
        int filled = Math.min(resource.getAmount(), this.capacity - this.fluid.getAmount());
        if (filled <= 0) {
            return 0;
        }
        if (action.execute()) {
            this.fluid = resource.copyWithAmount(this.fluid.getAmount() + filled);
            this.changed.run();
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return !resource.isEmpty() && !this.fluid.isEmpty() && FluidStack.isSameFluidSameComponents(this.fluid, resource)
                ? this.drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (this.fluid.isEmpty() || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        int drained = Math.min(maxDrain, this.fluid.getAmount());
        FluidStack result = this.fluid.copyWithAmount(drained);
        if (action.execute()) {
            this.fluid = this.fluid.getAmount() == drained ? FluidStack.EMPTY
                    : this.fluid.copyWithAmount(this.fluid.getAmount() - drained);
            this.changed.run();
        }
        return result;
    }

    @Override
    public int getChemicalTanks() {
        return 1;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return this.chemical;
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        this.chemical = stack;
        this.changed.run();
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return this.capacity;
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return this.fluid.isEmpty();
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (stack.isEmpty() || !this.fluid.isEmpty()
                || (!this.chemical.isEmpty() && !ChemicalStack.isSameChemical(this.chemical, stack))) {
            return stack;
        }
        long inserted = Math.min(stack.getAmount(), (long)this.capacity - this.chemical.getAmount());
        if (inserted <= 0L) {
            return stack;
        }
        if (action.execute()) {
            this.chemical = stack.copyWithAmount(this.chemical.getAmount() + inserted);
            this.changed.run();
        }
        return stack.copyWithAmount(stack.getAmount() - inserted);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        if (this.chemical.isEmpty() || amount <= 0L) {
            return ChemicalStack.EMPTY;
        }
        long extracted = Math.min(amount, this.chemical.getAmount());
        ChemicalStack result = this.chemical.copyWithAmount(extracted);
        if (action.execute()) {
            this.chemical = this.chemical.getAmount() == extracted ? ChemicalStack.EMPTY
                    : this.chemical.copyWithAmount(this.chemical.getAmount() - extracted);
            this.changed.run();
        }
        return result;
    }
}
