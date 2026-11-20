package c_y.meknh_core.common.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * FE 电缆方块实体：内部缓存 + 每 tick 触发一次网络传输。
 *
 * <p>缓存容量 = 电缆电压 × 电缆能量包个数（Amperage）。其他模组的导线/创造能源
 * 可以把 FE 插入这个缓存（走 {@link net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage}），
 * 缓存满了之后由网络按能量包规则继续往机器送。</p>
 */
public class FeCableBlockEntity extends BlockEntity {

    private final CableEnergyStorage energyStorage = new CableEnergyStorage();

    public FeCableBlockEntity(BlockPos pos, BlockState state) {
        super(FePipeRegistration.cableType(), pos, state);
    }

    public FePipeDefinition definition() {
        if (this.getBlockState().getBlock() instanceof FeCableBlock cable) {
            return cable.definition();
        }
        return null;
    }

    public EnergyStorage energyStorage() {
        return this.energyStorage;
    }

    /**
     * Mekanism 严格能量（J）视图：用于和 Mek 的创造能源/线缆互通（1 FE = 2.5 J）。
     */
    public mekanism.api.energy.IStrictEnergyHandler strictEnergyStorage() {
        return new mekanism.api.energy.IStrictEnergyHandler() {
            @Override
            public int getEnergyContainerCount() {
                return 1;
            }

            @Override
            public long getEnergy(int container) {
                return Math.round(FeCableBlockEntity.this.energyStorage.getEnergyStored() * 2.5D);
            }

            @Override
            public void setEnergy(int container, long energy) {
                FeCableBlockEntity.this.energyStorage.forceSet((int) Math.round(energy / 2.5D));
            }

            @Override
            public long getMaxEnergy(int container) {
                return Math.round(FeCableBlockEntity.this.bufferCapacity() * 2.5D);
            }

            @Override
            public long getNeededEnergy(int container) {
                return Math.max(0L, this.getMaxEnergy(container) - this.getEnergy(container));
            }

            @Override
            public long insertEnergy(int container, long amount, mekanism.api.Action action) {
                if (amount <= 0L) {
                    return 0L;
                }
                int fe = (int) Math.min(Integer.MAX_VALUE, Math.floor(amount / 2.5D));
                int accepted = FeCableBlockEntity.this.energyStorage.receiveEnergy(fe,
                        action.simulate());
                return amount - Math.round(accepted * 2.5D);
            }

            @Override
            public long extractEnergy(int container, long amount, mekanism.api.Action action) {
                if (amount <= 0L) {
                    return 0L;
                }
                int fe = (int) Math.min(Integer.MAX_VALUE, Math.ceil(amount / 2.5D));
                int extracted = FeCableBlockEntity.this.energyStorage.extractEnergy(fe,
                        action.simulate());
                return Math.round(extracted * 2.5D);
            }
        };
    }

    /**
     * 缓存容量：电压 × 能量包个数。
     */
    public int bufferCapacity() {
        FePipeDefinition definition = this.definition();
        if (definition == null) {
            return 0;
        }
        long capacity = definition.tier().voltage() * definition.amperage();
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    public static void tick(Level level, BlockPos pos, FeCableBlockEntity cable) {
        if (level.isClientSide) {
            return;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            FeCableNetwork.tick(serverLevel, pos);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FeBuffer", this.energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energyStorage.forceSet(tag.getInt("FeBuffer"));
    }

    /**
     * 电缆缓存：只允许外部插入（接收），不允许外部抽取。
     */
    private final class CableEnergyStorage extends EnergyStorage {

        private CableEnergyStorage() {
            // capacity 必须足够大：getMaxEnergyStored() 被重写为 bufferCapacity()，
            // 但 EnergyStorage.receiveEnergy 内部仍用构造参数 capacity 做上限。
            super(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        }

        @Override
        public int getMaxEnergyStored() {
            return FeCableBlockEntity.this.bufferCapacity();
        }

        @Override
        public boolean canExtract() {
            // 可进可出：其他模组也能从电缆缓存抽取。
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int space = this.getMaxEnergyStored() - this.getEnergyStored();
            if (space <= 0) {
                return 0;
            }
            return super.receiveEnergy(Math.min(maxReceive, space), simulate);
        }

        private void forceSet(int energy) {
            this.energy = Math.max(0, Math.min(energy, this.getMaxEnergyStored()));
        }
    }
}
