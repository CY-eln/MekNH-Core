package c_y.meknh_core.common.pipe;

import net.neoforged.neoforge.energy.EnergyStorage;

/** FE 电缆内部缓存：容量 = 电压 × Amperage。 */
public class FeCableStorage extends EnergyStorage {

    private final FePipeDefinition definition;

    public FeCableStorage(FePipeDefinition definition) {
        super((int) Math.min(Integer.MAX_VALUE, Math.max(1L, definition.maxTransfer())),
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        this.definition = definition;
    }

    public void forceSet(int energy) {
        this.energy = Math.max(0, Math.min(energy, this.getMaxEnergyStored()));
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, this.definition.maxTransfer()));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int space = this.getMaxEnergyStored() - this.getEnergyStored();
        return space <= 0 ? 0 : super.receiveEnergy(Math.min(maxReceive, space), simulate);
    }
}
