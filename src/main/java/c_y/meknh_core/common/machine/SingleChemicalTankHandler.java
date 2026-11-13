package c_y.meknh_core.common.machine;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;

/**
 * 把单个 IChemicalTank 包装成 IChemicalHandler（tank 0），供滴管/JEI 使用。
 */
public final class SingleChemicalTankHandler implements IChemicalHandler {

    private final IChemicalTank tank;

    public SingleChemicalTankHandler(IChemicalTank tank) {
        this.tank = tank;
    }

    @Override
    public int getChemicalTanks() {
        return 1;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return this.tank.getStack();
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        this.tank.setStack(stack);
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return this.tank.getCapacity();
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return this.tank.isValid(stack);
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return this.tank.insert(stack, action, AutomationType.MANUAL);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        return this.tank.extract(amount, action, AutomationType.MANUAL);
    }
}
