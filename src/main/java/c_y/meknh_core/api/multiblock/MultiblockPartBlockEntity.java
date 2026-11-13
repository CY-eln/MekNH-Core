package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.api.machine.CombinedChemicalHandler;
import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 内核自带的标准仓室方块实体。
 *
 * <p>按 {@link MultiblockPartType} 只创建一种容器：</p>
 * <ul>
 *     <li>物品仓：{@link MachineItemHandler}，9 格，输入仓只进、输出仓只出；</li>
 *     <li>流体仓：MEK 逻辑槽，一个槽位同时暴露流体与化学品能力，但同一时间只存一种；</li>
 *     <li>能量仓：FE 缓存，输入仓只收、输出仓只放。</li>
 * </ul>
 *
 * <p>控制器直接读取本对象的原始容器；对外暴露的能力则按输入/输出方向收窄。
 * 因此管道只能按仓室的方向工作，而控制器总能拿到完整的读写权限。</p>
 */
public class MultiblockPartBlockEntity extends BlockEntity implements MultiblockPart {

    /**
     * 物品仓的槽位数。
     */
    public static final int ITEM_SLOTS = 9;

    /**
     * 物品仓每个槽位的堆叠上限。
     */
    public static final int ITEM_STACK_LIMIT = 64;

    /**
     * 流体/化学品共用槽位（MEK 逻辑槽）的对外名称。
     */
    public static final String MEK_SLOT = "MEK";

    private static final long FLUID_CAPACITY_BASE = 8_000L;
    private static final long ENERGY_CAPACITY_MULTIPLIER = 16L;

    private final MultiblockPartType partType;
    private final MachineLevel machineLevel;
    private final int amp;

    @Nullable
    private BlockPos controllerPos;

    @Nullable
    private MachineItemHandler items;
    @Nullable
    private FluidTank fluidTank;
    @Nullable
    private IChemicalTank chemicalTank;
    @Nullable
    private HatchEnergyStorage energyStorage;
    @Nullable
    private IItemHandler externalItemHandler;
    @Nullable
    private IFluidHandler externalFluidHandler;
    @Nullable
    private IChemicalHandler externalChemicalHandler;
    @Nullable
    private IStrictEnergyHandler strictEnergyHandler;

    public MultiblockPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                     MultiblockPartType partType, MachineLevel level) {
        this(type, pos, state, partType, level, 2);
    }

    public MultiblockPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                     MultiblockPartType partType, MachineLevel level, int amp) {
        super(type, pos, state);
        this.partType = partType;
        this.machineLevel = level;
        this.amp = amp;
        this.createHandlers();
    }

    private void createHandlers() {
        switch (this.partType) {
            case ITEM_INPUT -> {
                this.items = new MachineItemHandler(limits(ITEM_SLOTS), List.of(), this::setChanged);
                this.externalItemHandler = this.items.externalView();
            }
            case ITEM_OUTPUT -> {
                this.items = new MachineItemHandler(List.of(), limits(ITEM_SLOTS), this::setChanged);
                this.externalItemHandler = this.items.externalView();
            }
            case FLUID_INPUT -> {
                this.createMekSlot(this.machineLevel);
                this.externalFluidHandler = new DirectionalFluidHandler(this.fluidTank, true, false);
                this.externalChemicalHandler = new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                        new CombinedChemicalHandler(this.chemicalTank), true, false);
            }
            case FLUID_OUTPUT -> {
                this.createMekSlot(this.machineLevel);
                this.externalFluidHandler = new DirectionalFluidHandler(this.fluidTank, false, true);
                this.externalChemicalHandler = new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                        new CombinedChemicalHandler(this.chemicalTank), false, true);
            }
            case STEAM_INPUT -> {
                // 蒸汽仓只接受 mekanism:steam；控制器用内部 shrinkStack 扣除，
                // 对外能力仍然只允许插入。
                this.chemicalTank = BasicChemicalTank.createModern(
                        this.machineLevel.steamCapacity(),
                        stack -> stack.is(mekanism.common.registries.MekanismChemicals.STEAM),
                        stack -> stack.is(mekanism.common.registries.MekanismChemicals.STEAM),
                        this::setChanged);
                this.externalChemicalHandler = new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                        new CombinedChemicalHandler(this.chemicalTank), true, false);
            }
            case ENERGY_INPUT -> {
                this.energyStorage = new HatchEnergyStorage(energyCapacity(this.machineLevel, this.amp), true, false, this::setChanged);
                this.strictEnergyHandler = this.energyStorage.strictHandler();
            }
            case ENERGY_OUTPUT -> {
                this.energyStorage = new HatchEnergyStorage(energyCapacity(this.machineLevel, this.amp), false, true, this::setChanged);
                this.strictEnergyHandler = this.energyStorage.strictHandler();
            }
        }
    }

    private static List<Integer> limits(int slots) {
        List<Integer> limits = new java.util.ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            limits.add(ITEM_STACK_LIMIT);
        }
        return List.copyOf(limits);
    }

    /**
     * 流体仓容量：LV 8 B，此后每级 ×4。
     */
    public static long fluidCapacity(MachineLevel level) {
        int tier = Math.max(0, level.voltage().level() - 1);
        return FLUID_CAPACITY_BASE << (2 * tier);
    }

    /**
     * 能量仓容量：单方块机器缓存 ×16。
     */
    public static long energyCapacity(MachineLevel level) {
        return energyCapacity(level, 2);
    }

    /**
     * 能源仓容量：2A 时 ULV 256 FE，每级 ×4；4A 为 2A 的 2 倍，16A 为 8 倍。
     */
    public static long energyCapacity(MachineLevel level, int amp) {
        long base = 256L << (2 * level.voltage().level());
        return base * Math.max(1, amp) / 2L;
    }

    /**
     * MEK 逻辑槽：一个槽位同时提供流体与化学品能力，但同一时间只存一种。
     *
     * <p>两种罐都是满容量，插入时检查另一种是否为空；抽空后即可切换类型。
     * 控制器把两个罐都当作输入/输出源，因此配方写流体或化学品都能匹配。</p>
     */
    private void createMekSlot(MachineLevel level) {
        int capacity = (int) Math.min(Integer.MAX_VALUE, fluidCapacity(level));
        this.fluidTank = new FluidTank(capacity) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return MultiblockPartBlockEntity.this.chemicalTank == null
                        || MultiblockPartBlockEntity.this.chemicalTank.getStored() <= 0L;
            }
        };
        this.chemicalTank = BasicChemicalTank.createModern(
                capacity,
                stack -> true,
                stack -> MultiblockPartBlockEntity.this.fluidTank == null
                        || MultiblockPartBlockEntity.this.fluidTank.isEmpty(),
                this::setChanged);
    }

    // ------------------------------------------------------------------
    // 仓室契约
    // ------------------------------------------------------------------

    @Override
    public MultiblockPartType partType() {
        return this.partType;
    }

    public MachineLevel level() {
        return this.machineLevel;
    }

    @Nullable
    @Override
    public BlockPos controllerPos() {
        return this.controllerPos;
    }

    @Override
    public void linkController(BlockPos controllerPos) {
        this.controllerPos = controllerPos.immutable();
        this.setChanged();
    }

    @Override
    public void unlinkController(BlockPos controllerPos) {
        if (controllerPos.equals(this.controllerPos)) {
            this.controllerPos = null;
            this.setChanged();
        }
    }

    @Override
    public BlockEntity self() {
        return this;
    }

    // ------------------------------------------------------------------
    // 控制器访问的原始容器
    // ------------------------------------------------------------------

    @Nullable
    public MachineItemHandler items() {
        return this.items;
    }

    @Nullable
    public FluidTank fluidTank() {
        return this.fluidTank;
    }

    @Nullable
    public IChemicalTank chemicalTank() {
        return this.chemicalTank;
    }

    @Nullable
    public HatchEnergyStorage energyStorage() {
        return this.energyStorage;
    }

    // ------------------------------------------------------------------
    // 对外能力（方向收窄）
    // ------------------------------------------------------------------

    @Nullable
    public IItemHandler externalItemHandler() {
        return this.externalItemHandler;
    }

    @Nullable
    public IFluidHandler externalFluidHandler() {
        return this.externalFluidHandler;
    }

    @Nullable
    public IChemicalHandler externalChemicalHandler() {
        return this.externalChemicalHandler;
    }

    @Nullable
    public IStrictEnergyHandler strictEnergyHandler() {
        return this.strictEnergyHandler;
    }

    // ------------------------------------------------------------------
    // 手动交互（无 GUI）
    // ------------------------------------------------------------------

    /**
     * 右键仓室时的手动交互。
     *
     * <ul>
     *     <li>物品输入仓：把主手物品塞进仓室；</li>
     *     <li>物品输出仓：空手把第一格产物拿到手上；</li>
     *     <li>流体仓：用桶/容器交互；</li>
     *     <li>化学品与能量仓：不支持手动交互。</li>
     * </ul>
     *
     * @return 是否消耗了这次右键
     */
    public boolean usePart(Player player) {
        return switch (this.partType) {
            case ITEM_INPUT -> this.insertHeldItem(player);
            case ITEM_OUTPUT -> this.takeOutputItem(player);
            case FLUID_INPUT, FLUID_OUTPUT -> this.externalFluidHandler != null
                    && FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, this.externalFluidHandler);
            default -> false;
        };
    }

    private boolean insertHeldItem(Player player) {
        if (this.items == null) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        int before = held.getCount();
        for (int slot = 0; slot < this.items.getSlots() && !held.isEmpty(); slot++) {
            held = this.items.insertItem(slot, held, false);
        }
        if (held.getCount() == before) {
            return false;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        this.setChanged();
        return true;
    }

    private boolean takeOutputItem(Player player) {
        if (this.items == null || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack extracted = this.items.extractItem(slot, stack.getCount(), false);
            if (!extracted.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                this.setChanged();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 生命周期与存档
    // ------------------------------------------------------------------

    @Override
    public void setRemoved() {
        super.setRemoved();
        Level world = this.getLevel();
        if (this.controllerPos != null && world != null && !world.isClientSide
                && world.getBlockEntity(this.controllerPos) instanceof MultiblockControllerBlockEntity controller) {
            controller.invalidateStructure();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.controllerPos != null) {
            tag.putLong("Controller", this.controllerPos.asLong());
        }
        if (this.items != null) {
            tag.put("Items", this.items.serializeNBT(registries));
        }
        if (this.fluidTank != null) {
            tag.put("Fluid", this.fluidTank.writeToNBT(registries, new CompoundTag()));
        }
        if (this.chemicalTank != null) {
            tag.put("Chemical", this.chemicalTank.serializeNBT(registries));
        }
        if (this.energyStorage != null) {
            tag.putInt("Energy", this.energyStorage.getEnergyStored());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        if (this.items != null && tag.contains("Items")) {
            this.items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        if (this.fluidTank != null && tag.contains("Fluid")) {
            this.fluidTank.readFromNBT(registries, tag.getCompound("Fluid"));
        }
        if (this.chemicalTank != null && tag.contains("Chemical")) {
            this.chemicalTank.deserializeNBT(registries, tag.getCompound("Chemical"));
        }
        if (this.energyStorage != null && tag.contains("Energy")) {
            this.energyStorage.setStored(tag.getInt("Energy"));
        }
    }

    // ------------------------------------------------------------------
    // 辅助类型
    // ------------------------------------------------------------------

    /**
     * 按输入/输出方向收窄的流体视图。
     */
    private record DirectionalFluidHandler(IFluidHandler delegate, boolean insert, boolean extract)
            implements IFluidHandler {

        @Override
        public int getTanks() {
            return this.delegate.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.delegate.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.delegate.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.insert && this.delegate.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return this.insert ? this.delegate.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return this.extract ? this.delegate.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return this.extract ? this.delegate.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    /**
     * 仓室 FE 缓存：输入仓只收、输出仓只放。
     */
    public static final class HatchEnergyStorage extends EnergyStorage {

        private final boolean receive;
        private final boolean extract;
        private final Runnable onChanged;

        public HatchEnergyStorage(long capacity, boolean receive, boolean extract, Runnable onChanged) {
            super((int) Math.min(Integer.MAX_VALUE, Math.max(1L, capacity)), Integer.MAX_VALUE,
                    Integer.MAX_VALUE, 0);
            this.receive = receive;
            this.extract = extract;
            this.onChanged = onChanged;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!this.receive) {
                return 0;
            }
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) {
                this.changed();
            }
            return received;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (!this.extract) {
                return 0;
            }
            int extracted = super.extractEnergy(toExtract, simulate);
            if (extracted > 0 && !simulate) {
                this.changed();
            }
            return extracted;
        }

        @Override
        public boolean canExtract() {
            return this.extract;
        }

        @Override
        public boolean canReceive() {
            return this.receive;
        }

        /**
         * 控制器运行时直接扣除。
         */
        public boolean consume(long amount) {
            if (amount <= 0L) {
                return true;
            }
            int stored = this.getEnergyStored();
            if (stored < amount) {
                return false;
            }
            this.energy = (int) (stored - amount);
            this.changed();
            return true;
        }

        /**
         * 直接写入（读档用）。
         */
        public void setStored(long amount) {
            this.energy = (int) Math.max(0L, Math.min(this.getMaxEnergyStored(), amount));
        }

        private void changed() {
            if (this.onChanged != null) {
                this.onChanged.run();
            }
        }

        /**
         * Mekanism 线缆使用的严格能量视图（J = FE × 2.5）。
         */
        public IStrictEnergyHandler strictHandler() {
            HatchEnergyStorage storage = this;
            return new IStrictEnergyHandler() {

                @Override
                public int getEnergyContainerCount() {
                    return 1;
                }

                @Override
                public long getEnergy(int container) {
                    return Math.round(storage.getEnergyStored() * 2.5D);
                }

                @Override
                public void setEnergy(int container, long energy) {
                    storage.setStored(Math.round(energy / 2.5D));
                }

                @Override
                public long getMaxEnergy(int container) {
                    return Math.round(storage.getMaxEnergyStored() * 2.5D);
                }

                @Override
                public long getNeededEnergy(int container) {
                    return Math.max(0L, this.getMaxEnergy(container) - this.getEnergy(container));
                }

                @Override
                public long insertEnergy(int container, long amount, Action action) {
                    if (!storage.receive || amount <= 0L) {
                        return amount;
                    }
                    int accepted = storage.receiveEnergy(
                            (int) Math.min(Integer.MAX_VALUE, Math.round(amount / 2.5D)), action.simulate());
                    return amount - Math.round(accepted * 2.5D);
                }

                @Override
                public long extractEnergy(int container, long amount, Action action) {
                    if (!storage.extract || amount <= 0L) {
                        return 0L;
                    }
                    int extracted = storage.extractEnergy(
                            (int) Math.min(Integer.MAX_VALUE, Math.round(amount / 2.5D)), action.simulate());
                    return Math.round(extracted * 2.5D);
                }
            };
        }
    }
}