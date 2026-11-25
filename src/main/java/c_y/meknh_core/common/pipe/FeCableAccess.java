package c_y.meknh_core.common.pipe;

/** 通过 Mixin 实现在 Mek 通用线缆方块实体上，暴露我们的 FE 缓存。 */
public interface FeCableAccess {

    net.neoforged.neoforge.energy.IEnergyStorage meknh_core$feStorage();

    FePipeDefinition meknh_core$feDefinition();
}
