package c_y.meknh_core.common.generator.boiler;

import c_y.meknh_core.api.chemical.ChemicalDefinition;
import c_y.meknh_core.api.chemical.FluidAttributeRegistry;
import c_y.meknh_core.api.generator.boiler.BoilerDefinition;
import c_y.meknh_core.api.generator.boiler.BoilerTier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class BoilerBlockEntity extends BlockEntity implements IFluidHandler, IChemicalHandler, IHeatHandler,
        c_y.meknh_core.common.machine.DropperTankHolder {

    private final BoilerDefinition definition;
    private final BoilerTier tier;
    private final ItemStackHandler inventory;
    private final SharedBoilerTank sharedInput;
    private final IChemicalTank steamOutput;
    private final FluidTank waterTank = new FluidTank(10000, stack -> stack.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            BoilerBlockEntity.this.contentsChanged();
        }
    };
    private final BasicHeatCapacitor heatCapacitor = BasicHeatCapacitor.create(
            100, 5, 10, () -> HeatAPI.AMBIENT_TEMP, this::setChanged);    private int burnTime;
    private int burnDuration;
    private int heatTime;
    private int temperature = 20;
    private int heatingTicks;
    private int coolingTicks;
    private int steamTicks;
    private int ejectTicks;
    private boolean blocked;
    private final c_y.meknh_core.common.machine.config.SideMode[][] sideModes =
            new c_y.meknh_core.common.machine.config.SideMode[4][net.minecraft.core.Direction.values().length];
    private final boolean[] autoEject = new boolean[4];
    private final int[] inputColors = new int[net.minecraft.core.Direction.values().length];
    private boolean strictInput;
    private int outputColor;
    private c_y.meknh_core.common.machine.config.RedstoneMode redstoneMode =
            c_y.meknh_core.common.machine.config.RedstoneMode.DISABLED;
    private boolean privateMode;
    private java.util.UUID owner;

    public BoilerBlockEntity(BlockEntityType<BoilerBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        BoilerBlock block = (BoilerBlock)state.getBlock();
        this.definition = block.definition();
        this.tier = block.tier();
        this.inventory = new ItemStackHandler(Math.max(1, definition.itemInputSlots())) {
            @Override
            protected void onContentsChanged(int slot) {
                BoilerBlockEntity.this.setChanged();
            }
        };
        this.sharedInput = definition.fluidInputSlots() <= 0 && definition.chemicalInputSlots() <= 0
                ? null : new SharedBoilerTank(10000, this::contentsChanged);
        this.steamOutput = BasicChemicalTank.output(definition.steamCapacity(), this::contentsChanged);
        for (c_y.meknh_core.common.machine.config.SideMode[] modes : this.sideModes) {
            java.util.Arrays.fill(modes, c_y.meknh_core.common.machine.config.SideMode.NONE);
        }
        // 锅炉默认：水/燃料输入，蒸汽输出；蒸汽没有能量缓存。
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            int index = side.ordinal();
            this.sideModes[c_y.meknh_core.common.machine.config.MachineConfigType.ITEM.ordinal()][index]
                    = c_y.meknh_core.common.machine.config.SideMode.INPUT;
            this.sideModes[c_y.meknh_core.common.machine.config.MachineConfigType.FLUID.ordinal()][index]
                    = c_y.meknh_core.common.machine.config.SideMode.INPUT;
            this.sideModes[c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL.ordinal()][index]
                    = c_y.meknh_core.common.machine.config.SideMode.OUTPUT;
        }
        java.util.Arrays.fill(this.autoEject, false);
    }

    public void serverTick() {
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return;
        }
        // 先尝试自动弹出，避免输出满时把自己永久堵死。
        if (++this.ejectTicks >= 5) {
            this.ejectTicks = 0;
            if (this.autoEject[c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL.ordinal()]) {
                this.ejectSteamToNeighbors();
            }
        }
        this.blocked = this.steamOutput.getCapacity() > 0L
                && this.steamOutput.getStored() >= this.steamOutput.getCapacity();
        if (this.blocked) {
            this.setActive(false);
            this.setChanged();
            return;
        }
        int maxTemperature = this.maxTemperature();
        boolean powered = level.hasNeighborSignal(this.worldPosition);
        if ((this.redstoneMode == c_y.meknh_core.common.machine.config.RedstoneMode.HIGH && !powered)
                || (this.redstoneMode == c_y.meknh_core.common.machine.config.RedstoneMode.LOW && powered)) {
            this.setActive(false);
            this.setChanged();
            return;
        }
        boolean active = false;
        switch (this.definition.type()) {
            case SOLID -> {
                if (this.burnTime > 0) {
                    this.burnTime--;
                    active = true;
                } else if (this.consumeFuel()) {
                    active = true;
                }
            }
            case LIQUID -> {
                if (this.burnTime > 0) {
                    this.burnTime--;
                    active = true;
                } else if (this.consumeLiquid()) {
                    active = true;
                }
            }
            case SOLAR -> active = level.isDay() && level.canSeeSky(this.worldPosition.above());
        }

        // GT 加热：低压每 24 tick +1°C，高压每 12 tick +1°C。
        if (active) {
            this.heatingTicks++;
            int interval = this.tier == BoilerTier.HIGH ? 12 : 24;
            if (this.heatingTicks >= interval) {
                this.heatingTicks = 0;
                this.temperature = Math.min(maxTemperature, this.temperature + 1);
            }
            this.coolingTicks = 0;
        } else if (this.coolingTicks > 0) {
            this.coolingTicks--;
        } else if (this.temperature > 20) {
            this.temperature = Math.max(20, this.temperature - 1);
            this.coolingTicks = this.tier == BoilerTier.HIGH ? 40 : 45;
        }

        // GT：温度达到 100°C 后才产蒸汽；每 10 tick 消耗 1 mB 水并产出。
        if (++this.steamTicks >= 10) {
            this.steamTicks = 0;
            if (this.temperature >= 100 && this.waterTank.getFluidAmount() >= 1) {
                this.waterTank.drain(1, FluidAction.EXECUTE);
                int perTick = this.definition.steamPerTick()
                        * (this.tier == BoilerTier.HIGH ? this.definition.highPressureMultiplier() : 1);
                long amount = Math.max(1L, Math.round(perTick * (this.temperature / (double)maxTemperature) * 10.0D));
                long accepted = this.steamOutput.getCapacity() - this.steamOutput.getStored();
                if (accepted > 0L) {
                    this.steamOutput.insert(MekanismChemicals.STEAM.asStack(Math.min(amount, accepted)),
                            Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
        this.setActive(active);
        this.heatCapacitor.setHeat((this.temperature + 273.15D) * this.heatCapacitor.getHeatCapacity());
        this.setChanged();
    }

    /**
     * 发电机自动弹出：仅对侧面配置为 OUTPUT/BOTH 的面输出蒸汽。
     */
    private void ejectSteamToNeighbors() {
        if (this.level == null || this.steamOutput.isEmpty()) {
            return;
        }
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            if (!this.sideMode(c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL, side).allowsExtract()) {
                continue;
            }
            net.minecraft.core.BlockPos targetPos = this.worldPosition.relative(side);
            mekanism.api.chemical.IChemicalHandler target = this.level.getCapability(
                    mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                    targetPos, side.getOpposite());
            if (target == null) {
                continue;
            }
            long amount = Math.min(this.steamOutput.getStored(), 1000L);
            ChemicalStack drained = this.steamOutput.extract(amount, Action.EXECUTE, AutomationType.EXTERNAL);
            if (drained.isEmpty()) {
                continue;
            }
            ChemicalStack remaining = target.insertChemical(drained, Action.EXECUTE);
            if (!remaining.isEmpty()) {
                this.steamOutput.insert(remaining, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    /**
     * 与 GT 小型锅炉一致：低压上限 500°C，高压上限 1000°C。
     */
    public int maxTemperature() {
        return this.tier == BoilerTier.HIGH
                ? Math.min(1000, this.definition.temperatureMax())
                : Math.min(500, this.definition.temperatureMax());
    }

    private boolean consumeFuel() {
        ItemStack stack = this.inventory.getStackInSlot(0);
        if (stack.isEmpty()) {
            return false;
        }
        int burn = stack.getBurnTime(RecipeType.SMELTING);
        if (burn <= 0) {
            return false;
        }
        this.inventory.extractItem(0, 1, false);
        this.burnTime = burn;
        this.burnDuration = burn;
        this.setChanged();
        return true;
    }

    private boolean consumeLiquid() {
        if (this.sharedInput == null) {
            return false;
        }
        int burn;
        if (!this.sharedInput.fluid().isEmpty()) {
            FluidStack fluid = this.sharedInput.fluid();
            ChemicalDefinition definition = FluidAttributeRegistry.get(fluid);
            if (definition == null || definition.fuel() == null) {
                return false;
            }
            burn = definition.fuel().burnTime();
            if (this.sharedInput.drain(1, FluidAction.EXECUTE).isEmpty()) {
                return false;
            }
        } else {
            ChemicalStack chemical = this.sharedInput.chemical();
            if (chemical.isEmpty()) {
                return false;
            }
            ChemicalFuel fuel = chemical.getData(IMekanismDataMapTypes.INSTANCE.chemicalFuel());
            if (fuel == null) {
                return false;
            }
            burn = fuel.burnTicks();
            if (this.sharedInput.extractChemical(1L, Action.EXECUTE).isEmpty()) {
                return false;
            }
        }
        this.burnTime = Math.max(1, burn);
        this.burnDuration = this.burnTime;
        this.setChanged();
        return true;
    }

    private boolean consumeWater(int amount) {
        if (this.waterTank.getFluidAmount() < amount) {
            return false;
        }
        this.waterTank.drain(amount, FluidAction.EXECUTE);
        return true;
    }

    public boolean isActive() {
        BlockState state = this.getBlockState();
        return state.hasProperty(BoilerBlock.ACTIVE) && state.getValue(BoilerBlock.ACTIVE);
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    private void setActive(boolean active) {
        BlockState state = this.getBlockState();
        if (state.hasProperty(BoilerBlock.ACTIVE) && state.getValue(BoilerBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition, state.setValue(BoilerBlock.ACTIVE, active), 3);
        }
    }

    public IItemHandler itemHandler() {
        return this.definition.itemInputSlots() > 0 ? this.inventory : null;
    }

    public ItemStackHandler inventory() {
        return this.inventory;
    }

    public int waterAmount() {
        return this.waterTank.getFluidAmount();
    }

    public int waterCapacity() {
        return this.waterTank.getCapacity();
    }

    public int steamAmount() {
        return (int)Math.min(Integer.MAX_VALUE, this.steamOutput.getStored());
    }

    public int steamCapacity() {
        return (int)Math.min(Integer.MAX_VALUE, this.steamOutput.getCapacity());
    }

    public int fuelAmount() {
        if (this.sharedInput == null) {
            return 0;
        }
        return (int)Math.min(Integer.MAX_VALUE,
                (long)this.sharedInput.fluid().getAmount() + this.sharedInput.chemical().getAmount());
    }

    public int fuelCapacity() {
        return this.sharedInput == null ? 0 : this.sharedInput.capacity();
    }

    public int heatTime() {
        return this.heatTime;
    }

    public int temperature() {
        return this.temperature;
    }

    public FluidStack inputFluidDisplay() {
        return !this.waterTank.getFluid().isEmpty() ? this.waterTank.getFluid()
                : (this.sharedInput == null ? FluidStack.EMPTY : this.sharedInput.fluid());
    }

    public FluidStack waterFluidDisplay() {
        return this.waterTank.getFluid();
    }

    public FluidStack fuelFluidDisplay() {
        return this.sharedInput == null ? FluidStack.EMPTY : this.sharedInput.fluid();
    }

    public ChemicalStack fuelChemicalDisplay() {
        return this.sharedInput == null ? ChemicalStack.EMPTY : this.sharedInput.chemical();
    }

    public ChemicalStack inputChemicalDisplay() {
        return this.sharedInput == null ? ChemicalStack.EMPTY : this.sharedInput.chemical();
    }

    public ChemicalStack outputChemicalDisplay() {
        return this.steamOutput.getStack();
    }

    private void contentsChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public int burnTime() {
        return this.burnTime;
    }

    public int burnDuration() {
        return this.burnDuration;
    }

    public int temperatureMax() {
        return this.maxTemperature();
    }

    public int currentSteamPerTick() {
        if (this.temperature < 100) {
            return 0;
        }
        int max = Math.max(1, this.maxTemperature());
        return (int)Math.round(this.nominalSteamPerTick() * (this.temperature / (double)max));
    }

    public int nominalSteamPerTick() {
        return this.definition.steamPerTick()
                * (this.tier == BoilerTier.HIGH ? this.definition.highPressureMultiplier() : 1);
    }

    public c_y.meknh_core.common.machine.config.SideMode sideMode(
            c_y.meknh_core.common.machine.config.MachineConfigType type,
            net.minecraft.core.Direction side) {
        return this.sideModes[type.ordinal()][side.ordinal()];
    }

    public void cycleSideMode(c_y.meknh_core.common.machine.config.MachineConfigType type,
                              net.minecraft.core.Direction side, boolean reverse) {
        int index = side.ordinal();
        this.sideModes[type.ordinal()][index] = this.allowedNextMode(type,
                this.sideModes[type.ordinal()][index], reverse);
        this.setChanged();
        this.invalidateSides();
    }

    /**
     * 锅炉是发电机：化学品（蒸汽）只允许 NONE/OUTPUT；没有能量缓存，能量通道只允许 NONE。
     */
    public boolean allowsSideMode(c_y.meknh_core.common.machine.config.MachineConfigType type,
                                  c_y.meknh_core.common.machine.config.SideMode mode) {
        if (type == c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL) {
            return mode == c_y.meknh_core.common.machine.config.SideMode.NONE
                    || mode == c_y.meknh_core.common.machine.config.SideMode.OUTPUT;
        }
        if (type == c_y.meknh_core.common.machine.config.MachineConfigType.FLUID
                || type == c_y.meknh_core.common.machine.config.MachineConfigType.ITEM) {
            // 锅炉的流体/物品槽都是输入槽，不允许输出或双向。
            return mode == c_y.meknh_core.common.machine.config.SideMode.NONE
                    || mode == c_y.meknh_core.common.machine.config.SideMode.INPUT;
        }
        if (type == c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY) {
            return mode == c_y.meknh_core.common.machine.config.SideMode.NONE;
        }
        return true;
    }

    public boolean canEjectSideType(c_y.meknh_core.common.machine.config.MachineConfigType type) {
        return type == c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL;
    }

    private c_y.meknh_core.common.machine.config.SideMode allowedNextMode(
            c_y.meknh_core.common.machine.config.MachineConfigType type,
            c_y.meknh_core.common.machine.config.SideMode current, boolean reverse) {
        c_y.meknh_core.common.machine.config.SideMode candidate = current;
        for (int i = 0; i < 4; i++) {
            candidate = reverse ? candidate.previous() : candidate.next();
            if (this.allowsSideMode(type, candidate)) {
                return candidate;
            }
        }
        return c_y.meknh_core.common.machine.config.SideMode.NONE;
    }

    public void clearSideModes(c_y.meknh_core.common.machine.config.MachineConfigType type) {
        java.util.Arrays.fill(this.sideModes[type.ordinal()],
                c_y.meknh_core.common.machine.config.SideMode.NONE);
        this.setChanged();
        this.invalidateSides();
    }

    /**
     * 与机器侧配置的“复制面配置”一致：统一模式取下一个/上一个，混合模式清空。
     */
    public void batchCycleSideModes(c_y.meknh_core.common.machine.config.MachineConfigType type, boolean reverse) {
        c_y.meknh_core.common.machine.config.SideMode common = null;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            c_y.meknh_core.common.machine.config.SideMode mode = this.sideModes[type.ordinal()][side.ordinal()];
            if (common == null) {
                common = mode;
            } else if (common != mode) {
                common = null;
                break;
            }
        }
        c_y.meknh_core.common.machine.config.SideMode target = common == null
                || !this.allowsSideMode(type, common)
                ? c_y.meknh_core.common.machine.config.SideMode.NONE
                : this.allowedNextMode(type, common, reverse);
        java.util.Arrays.fill(this.sideModes[type.ordinal()], target);
        this.setChanged();
        this.invalidateSides();
    }

    public void clearAllSideModes() {
        for (c_y.meknh_core.common.machine.config.SideMode[] modes : this.sideModes) {
            java.util.Arrays.fill(modes, c_y.meknh_core.common.machine.config.SideMode.NONE);
        }
        this.setChanged();
        this.invalidateSides();
    }

    private void invalidateSides() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.invalidateCapabilities(this.worldPosition);
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
    }

    public boolean autoEject(c_y.meknh_core.common.machine.config.MachineConfigType type) {
        return this.autoEject[type.ordinal()];
    }

    public void toggleAutoEject(c_y.meknh_core.common.machine.config.MachineConfigType type) {
        if (!this.canEjectSideType(type)) {
            return;
        }
        this.autoEject[type.ordinal()] = !this.autoEject[type.ordinal()];
        this.setChanged();
    }

    public boolean strictInput() {
        return this.strictInput;
    }

    public void toggleStrictInput() {
        this.strictInput = !this.strictInput;
        this.setChanged();
    }

    public int outputColor() {
        return this.outputColor;
    }

    public int inputColor(net.minecraft.core.Direction side) {
        return this.inputColors[side.ordinal()];
    }

    public void cycleOutputColor() {
        this.outputColor = (this.outputColor + 1) % 16;
        this.setChanged();
    }

    public void cycleInputColor(net.minecraft.core.Direction side) {
        int index = side.ordinal();
        this.inputColors[index] = (this.inputColors[index] + 1) % 16;
        this.setChanged();
    }

    public c_y.meknh_core.common.machine.config.RedstoneMode redstoneMode() {
        return this.redstoneMode;
    }

    /**
     * 锅炉警告位：1=缺水，2=缺燃料，8=红石条件不满足。
     */
    public int warningCode() {
        int code = 0;
        if (this.waterTank.getFluidAmount() < 1) {
            code |= 1;
        }
        if (this.tier != null && this.definition.type() == c_y.meknh_core.api.generator.boiler.BoilerDefinition.Type.SOLID) {
            if (this.burnTime <= 0 && this.inventory.getStackInSlot(0).isEmpty()) {
                code |= 2;
            }
        } else if (this.sharedInput != null
                && this.sharedInput.fluid().isEmpty() && this.sharedInput.chemical().isEmpty()) {
            code |= 2;
        }
        if (this.redstoneBlocked()) {
            code |= 8;
        }
        if (this.blocked) {
            code |= 4;
        }
        return code;
    }

    private boolean redstoneBlocked() {
        if (this.level == null) {
            return false;
        }
        boolean powered = this.level.hasNeighborSignal(this.worldPosition);
        return (this.redstoneMode == c_y.meknh_core.common.machine.config.RedstoneMode.HIGH && !powered)
                || (this.redstoneMode == c_y.meknh_core.common.machine.config.RedstoneMode.LOW && powered);
    }

    public void cycleRedstoneMode() {
        this.redstoneMode = this.redstoneMode.next();
        this.setChanged();
    }

    public boolean privateMode() {
        return this.privateMode;
    }

    public void setOwner(java.util.UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    public boolean canAccess(java.util.UUID player) {
        return !this.privateMode || this.owner == null || this.owner.equals(player);
    }

    public void togglePrivateMode() {
        this.privateMode = !this.privateMode;
        this.setChanged();
    }

    @Override
    public int getHeatCapacitorCount() {
        return 1;
    }

    @Override
    public double getTemperature(int capacitor) {
        return this.heatCapacitor.getTemperature();
    }

    @Override
    public double getInverseConduction(int capacitor) {
        return this.heatCapacitor.getInverseConduction();
    }

    @Override
    public double getHeatCapacity(int capacitor) {
        return this.heatCapacitor.getHeatCapacity();
    }

    @Override
    public void handleHeat(int capacitor, double transfer) {
        this.heatCapacitor.handleHeat(transfer);
    }

    @Override
    public int getTanks() {
        return this.sharedInput == null ? 1 : 2;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? this.waterTank.getFluid()
                : (this.sharedInput == null ? FluidStack.EMPTY : this.sharedInput.fluid());
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? this.waterTank.getCapacity()
                : (this.sharedInput == null ? 0 : this.sharedInput.capacity());
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 ? this.waterTank.isFluidValid(stack)
                : this.sharedInput != null && this.sharedInput.isFluidValid(0, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.getFluid().isSame(Fluids.WATER)) {
            return this.waterTank.fill(resource, action);
        }
        return this.sharedInput == null ? 0 : this.sharedInput.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return this.sharedInput == null ? FluidStack.EMPTY : this.sharedInput.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return this.sharedInput == null ? FluidStack.EMPTY : this.sharedInput.drain(maxDrain, action);
    }

    @Override
    public int getChemicalTanks() {
        return this.sharedInput == null ? 1 : 2;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return this.sharedInput != null && tank == 0 ? this.sharedInput.chemical() : this.steamOutput.getStack();
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        if (this.sharedInput != null && tank == 0) {
            this.sharedInput.setChemicalInTank(0, stack);
        } else {
            this.steamOutput.setStack(stack);
        }
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return this.sharedInput != null && tank == 0 ? this.sharedInput.capacity() : this.steamOutput.getCapacity();
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return this.sharedInput != null && tank == 0 ? this.sharedInput.isValid(0, stack) : this.steamOutput.isValid(stack);
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return this.sharedInput != null && tank == 0 ? this.sharedInput.insertChemical(0, stack, action) : stack;
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        int outputIndex = this.sharedInput == null ? 0 : 1;
        return tank == outputIndex ? this.steamOutput.extract(amount, action, AutomationType.EXTERNAL) : ChemicalStack.EMPTY;
    }

    @Nullable
    public IChemicalHandler chemicalHandlerForSide(@Nullable net.minecraft.core.Direction side) {
        if (side == null) {
            return null;
        }
        c_y.meknh_core.common.machine.config.SideMode mode = this.sideMode(
                c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL, side);
        if (mode == c_y.meknh_core.common.machine.config.SideMode.NONE) {
            return null;
        }
        return new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                this, mode.allowsInsert(), mode.allowsExtract());
    }

    @Override
    @Nullable
    public IChemicalHandler dropperChemicalHandler(int index) {
        return switch (index) {
            case 0 -> this.sharedInput;
            case 1 -> new c_y.meknh_core.common.machine.SingleChemicalTankHandler(this.steamOutput);
            default -> null;
        };
    }

    @Override
    @Nullable
    public IFluidHandler dropperFluidHandler(int index) {
        return switch (index) {
            case 0 -> this.waterTank;
            case 1 -> this.sharedInput;
            default -> null;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", this.inventory.serializeNBT(registries));
        tag.putInt("burn", this.burnTime);
        tag.putInt("burnDuration", this.burnDuration);
        tag.putInt("heat", this.heatTime);
        tag.putInt("temperature", this.temperature);
        if (this.sharedInput != null) {
            this.sharedInput.save(tag, registries);
        }
        tag.put("water", this.waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("steam", this.steamOutput.serializeNBT(registries));
        tag.put("heat_capacitor", this.heatCapacitor.serializeNBT(registries));
        CompoundTag sides = new CompoundTag();
        for (int type = 0; type < 4; type++) {
            for (int i = 0; i < this.sideModes[type].length; i++) {
                sides.putInt("s" + type + "_" + i, this.sideModes[type][i].ordinal());
                sides.putBoolean("a" + type, this.autoEject[type]);
            }
        }
        tag.put("sides", sides);
        tag.putBoolean("strictInput", this.strictInput);
        tag.putInt("outputColor", this.outputColor);
        for (int i = 0; i < this.inputColors.length; i++) {
            tag.putInt("inputColor" + i, this.inputColors[i]);
        }
        tag.putInt("redstone", this.redstoneMode.ordinal());
        tag.putBoolean("private", this.privateMode);
        if (this.owner != null) {
            tag.putUUID("owner", this.owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) {
            this.inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        }
        this.burnTime = tag.getInt("burn");
        this.burnDuration = tag.getInt("burnDuration");
        this.heatTime = tag.getInt("heat");
        this.temperature = tag.contains("temperature") ? tag.getInt("temperature") : 300;
        if (this.sharedInput != null) {
            this.sharedInput.load(tag, registries);
        }
        if (tag.contains("water")) {
            this.waterTank.readFromNBT(registries, tag.getCompound("water"));
        }
        if (tag.contains("steam")) {
            this.steamOutput.deserializeNBT(registries, tag.getCompound("steam"));
        }
        if (tag.contains("heat_capacitor")) {
            this.heatCapacitor.deserializeNBT(registries, tag.getCompound("heat_capacitor"));
        }
        if (tag.contains("sides")) {
            CompoundTag sides = tag.getCompound("sides");
            for (int type = 0; type < 4; type++) {
                for (int i = 0; i < this.sideModes[type].length; i++) {
                    this.sideModes[type][i] = c_y.meknh_core.common.machine.config.SideMode
                            .byOrdinal(sides.getInt("s" + type + "_" + i));
                }
                this.autoEject[type] = sides.getBoolean("a" + type);
            }
        }
        this.strictInput = tag.getBoolean("strictInput");
        this.outputColor = tag.getInt("outputColor");
        for (int i = 0; i < this.inputColors.length; i++) {
            this.inputColors[i] = tag.getInt("inputColor" + i);
        }
        this.redstoneMode = c_y.meknh_core.common.machine.config.RedstoneMode.byOrdinal(tag.getInt("redstone"));
        this.privateMode = tag.getBoolean("private");
        if (tag.hasUUID("owner")) {
            this.owner = tag.getUUID("owner");
        }
        this.normalizeSideModes();
    }

    /**
     * 旧存档可能保存了不适用于锅炉的 INPUT/BOTH；读档时统一降级为 NONE。
     */
    private void normalizeSideModes() {
        for (c_y.meknh_core.common.machine.config.MachineConfigType type
                : c_y.meknh_core.common.machine.config.MachineConfigType.VALUES) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                int index = side.ordinal();
                if (!this.allowsSideMode(type, this.sideModes[type.ordinal()][index])) {
                    this.sideModes[type.ordinal()][index] = c_y.meknh_core.common.machine.config.SideMode.NONE;
                }
            }
        }
    }
}
