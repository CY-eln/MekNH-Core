package c_y.meknh_core.api.machine;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;

/**
 * 把多个化学品罐（输入罐、输出罐、蒸汽罐）合并成一个 {@link IChemicalHandler}，
 * 方便一次性注册到方块实体能力上。
 */
public final class CombinedChemicalHandler implements IChemicalHandler {

    private final List<IChemicalTank> tanks;

    public CombinedChemicalHandler(IChemicalTank... tanks) {
        this.tanks = List.of(tanks);
    }

    public CombinedChemicalHandler(List<IChemicalTank> tanks) {
        this.tanks = List.copyOf(tanks);
    }

    public List<IChemicalTank> tanks() {
        return this.tanks;
    }

    private IChemicalTank tank(int index) {
        if (index < 0 || index >= this.tanks.size()) {
            throw new IndexOutOfBoundsException("Chemical tank index " + index + " out of range " + this.tanks.size());
        }
        return this.tanks.get(index);
    }

    @Override
    public int getChemicalTanks() {
        return this.tanks.size();
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return this.tank(tank).getStack();
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        this.tank(tank).setStack(stack);
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return this.tank(tank).getCapacity();
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return this.tank(tank).isValid(stack);
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return this.tank(tank).insert(stack, action, AutomationType.EXTERNAL);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        return this.tank(tank).extract(amount, action, AutomationType.EXTERNAL);
    }
}
