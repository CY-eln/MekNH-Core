package c_y.meknh_core.common.machine.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * 机器内部 FE 缓存（{@code 运行，超频与能耗.txt}）。
 *
 * <p>只允许输入、不允许抽取；容量按电压等级给出：
 * LV 2048 FE，每级乘 4（MV 8192、HV 32768、EV 131072、IV 524288）。</p>
 *
 * <p>输入不限速率，只受容量限制；{@link #extractEnergy} 永远返回 0。</p>
 */
public class MachineEnergyStorage extends EnergyStorage {

    private final Runnable onChanged;

    public MachineEnergyStorage(long capacity, Runnable onChanged) {
        super(checkedCapacity(capacity), Integer.MAX_VALUE, 0, 0);
        this.onChanged = onChanged;
    }

    private static int checkedCapacity(long capacity) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("Machine energy capacity must be greater than zero");
        }
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int received = super.receiveEnergy(toReceive, simulate);
        if (received > 0 && !simulate) {
            this.changed();
        }
        return received;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    /**
     * 直接扣除内部电量（机器运行时调用），返回是否扣除成功。
     */
    public boolean consume(long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (this.energy < amount) {
            return false;
        }
        this.energy -= (int) amount;
        this.changed();
        return true;
    }

    /**
     * 剩余空间是否足够再存入 {@code amount} FE。
     */
    public boolean hasSpaceFor(long amount) {
        return this.getMaxEnergyStored() - (long) this.energy >= amount;
    }

    /**
     * 直接写入电量（不经过 {@link #receiveEnergy} 的速率限制）。
     */
    public void setEnergyStored(long amount) {
        this.energy = (int) Math.max(0L, Math.min(this.getMaxEnergyStored(), amount));
        this.changed();
    }

    private void changed() {
        if (this.onChanged != null) {
            this.onChanged.run();
        }
    }
}
