package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.api.machine.CombinedChemicalHandler;
import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
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

    /**
     * 自动搬运的间隔（tick）。10 tick 一次，避免每 tick 反复扫邻居能力。
     */
    public static final int TRANSFER_INTERVAL = 10;

    private final MultiblockPartType partType;
    private final MachineLevel machineLevel;
    private final int amp;

    /**
     * 自动搬运开关：输出仓自动弹出、输入仓自动吸入，默认开启。
     */
    private boolean autoTransfer;

    private int transferTimer;

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
        this.autoTransfer = true;
        this.createHandlers();
    }

    private void createHandlers() {
        switch (this.partType) {
            case ITEM_INPUT -> {
                this.items = new MachineItemHandler(limits(itemSlots(this.machineLevel)), List.of(), this::setChanged);
                this.externalItemHandler = this.items.externalView();
            }
            case ITEM_OUTPUT -> {
                this.items = new MachineItemHandler(List.of(), limits(itemSlots(this.machineLevel)), this::setChanged);
                this.externalItemHandler = this.items.externalView();
            }
            case FLUID_INPUT -> {
                this.createMekSlot(this.machineLevel);
                this.items = new MachineItemHandler(limits(1), limits(1), this::setChanged);
                this.externalFluidHandler = new DirectionalFluidHandler(this.fluidTank, true, false);
                this.externalChemicalHandler = new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                        new CombinedChemicalHandler(this.chemicalTank), true, false);
            }
            case FLUID_OUTPUT -> {
                this.createMekSlot(this.machineLevel);
                this.items = new MachineItemHandler(limits(1), limits(1), this::setChanged);
                this.externalFluidHandler = new DirectionalFluidHandler(this.fluidTank, false, true);
                this.externalChemicalHandler = new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                        new CombinedChemicalHandler(this.chemicalTank), false, true);
            }
            case STEAM_INPUT -> {
                this.items = new MachineItemHandler(limits(1), limits(1), this::setChanged);
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
                this.items = new MachineItemHandler(limits(1), limits(1), this::setChanged);
                this.energyStorage = new HatchEnergyStorage(energyCapacity(this.machineLevel, this.amp), true, false, this::setChanged);
                this.strictEnergyHandler = this.energyStorage.strictHandler();
            }
            case ENERGY_OUTPUT -> {
                this.items = new MachineItemHandler(limits(1), limits(1), this::setChanged);
                this.energyStorage = new HatchEnergyStorage(energyCapacity(this.machineLevel, this.amp), false, true, this::setChanged);
                this.strictEnergyHandler = this.energyStorage.strictHandler();
            }
        }
    }

    /**
     * 物品总线的槽位数，对齐 GT：{@code (1 + min(9, tier))^2}。
     */
    public static int itemSlots(MachineLevel level) {
        int n = 1 + Math.min(9, level.voltage().level());
        return n * n;
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

    /**
     * 能源仓安培数；普通仓室为 2。
     */
    public int amp() {
        return this.amp;
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
    // 覆盖面 / 自动搬运
    // ------------------------------------------------------------------

    /**
     * 覆盖层所在面：输出仓是弹出口，输入仓是吸入口；没有该属性时退回北面。
     */
    public Direction ioFace() {
        BlockState state = this.getBlockState();
        return state.hasProperty(MultiblockPartBlock.FACING)
                ? state.getValue(MultiblockPartBlock.FACING)
                : Direction.NORTH;
    }

    /**
     * 自动搬运是否开启：输出仓自动弹出，输入仓自动吸入。
     */
    public boolean autoTransfer() {
        return this.autoTransfer;
    }

    /**
     * 切换自动搬运（GUI 的 tab 按钮调用）。
     */
    public void setAutoTransfer(boolean autoTransfer) {
        if (this.autoTransfer == autoTransfer) {
            return;
        }
        this.autoTransfer = autoTransfer;
        this.setChanged();
    }

    // ------------------------------------------------------------------
    // 手动交互（无 GUI）
    // ------------------------------------------------------------------

    /**
     * 服务端 tick：先处理自动搬运，再处理仓室 IO 槽里的容器/电池。
     */
    public void tick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        this.tickTransfer();
        if (this.items == null || this.items.getSlots() < 2) {
            return;
        }
        ItemStack input = this.items.getStackInSlot(0);
        if (input.isEmpty() || !this.items.getStackInSlot(1).isEmpty()) {
            return;
        }
        if (this.fluidTank != null) {
            this.handleFluidContainer(input);
        } else if (this.energyStorage != null) {
            this.handleEnergyItem(input);
        }
    }

    private void tickTransfer() {
        if (!this.autoTransfer) {
            return;
        }
        if (++this.transferTimer < TRANSFER_INTERVAL) {
            return;
        }
        this.transferTimer = 0;
        Direction face = this.ioFace();
        BlockPos target = this.worldPosition.relative(face);
        switch (this.partType) {
            case ITEM_INPUT -> this.pullItems(face, target);
            case ITEM_OUTPUT -> this.ejectItems(face, target);
            case FLUID_INPUT -> {
                this.pullFluid(face, target);
                this.pullChemical(face, target);
            }
            case FLUID_OUTPUT -> {
                this.ejectFluid(face, target);
                this.ejectChemical(face, target);
            }
            case STEAM_INPUT -> this.pullChemical(face, target);
            case ENERGY_INPUT -> this.pullEnergy(face, target);
            case ENERGY_OUTPUT -> this.ejectEnergy(face, target);
        }
    }

    // ------------------------------------------------------------------
    // 输入仓：自动吸入
    // ------------------------------------------------------------------

    private void pullItems(Direction face, BlockPos target) {
        if (this.items == null || this.level == null) {
            return;
        }
        IItemHandler source = this.level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                target, face.getOpposite());
        if (source == null) {
            return;
        }
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack available = source.extractItem(slot, ITEM_STACK_LIMIT, true);
            if (available.isEmpty()) {
                continue;
            }
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(this.items, available, true);
            int accepted = available.getCount() - leftover.getCount();
            if (accepted <= 0) {
                continue;
            }
            // 先按可接受量真实抽取，再把塞不下的还回源容器，避免复制/丢失。
            ItemStack taken = source.extractItem(slot, accepted, false);
            if (taken.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(this.items, taken, false);
            if (!rest.isEmpty()) {
                source.insertItem(slot, rest, false);
            }
            this.setChanged();
        }
    }

    private void pullFluid(Direction face, BlockPos target) {
        if (this.fluidTank == null || this.level == null) {
            return;
        }
        if (this.fluidTank.getFluidAmount() >= this.fluidTank.getCapacity()) {
            return;
        }
        IFluidHandler source = this.level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                target, face.getOpposite());
        if (source == null) {
            return;
        }
        FluidStack moved = FluidUtil.tryFluidTransfer(this.fluidTank, source,
                this.fluidTank.getCapacity() - this.fluidTank.getFluidAmount(), true);
        if (!moved.isEmpty()) {
            this.setChanged();
        }
    }

    private void pullChemical(Direction face, BlockPos target) {
        if (this.chemicalTank == null || this.level == null) {
            return;
        }
        long needed = this.chemicalTank.getNeeded();
        if (needed <= 0L) {
            return;
        }
        IChemicalHandler source = this.level.getCapability(
                mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                target, face.getOpposite());
        if (source == null) {
            return;
        }
        ChemicalStack available = source.extractChemical(needed, Action.SIMULATE);
        if (available.isEmpty()) {
            return;
        }
        ChemicalStack leftover = this.chemicalTank.insert(available, Action.SIMULATE, AutomationType.MANUAL);
        long accepted = available.getAmount() - leftover.getAmount();
        if (accepted <= 0L) {
            return;
        }
        ChemicalStack taken = source.extractChemical(accepted, Action.EXECUTE);
        if (taken.isEmpty()) {
            return;
        }
        this.chemicalTank.insert(taken, Action.EXECUTE, AutomationType.MANUAL);
        this.setChanged();
    }

    private void pullEnergy(Direction face, BlockPos target) {
        if (this.energyStorage == null || this.level == null) {
            return;
        }
        long neededFe = this.energyStorage.getMaxEnergyStored() - this.energyStorage.getEnergyStored();
        if (neededFe <= 0L) {
            return;
        }
        // Mek 线缆只认严格能量（J），优先走它；其余模组走 NeoForge FE。
        IStrictEnergyHandler strict = this.level.getCapability(
                mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(),
                target, face.getOpposite());
        if (strict != null) {
            long availableJ = strict.extractEnergy(Math.round(neededFe * 2.5D), Action.SIMULATE);
            if (availableJ <= 0L) {
                return;
            }
            int wouldAccept = this.energyStorage.receiveEnergy(
                    (int) Math.min(Integer.MAX_VALUE, Math.round(availableJ / 2.5D)), true);
            if (wouldAccept <= 0) {
                return;
            }
            long taken = strict.extractEnergy(Math.round(wouldAccept * 2.5D), Action.EXECUTE);
            if (taken > 0L) {
                this.energyStorage.receiveEnergy(
                        (int) Math.min(Integer.MAX_VALUE, Math.round(taken / 2.5D)), false);
                this.setChanged();
            }
            return;
        }
        IEnergyStorage source = this.level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                target, face.getOpposite());
        if (source == null) {
            return;
        }
        int available = source.extractEnergy((int) Math.min(Integer.MAX_VALUE, neededFe), true);
        if (available <= 0) {
            return;
        }
        int taken = source.extractEnergy(available, false);
        if (taken > 0) {
            this.energyStorage.receiveEnergy(taken, false);
            this.setChanged();
        }
    }

    // ------------------------------------------------------------------
    // 输出仓：自动弹出
    // ------------------------------------------------------------------

    private void ejectItems(Direction face, BlockPos target) {
        if (this.items == null || this.level == null) {
            return;
        }
        IItemHandler destination = this.level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                target, face.getOpposite());
        if (destination == null) {
            return;
        }
        for (int slot = this.items.inputSlots(); slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, stack.copy(), false);
            if (remainder.getCount() == stack.getCount()) {
                continue;
            }
            this.items.setStackInSlot(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            this.setChanged();
        }
    }

    private void ejectFluid(Direction face, BlockPos target) {
        if (this.fluidTank == null || this.level == null || this.fluidTank.isEmpty()) {
            return;
        }
        IFluidHandler destination = this.level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                target, face.getOpposite());
        if (destination == null) {
            return;
        }
        FluidStack moved = FluidUtil.tryFluidTransfer(destination, this.fluidTank,
                this.fluidTank.getFluidAmount(), true);
        if (!moved.isEmpty()) {
            this.setChanged();
        }
    }

    private void ejectChemical(Direction face, BlockPos target) {
        if (this.chemicalTank == null || this.level == null || this.chemicalTank.isEmpty()) {
            return;
        }
        IChemicalHandler destination = this.level.getCapability(
                mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                target, face.getOpposite());
        if (destination == null) {
            return;
        }
        ChemicalStack stack = this.chemicalTank.getStack();
        ChemicalStack remainder = destination.insertChemical(stack, Action.SIMULATE);
        long moved = stack.getAmount() - remainder.getAmount();
        if (moved <= 0L) {
            return;
        }
        ChemicalStack drained = this.chemicalTank.extract(moved, Action.EXECUTE, AutomationType.MANUAL);
        if (drained.isEmpty()) {
            return;
        }
        destination.insertChemical(drained, Action.EXECUTE);
        this.setChanged();
    }

    private void ejectEnergy(Direction face, BlockPos target) {
        if (this.energyStorage == null || this.level == null) {
            return;
        }
        long stored = this.energyStorage.getEnergyStored();
        if (stored <= 0L) {
            return;
        }
        // Mek 线缆只认严格能量（J），优先走它；其余模组走 NeoForge FE。
        IStrictEnergyHandler strict = this.level.getCapability(
                mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(),
                target, face.getOpposite());
        if (strict != null) {
            long offered = Math.round(stored * 2.5D);
            long remainder = strict.insertEnergy(offered, Action.EXECUTE);
            long moved = offered - remainder;
            if (moved > 0L) {
                this.energyStorage.extractEnergy(
                        (int) Math.min(Integer.MAX_VALUE, Math.round(moved / 2.5D)), false);
                this.setChanged();
            }
            return;
        }
        IEnergyStorage destination = this.level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                target, face.getOpposite());
        if (destination == null) {
            return;
        }
        int accepted = destination.receiveEnergy((int) Math.min(Integer.MAX_VALUE, stored), false);
        if (accepted > 0) {
            this.energyStorage.extractEnergy(accepted, false);
            this.setChanged();
        }
    }

    private void handleFluidContainer(ItemStack input) {
        boolean output = this.partType == MultiblockPartType.FLUID_OUTPUT;
        var result = output
                ? FluidUtil.tryFillContainer(input, this.fluidTank, Integer.MAX_VALUE, null, true)
                : FluidUtil.tryEmptyContainer(input, this.fluidTank, Integer.MAX_VALUE, null, true);
        if (!result.isSuccess()) {
            result = output
                    ? FluidUtil.tryEmptyContainer(input, this.fluidTank, Integer.MAX_VALUE, null, true)
                    : FluidUtil.tryFillContainer(input, this.fluidTank, Integer.MAX_VALUE, null, true);
        }
        if (!result.isSuccess()) {
            return;
        }
        ItemStack remaining = input.copy();
        remaining.shrink(1);
        this.items.setStackInSlot(0, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        this.items.setStackInSlot(1, result.getResult());
        this.setChanged();
    }

    private void handleEnergyItem(ItemStack input) {
        net.neoforged.neoforge.energy.IEnergyStorage storage = input.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (storage == null) {
            return;
        }
        if (this.partType == MultiblockPartType.ENERGY_INPUT) {
            int available = storage.extractEnergy(Integer.MAX_VALUE, true);
            int accepted = this.energyStorage.receiveEnergy(available, false);
            if (accepted > 0) {
                storage.extractEnergy(accepted, false);
                this.setChanged();
            }
        } else {
            int wanted = storage.receiveEnergy(Integer.MAX_VALUE, true);
            int drained = this.energyStorage.extractEnergy(wanted, false);
            if (drained > 0) {
                storage.receiveEnergy(drained, false);
                this.setChanged();
            }
        }
    }

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
        tag.putBoolean("AutoTransfer", this.autoTransfer);
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
        // AutoEject 是旧存档键，保留兼容。
        if (tag.contains("AutoTransfer")) {
            this.autoTransfer = tag.getBoolean("AutoTransfer");
        } else if (tag.contains("AutoEject")) {
            this.autoTransfer = tag.getBoolean("AutoEject");
        }
        this.resizeItems();
    }

    /**
     * 旧存档里的物品槽数量可能与当前槽位公式不同（例如总线从固定 9 格改为按等级）。
     * 读档后按当前槽位数量重建，避免服务端/客户端容器槽位不一致。
     */
    private void resizeItems() {
        int expected = switch (this.partType) {
            case ITEM_INPUT, ITEM_OUTPUT -> itemSlots(this.machineLevel);
            case FLUID_INPUT, FLUID_OUTPUT, STEAM_INPUT, ENERGY_INPUT, ENERGY_OUTPUT -> 2;
        };
        if (this.items == null || this.items.getSlots() == expected) {
            return;
        }
        MachineItemHandler fresh = switch (this.partType) {
            case ITEM_INPUT -> new MachineItemHandler(limits(expected), List.of(), this::setChanged);
            case ITEM_OUTPUT -> new MachineItemHandler(List.of(), limits(expected), this::setChanged);
            default -> new MachineItemHandler(limits(1), limits(1), this::setChanged);
        };
        int copy = Math.min(this.items.getSlots(), fresh.getSlots());
        for (int slot = 0; slot < copy; slot++) {
            fresh.setStackInSlot(slot, this.items.getStackInSlot(slot));
        }
        this.items = fresh;
        if (this.partType == MultiblockPartType.ITEM_INPUT || this.partType == MultiblockPartType.ITEM_OUTPUT) {
            this.externalItemHandler = fresh.externalView();
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