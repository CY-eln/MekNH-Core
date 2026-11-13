package c_y.meknh_core.common.machine.config;

/**
 * 机器侧面配置的传输类型。每个类型拥有独立的六面 NONE/INPUT/OUTPUT/BOTH 配置。
 */
public enum MachineConfigType {
    ITEM,
    FLUID,
    CHEMICAL,
    ENERGY;

    public static final MachineConfigType[] VALUES = values();
}
