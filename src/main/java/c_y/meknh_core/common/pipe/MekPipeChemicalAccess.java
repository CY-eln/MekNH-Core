package c_y.meknh_core.common.pipe;

import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** 由 Mixin 实现在 Mek 机械管道上，暴露同一“mek 槽位”的化学品视图。 */
public interface MekPipeChemicalAccess {

    @Nullable
    IChemicalHandler meknh_core$chemicalHandler(@Nullable Direction side);

    mekanism.api.chemical.IChemicalTank meknh_core$chemicalTank();
}
