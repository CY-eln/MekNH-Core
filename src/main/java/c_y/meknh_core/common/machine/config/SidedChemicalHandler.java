package c_y.meknh_core.common.machine.config;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;

/**
 * 按侧面配置限制插入/抽取方向的化学品视图。
 */
public final class SidedChemicalHandler implements IChemicalHandler {

    private final IChemicalHandler delegate;
    private final boolean insert;
    private final boolean extract;

    public SidedChemicalHandler(IChemicalHandler delegate, boolean insert, boolean extract) {
        this.delegate = delegate;
        this.insert = insert;
        this.extract = extract;
    }

    @Override
    public int getChemicalTanks() {
        return this.delegate.getChemicalTanks();
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return this.delegate.getChemicalInTank(tank);
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        this.delegate.setChemicalInTank(tank, stack);
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return this.delegate.getChemicalTankCapacity(tank);
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return this.insert && this.delegate.isValid(tank, stack);
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return this.insert ? this.delegate.insertChemical(tank, stack, action) : stack;
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        return this.extract ? this.delegate.extractChemical(tank, amount, action) : ChemicalStack.EMPTY;
    }
}
