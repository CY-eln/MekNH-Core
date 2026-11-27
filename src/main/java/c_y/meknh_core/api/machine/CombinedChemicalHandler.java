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
 *
 * <p><b>只有输出罐可以被抽取</b>：{@code firstExtractable} 之前的罐（蒸汽罐、气体输入罐）
 * {@link #extractChemical} 一律返回空。这样即使玩家把某个面配成 OUTPUT/BOTH，
 * 也不可能用管道把蒸汽或输入气体抽走。</p>
 */
public final class CombinedChemicalHandler implements IChemicalHandler {

    private final List<IChemicalTank> tanks;
    private final int firstExtractable;

    public CombinedChemicalHandler(IChemicalTank... tanks) {
        this.tanks = List.of(tanks);
        this.firstExtractable = 0;
    }

    /**
     * @param firstExtractable 第一个允许抽取的罐下标
     * @param tanks            罐列表，顺序必须是「先输入、后输出」，与能力暴露的顺序一致
     */
    public CombinedChemicalHandler(int firstExtractable, IChemicalTank... tanks) {
        this.tanks = List.of(tanks);
        this.firstExtractable = clamp(firstExtractable, this.tanks.size());
    }

    /**
     * 全部罐都可抽取（用于「这些罐本身就是输出」的场景，例如多方块输入聚合）。
     */
    public CombinedChemicalHandler(List<IChemicalTank> tanks) {
        this(tanks, 0);
    }

    public CombinedChemicalHandler(int firstExtractable, List<IChemicalTank> tanks) {
        this(tanks, firstExtractable);
    }

    public CombinedChemicalHandler(List<IChemicalTank> tanks, int firstExtractable) {
        this.tanks = List.copyOf(tanks);
        this.firstExtractable = clamp(firstExtractable, this.tanks.size());
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size));
    }

    public List<IChemicalTank> tanks() {
        return this.tanks;
    }

    /**
     * 第一个允许抽取的罐下标；外部可用于 UI/调试。
     */
    public int firstExtractableTank() {
        return this.firstExtractable;
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
        if (tank < this.firstExtractable) {
            // 输入罐 / 蒸汽罐只进不出
            return ChemicalStack.EMPTY;
        }
        return this.tank(tank).extract(amount, action, AutomationType.EXTERNAL);
    }
}
