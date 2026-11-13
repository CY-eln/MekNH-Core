package c_y.meknh_core.common.machine;

import mekanism.api.chemical.IChemicalHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 允许滴管访问的非物品罐。每个返回的 handler 都按单罐（tank 0）处理。
 */
public interface DropperTankHolder {

    @Nullable
    IChemicalHandler dropperChemicalHandler(int index);

    @Nullable
    IFluidHandler dropperFluidHandler(int index);
}
