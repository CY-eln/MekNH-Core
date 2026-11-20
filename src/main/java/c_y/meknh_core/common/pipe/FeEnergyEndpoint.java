package c_y.meknh_core.common.pipe;

/** 本模组 FE 电缆可连接的“有规则”能量端点：机器 / 能源仓 / 动力仓。 */
public interface FeEnergyEndpoint {

    long feVoltage();

    boolean acceptsFePacket(long packetVoltage);

    int acceptFePacket(long packetVoltage, int fe);

    int extractFePacket(int maxFe);

    void overvoltageExplode();
}
