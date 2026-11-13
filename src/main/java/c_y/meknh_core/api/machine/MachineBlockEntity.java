package c_y.meknh_core.api.machine;

import c_y.meknh_core.common.data.overclock.Overclock;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.machine.energy.MachineEnergyStorage;
import c_y.meknh_core.common.machine.steam.SteamTank;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.common.recipe.MachineRecipeInput;
import c_y.meknh_core.common.recipe.MachineRecipeLookup;
import c_y.meknh_core.common.recipe.MachineOutput;
import com.mojang.logging.LogUtils;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 机器的方块实体：处理配方、消耗能量、产出产物。
 *
 * <p>运行逻辑对应 {@code 运行，超频与能耗.txt}：</p>
 * <ol>
 *     <li>找一份等级足够、输入满足的配方；</li>
 *     <li>按超频次数计算处理时间与每 tick 耗能（蒸汽机器改用蒸汽）；</li>
 *     <li>开始时一次性扣掉输入，之后每 tick 扣能量并推进度；</li>
 *     <li>进度满后产出，产物带概率。</li>
 * </ol>
 *
 * <p>本类同时实现 {@link MachineOutput}，作为配方的产物入口。</p>
 */
public class MachineBlockEntity extends BlockEntity implements MachineOutput, c_y.meknh_core.common.machine.DropperTankHolder {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MachineDefinition definition;
    private final MachineLevel machineLevel;
    private final MachineItemHandler inventory;

    @Nullable
    private final MachineEnergyStorage energyStorage;
    @Nullable
    private final FluidTank inputFluidTank;
    @Nullable
    private final FluidTank outputFluidTank;
    @Nullable
    private final IChemicalTank inputGasTank;
    @Nullable
    private final IChemicalTank outputGasTank;
    @Nullable
    private final IChemicalTank steamTank;
    @Nullable
    private final CombinedFluidHandler fluidHandler;
    @Nullable
    private final CombinedChemicalHandler chemicalHandler;

    @Nullable
    private MachineRecipe activeRecipe;
    @Nullable
    private ResourceLocation activeRecipeId;
    private int progress;
    private int totalTicks;
    private int overclocks;
    private int activeParallel = 1;
    private long energyPerTick;
    private long steamPerTick;
    private boolean running;
    private boolean noRecipeLogged;
    private StartResult lastStartResult = StartResult.NONE;

    /** 每种传输类型、每个面的物流配置（NONE / INPUT / OUTPUT / BOTH）。 */
    private final c_y.meknh_core.common.machine.config.SideMode[][] sideModes =
            new c_y.meknh_core.common.machine.config.SideMode
                    [c_y.meknh_core.common.machine.config.MachineConfigType.VALUES.length]
                    [Direction.values().length];
    private c_y.meknh_core.common.machine.config.RedstoneMode redstoneMode =
            c_y.meknh_core.common.machine.config.RedstoneMode.DISABLED;
    private boolean privateMode;
    private boolean strictInput;
    private int outputColor;
    private final int[] inputColors = new int[Direction.values().length];
    @Nullable
    private java.util.UUID owner;

    /** 每种传输类型是否自动向相邻方块输出。 */
    private final boolean[] autoEject =
            new boolean[c_y.meknh_core.common.machine.config.MachineConfigType.VALUES.length];
    private int ejectCooldown;

    /**
     * 上一次尝试开工的结果，仅用于日志排查。
     */
    private enum StartResult {
        NONE,
        STARTED,
        NO_RECIPE,
        NO_OUTPUT_SPACE,
        NO_POWER
    }

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                              MachineDefinition definition, MachineLevel level) {
        super(type, pos, state);
        this.definition = definition;
        this.machineLevel = level;
        this.inventory = new MachineItemHandler(definition.itemInputLimits(), definition.itemOutputLimits(), this::setChanged);
        for (c_y.meknh_core.common.machine.config.SideMode[] modes : this.sideModes) {
            java.util.Arrays.fill(modes, c_y.meknh_core.common.machine.config.SideMode.NONE);
        }
        // 能量通道默认全输入：电力机器默认 FE 输入，蒸汽机器默认蒸汽输入。
        for (Direction side : Direction.values()) {
            int index = side.ordinal();
            if (this.machineLevel.isSteam()) {
                this.sideModes[c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL.ordinal()][index]
                        = c_y.meknh_core.common.machine.config.SideMode.INPUT;
            } else {
                this.sideModes[c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY.ordinal()][index]
                        = c_y.meknh_core.common.machine.config.SideMode.INPUT;
            }
        }

        long fluidInput = definition.fluidInputCapacity();
        long fluidOutput = definition.fluidOutputCapacity();
        long chemicalInput = definition.chemicalInputCapacity();
        long chemicalOutput = definition.chemicalOutputCapacity();

        // 提取机的 MEK 输出罐：电力版本按等级给 8/12/16/32/64 B；
        // 蒸汽版本的容量为 0，因此无法执行带流体/化学品产物的配方。
        if (definition.id().equals(c_y.meknh_core.MekNHCore.rl("extractor"))) {
            if (level.isSteam()) {
                fluidOutput = 0L;
                chemicalOutput = 0L;
            } else {
                long mekCapacity = mekOutputCapacity(level);
                fluidOutput = mekCapacity;
                chemicalOutput = mekCapacity;
            }
        }

        this.inputFluidTank = fluidInput > 0L ? new FluidTank((int) Math.min(Integer.MAX_VALUE, fluidInput)) : null;
        this.outputFluidTank = fluidOutput > 0L ? new FluidTank((int) Math.min(Integer.MAX_VALUE, fluidOutput)) : null;

        // 蒸汽机器的蒸汽走独立的蒸汽罐；气体输入罐拒绝蒸汽，避免管道把蒸汽灌进气体槽里被「吃掉」
        java.util.function.Predicate<mekanism.api.chemical.ChemicalStack> gasInsert =
                level.isSteam()
                        ? stack -> !stack.is(mekanism.common.registries.MekanismChemicals.STEAM)
                        : stack -> true;
        this.inputGasTank = chemicalInput > 0L
                ? mekanism.api.chemical.BasicChemicalTank.createModern(chemicalInput, stack -> true, gasInsert, this::setChanged)
                : null;
        this.outputGasTank = chemicalOutput > 0L
                ? mekanism.api.chemical.BasicChemicalTank.createModern(chemicalOutput, stack -> true, stack -> true, this::setChanged)
                : null;

        this.steamTank = level.isSteam() ? SteamTank.create(level.steamCapacity(), this::setChanged) : null;
        this.energyStorage = level.isSteam() ? null : new MachineEnergyStorage(level.energyCapacity(), this::setChanged);

        this.fluidHandler = this.inputFluidTank == null && this.outputFluidTank == null
                ? null
                : new CombinedFluidHandler(nonNullTanks(this.inputFluidTank, this.outputFluidTank));
        this.chemicalHandler = this.buildChemicalHandler();
    }

    /**
     * 化学品能力里罐的顺序：蒸汽罐 → 气体输入罐 → 气体输出罐。
     *
     * <p>蒸汽罐只认蒸汽，气体输入罐拒绝蒸汽，蒸汽永远进入第一个罐。</p>
     */
    @Nullable
    private CombinedChemicalHandler buildChemicalHandler() {
        List<IChemicalTank> tanks = new java.util.ArrayList<>(3);
        if (this.steamTank != null) {
            tanks.add(this.steamTank);
        }
        if (this.inputGasTank != null) {
            tanks.add(this.inputGasTank);
        }
        if (this.outputGasTank != null) {
            tanks.add(this.outputGasTank);
        }
        return tanks.isEmpty() ? null : new CombinedChemicalHandler(tanks);
    }

    private static List<FluidTank> nonNullTanks(FluidTank... tanks) {
        return java.util.Arrays.stream(tanks).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * 提取机 MEK 输出容量：LV 8B、MV 12B、HV 16B，之后每级翻倍。
     */
    public static long mekOutputCapacity(MachineLevel level) {
        return switch (level.voltage()) {
            case LV -> 8_000L;
            case MV -> 12_000L;
            case HV -> 16_000L;
            case EV -> 32_000L;
            case IV -> 64_000L;
            default -> 0L;
        };
    }

    public MachineDefinition definition() {
        return this.definition;
    }

    public MachineLevel machineLevel() {
        return this.machineLevel;
    }

    public MachineItemHandler inventory() {
        return this.inventory;
    }

    /**
     * 电力机器的能量槽（绿色 slot/power.png）：放带 FE 的物品，每 tick 往机器里充。
     */
    private final net.neoforged.neoforge.items.ItemStackHandler energySlot =
            new net.neoforged.neoforge.items.ItemStackHandler(1) {
                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM) != null;
                }

                @Override
                protected void onContentsChanged(int slot) {
                    MachineBlockEntity.this.setChanged();
                }
            };

    public net.neoforged.neoforge.items.ItemStackHandler energySlot() {
        return this.energySlot;
    }

    private void chargeFromEnergySlot() {
        if (this.energyStorage == null) {
            return;
        }
        ItemStack stack = this.energySlot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        net.neoforged.neoforge.energy.IEnergyStorage itemEnergy =
                stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (itemEnergy == null) {
            return;
        }
        int space = this.energyStorage.getMaxEnergyStored() - this.energyStorage.getEnergyStored();
        int moved = itemEnergy.extractEnergy(Math.min(space, 1000), true);
        if (moved <= 0) {
            return;
        }
        int accepted = this.energyStorage.receiveEnergy(moved, false);
        if (accepted > 0) {
            itemEnergy.extractEnergy(accepted, false);
        }
    }

    // ------------------------------------------------------------------
    // 侧面配置 / 红石 / 安全
    // ------------------------------------------------------------------

    public c_y.meknh_core.common.machine.config.SideMode sideMode(
            c_y.meknh_core.common.machine.config.MachineConfigType type, Direction side) {
        return this.sideModes[type.ordinal()][side.ordinal()];
    }

    public void cycleSideMode(c_y.meknh_core.common.machine.config.MachineConfigType type, Direction side) {
        int index = side.ordinal();
        this.sideModes[type.ordinal()][index] = this.allowedNextMode(type,
                this.sideModes[type.ordinal()][index], false);
        this.setChanged();
        this.invalidateSideCapabilities();
    }

    public void cycleSideMode(c_y.meknh_core.common.machine.config.MachineConfigType type, Direction side, boolean reverse) {
        int index = side.ordinal();
        this.sideModes[type.ordinal()][index] = this.allowedNextMode(type,
                this.sideModes[type.ordinal()][index], reverse);
        this.setChanged();
        this.invalidateSideCapabilities();
    }

    /**
     * 耗电机器/蒸汽机器的能量通道只允许 NONE/INPUT；发电机才允许 OUTPUT/BOTH。
     */
    public boolean allowsSideMode(c_y.meknh_core.common.machine.config.MachineConfigType type,
                                  c_y.meknh_core.common.machine.config.SideMode mode) {
        if (type == c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY) {
            return mode == c_y.meknh_core.common.machine.config.SideMode.NONE
                    || mode == c_y.meknh_core.common.machine.config.SideMode.INPUT;
        }
        if (type == c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL
                && this.machineLevel.isSteam()) {
            // 蒸汽机器的横向化学品槽就是能量槽：只允许输入蒸汽，不允许弹出。
            return mode == c_y.meknh_core.common.machine.config.SideMode.NONE
                    || mode == c_y.meknh_core.common.machine.config.SideMode.INPUT;
        }
        return true;
    }

    public boolean canEjectSideType(c_y.meknh_core.common.machine.config.MachineConfigType type) {
        return type != c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY
                && !(type == c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL
                && this.machineLevel.isSteam());
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

    private void invalidateSideCapabilities() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.invalidateCapabilities(this.worldPosition);
            // 同时通知相邻管道/机器重新查询能力，避免它们继续持有旧引用。
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
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

    public int inputColor(Direction side) {
        return this.inputColors[side.ordinal()];
    }

    public void cycleOutputColor() {
        this.outputColor = (this.outputColor + 1) % 16;
        this.setChanged();
    }

    public void cycleInputColor(Direction side) {
        int index = side.ordinal();
        this.inputColors[index] = (this.inputColors[index] + 1) % 16;
        this.setChanged();
    }

    /**
     * 把某一传输类型的所有面清空为 NONE。
     */
    public void clearSideModes(c_y.meknh_core.common.machine.config.MachineConfigType type) {
        java.util.Arrays.fill(this.sideModes[type.ordinal()], c_y.meknh_core.common.machine.config.SideMode.NONE);
        this.setChanged();
        this.invalidateSideCapabilities();
    }

    /**
     * 对应 Mek Side Config 的批量清空/递增按钮：把当前类型的六个面统一成
     * “共同模式的下一个/上一个”，混合模式则统一为 NONE。
     */
    public void batchCycleSideModes(c_y.meknh_core.common.machine.config.MachineConfigType type, boolean reverse) {
        c_y.meknh_core.common.machine.config.SideMode common = null;
        for (Direction side : Direction.values()) {
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
        this.invalidateSideCapabilities();
    }

    public void clearAllSideModes() {
        for (c_y.meknh_core.common.machine.config.SideMode[] modes : this.sideModes) {
            java.util.Arrays.fill(modes, c_y.meknh_core.common.machine.config.SideMode.NONE);
        }
        this.setChanged();
        this.invalidateSideCapabilities();
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

    /**
     * 某个面是否允许指定传输类型的外部物流（NONE 就断开）。
     */
    public boolean allowsConnection(c_y.meknh_core.common.machine.config.MachineConfigType type, @Nullable Direction side) {
        // 外部能力查询必须带面；null 面不再被当成“全允许”，否则管道会绕过侧面配置。
        return side != null && this.sideModes[type.ordinal()][side.ordinal()]
                != c_y.meknh_core.common.machine.config.SideMode.NONE;
    }

    /**
     * 某个面的物品能力：NONE 断开；INPUT/OUTPUT 只允许一个方向；BOTH 和以前一样。
     */
    @Nullable
    public net.neoforged.neoforge.items.IItemHandler itemHandlerFor(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        c_y.meknh_core.common.machine.config.SideMode mode = this.sideMode(
                c_y.meknh_core.common.machine.config.MachineConfigType.ITEM, side);
        if (mode == c_y.meknh_core.common.machine.config.SideMode.NONE) {
            return null;
        }
        if (mode == c_y.meknh_core.common.machine.config.SideMode.BOTH) {
            return this.inventory.externalView();
        }
        return this.inventory.directionalView(mode.allowsInsert(), mode.allowsExtract());
    }

    public c_y.meknh_core.common.machine.config.RedstoneMode redstoneMode() {
        return this.redstoneMode;
    }

    public void cycleRedstoneMode() {
        this.redstoneMode = this.redstoneMode.next();
        this.setChanged();
    }

    public boolean privateMode() {
        return this.privateMode;
    }

    public void togglePrivateMode() {
        this.privateMode = !this.privateMode;
        this.setChanged();
    }

    public void setOwner(Player player) {
        this.owner = player.getUUID();
        this.setChanged();
    }

    public boolean isOwner(Player player) {
        return this.owner != null && this.owner.equals(player.getUUID());
    }

    public boolean hasOwner() {
        return this.owner != null;
    }

    /**
     * 私有模式下只有主人能打开界面。
     */
    public boolean canUse(Player player) {
        return !this.privateMode || this.isOwner(player);
    }

    private boolean redstoneAllows() {
        Level world = this.level;
        if (world == null) {
            return true;
        }
        return switch (this.redstoneMode) {
            case DISABLED -> true;
            case HIGH -> world.hasNeighborSignal(this.worldPosition);
            case LOW -> !world.hasNeighborSignal(this.worldPosition);
        };
    }

    @Nullable
    public MachineEnergyStorage energyStorage() {
        return this.energyStorage;
    }

    /**
     * 按侧面配置返回真正受限的能量视图：NONE 断开，INPUT 只进，OUTPUT 只出，BOTH 双向。
     */
    @Nullable
    public net.neoforged.neoforge.energy.IEnergyStorage energyStorageForSide(@Nullable Direction side) {
        if (this.energyStorage == null || !this.allowsConnection(
                c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY, side)) {
            return null;
        }
        c_y.meknh_core.common.machine.config.SideMode mode = this.sideMode(
                c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY, side);
        MachineEnergyStorage storage = this.energyStorage;
        return new net.neoforged.neoforge.energy.IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return mode.allowsInsert() ? storage.receiveEnergy(maxReceive, simulate) : 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return mode.allowsExtract() ? storage.extractEnergy(maxExtract, simulate) : 0;
            }

            @Override
            public int getEnergyStored() {
                return storage.getEnergyStored();
            }

            @Override
            public int getMaxEnergyStored() {
                return storage.getMaxEnergyStored();
            }

            @Override
            public boolean canExtract() {
                return mode.allowsExtract() && storage.canExtract();
            }

            @Override
            public boolean canReceive() {
                return mode.allowsInsert() && storage.canReceive();
            }
        };
    }

    /**
     * 给 Mekanism LookingAt/Jade 用的严格能量视图。
     */
    public mekanism.api.energy.IStrictEnergyHandler strictEnergyHandler() {
        return this.strictEnergyHandler(true);
    }

    /**
     * 严格能量能力的按面视图；NONE 面返回 null，线缆不会连接。
     */
    @Nullable
    public mekanism.api.energy.IStrictEnergyHandler strictEnergyHandlerForSide(@Nullable Direction side) {
        if (this.energyStorage == null || !this.allowsConnection(
                c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY, side)) {
            return null;
        }
        c_y.meknh_core.common.machine.config.SideMode mode = this.sideMode(
                c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY, side);
        return this.strictEnergyHandler(mode.allowsInsert());
    }

    private mekanism.api.energy.IStrictEnergyHandler strictEnergyHandler(boolean allowInsert) {
        return new mekanism.api.energy.IStrictEnergyHandler() {
            @Override
            public int getEnergyContainerCount() {
                return MachineBlockEntity.this.energyStorage == null ? 0 : 1;
            }

            @Override
            public long getEnergy(int container) {
                return MachineBlockEntity.this.energyStorage == null ? 0L
                        : Math.round(MachineBlockEntity.this.energyStorage.getEnergyStored() * 2.5D);
            }

            @Override
            public void setEnergy(int container, long energy) {
                if (MachineBlockEntity.this.energyStorage != null) {
                    MachineBlockEntity.this.energyStorage.setEnergyStored(Math.round(energy / 2.5D));
                }
            }

            @Override
            public long getMaxEnergy(int container) {
                return MachineBlockEntity.this.energyStorage == null ? 0L
                        : Math.round(MachineBlockEntity.this.energyStorage.getMaxEnergyStored() * 2.5D);
            }

            @Override
            public long getNeededEnergy(int container) {
                return Math.max(0L, this.getMaxEnergy(container) - this.getEnergy(container));
            }

            @Override
            public long insertEnergy(int container, long amount, mekanism.api.Action action) {
                if (!allowInsert || MachineBlockEntity.this.energyStorage == null || amount <= 0L) {
                    return amount;
                }
                int accepted = MachineBlockEntity.this.energyStorage.receiveEnergy(
                        (int)Math.min(Integer.MAX_VALUE, Math.round(amount / 2.5D)), action.simulate());
                return amount - Math.round(accepted * 2.5D);
            }

            @Override
            public long extractEnergy(int container, long amount, mekanism.api.Action action) {
                return 0L;
            }
        };
    }

    @Nullable
    public CombinedFluidHandler fluidHandler() {
        return this.fluidHandler;
    }

    @Nullable
    public CombinedChemicalHandler chemicalHandler() {
        return this.chemicalHandler;
    }

    /**
     * 按侧面配置返回受限的化学品视图；NONE 断开，INPUT/OUTPUT 只允许对应方向。
     */
    @Nullable
    public mekanism.api.chemical.IChemicalHandler chemicalHandlerForSide(@Nullable Direction side) {
        if (this.chemicalHandler == null || !this.allowsConnection(
                c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL, side)) {
            return null;
        }
        c_y.meknh_core.common.machine.config.SideMode mode = this.sideMode(
                c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL, side);
        return new c_y.meknh_core.common.machine.config.SidedChemicalHandler(
                this.chemicalHandler, mode.allowsInsert(), mode.allowsExtract());
    }

    @Nullable
    public FluidTank inputFluidTank() {
        return this.inputFluidTank;
    }

    @Nullable
    public FluidTank outputFluidTank() {
        return this.outputFluidTank;
    }

    @Nullable
    public IChemicalTank inputGasTank() {
        return this.inputGasTank;
    }

    @Nullable
    public IChemicalTank outputGasTank() {
        return this.outputGasTank;
    }

    @Nullable
    public IChemicalTank steamTank() {
        return this.steamTank;
    }

    @Override
    @Nullable
    public mekanism.api.chemical.IChemicalHandler dropperChemicalHandler(int index) {
        return switch (index) {
            case 0 -> this.steamTank == null ? null : new c_y.meknh_core.common.machine.SingleChemicalTankHandler(this.steamTank);
            case 1 -> this.inputGasTank == null ? null : new c_y.meknh_core.common.machine.SingleChemicalTankHandler(this.inputGasTank);
            case 2 -> this.outputGasTank == null ? null : new c_y.meknh_core.common.machine.SingleChemicalTankHandler(this.outputGasTank);
            default -> null;
        };
    }

    @Override
    @Nullable
    public net.neoforged.neoforge.fluids.capability.IFluidHandler dropperFluidHandler(int index) {
        return switch (index) {
            case 0 -> this.inputFluidTank;
            case 1 -> this.outputFluidTank;
            default -> null;
        };
    }

    /**
     * 当前每 tick 蒸汽消耗（mB/t），用于能量标签页提示。
     */
    public long steamPerTick() {
        return this.steamPerTick;
    }

    public long energyPerTick() {
        return this.energyPerTick;
    }

    /**
     * GUI 问题提示位：1=蒸汽不足，2=没有匹配配方，4=输出已满。
     */
    public int warningCode() {
        int code = this.redstoneBlocked() ? 8 : 0;
        if (!this.running && this.hasInputs()) {
            code |= switch (this.lastStartResult) {
                case NO_POWER -> 1;
                case NO_RECIPE -> 2;
                case NO_OUTPUT_SPACE -> 4;
                default -> 0;
            };
        }
        return code;
    }

    /**
     * 红石控制条件当前是否阻止机器运行（用于红石 tab 的警告底板与问题 tab）。
     */
    public boolean redstoneBlocked() {
        return !this.redstoneAllows();
    }

    /**
     * 当前进度（tick）。
     */
    public int progress() {
        return this.progress;
    }

    /**
     * 当前批次总时长（tick）。
     */
    public int totalTicks() {
        return this.totalTicks;
    }

    /**
     * 是否正在处理。
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * 区块加载时发一次完整数据，客户端方块实体与存档保持一致。
     *
     * <p>界面用的进度/储量不走方块更新包，而是走 {@code MachineMenu} 的
     * {@code ContainerData}（和 Mekanism 一样由容器同步）。</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    /**
     * 服务端每 tick 调用。
     */
    public void serverTick() {
        Level world = this.level;
        if (world == null || world.isClientSide) {
            return;
        }
        if (++this.ejectCooldown >= 5) {
            this.ejectCooldown = 0;
            this.autoEjectToNeighbors();
        }
        // 红石模式不满足时暂停：不消耗能量也不推进进度，批次保留
        if (!this.redstoneAllows()) {
            this.setActive(false);
            return;
        }
        this.chargeFromEnergySlot();
        if (this.activeRecipe == null) {
            if (this.resolvePendingRecipe()) {
                // 已按存档里的配方恢复未完成的批次，继续推进
            } else if (!this.startRecipe()) {
                this.logNoRecipeOnce();
                this.stopRunning();
                return;
            }
        }
        if (!this.consumePower()) {
            this.setActive(false);
            return;
        }
        this.progress++;
        if (this.progress >= this.totalTicks) {
            this.finishRecipe();
        } else {
            this.setActive(true);
        }
    }

    private void autoEjectToNeighbors() {
        Level world = this.level;
        if (world == null) {
            return;
        }
        for (c_y.meknh_core.common.machine.config.MachineConfigType type
                : c_y.meknh_core.common.machine.config.MachineConfigType.VALUES) {
            if (!this.autoEject[type.ordinal()]) {
                continue;
            }
            // 蒸汽罐只进不出，禁止把蒸汽作为输出弹出。
            if (type == c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL && this.machineLevel.isSteam()) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (!this.sideMode(type, side).allowsExtract()) {
                    continue;
                }
                BlockPos targetPos = this.worldPosition.relative(side);
                switch (type) {
                    case ITEM -> this.ejectItems(world, side, targetPos);
                    case FLUID -> this.ejectFluid(world, side, targetPos);
                    case CHEMICAL -> this.ejectChemical(world, side, targetPos);
                    case ENERGY -> {
                        // 机器本身不产能，能量自动输出无意义。
                    }
                }
            }
        }
    }

    private void ejectItems(Level world, Direction side, BlockPos targetPos) {
        net.neoforged.neoforge.items.IItemHandler target = world.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                targetPos, side.getOpposite());
        if (target == null) {
            return;
        }
        for (int slot = this.inventory.inputSlots(); slot < this.inventory.getSlots(); slot++) {
            ItemStack stack = this.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int targetSlot = 0; targetSlot < target.getSlots() && !stack.isEmpty(); targetSlot++) {
                ItemStack simulated = target.insertItem(targetSlot, stack, true);
                int accepted = stack.getCount() - simulated.getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack extracted = this.inventory.extractItem(slot, accepted, false);
                target.insertItem(targetSlot, extracted, false);
                stack = this.inventory.getStackInSlot(slot);
            }
        }
    }

    private void ejectFluid(Level world, Direction side, BlockPos targetPos) {
        if (this.outputFluidTank == null || this.outputFluidTank.isEmpty()) {
            return;
        }
        net.neoforged.neoforge.fluids.capability.IFluidHandler target = world.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                targetPos, side.getOpposite());
        if (target == null) {
            return;
        }
        int accepted = target.fill(this.outputFluidTank.getFluid(),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return;
        }
        net.neoforged.neoforge.fluids.FluidStack drained = this.outputFluidTank.drain(accepted,
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) {
            target.fill(drained, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private void ejectChemical(Level world, Direction side, BlockPos targetPos) {
        if (this.outputGasTank == null || this.outputGasTank.isEmpty()) {
            return;
        }
        mekanism.api.chemical.IChemicalHandler target = world.getCapability(
                mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                targetPos, side.getOpposite());
        if (target == null) {
            return;
        }
        ChemicalStack simulated = target.insertChemical(this.outputGasTank.getStack(), Action.SIMULATE);
        long accepted = this.outputGasTank.getStored() - simulated.getAmount();
        if (accepted <= 0L) {
            return;
        }
        ChemicalStack drained = this.outputGasTank.extract(accepted, Action.EXECUTE,
                mekanism.api.AutomationType.INTERNAL);
        if (!drained.isEmpty()) {
            target.insertChemical(drained, Action.EXECUTE);
        }
    }

    /**
     * 尝试开始一个新批次。
     *
     * <p>只有在「配方匹配 + 输出放得下 + 至少够一 tick 的能量」时才扣输入，
     * 避免没电时把玩家的原料吞掉。</p>
     */
    private boolean startRecipe() {
        Level world = this.level;
        if (world == null) {
            return false;
        }
        MachineRecipeInput input = this.recipeInput();
        RecipeHolder<MachineRecipe> holder = MachineRecipeLookup.find(world, this.definition.id(), this.machineLevel.recipeTier(), input);
        if (holder == null) {
            this.lastStartResult = StartResult.NO_RECIPE;
            return false;
        }
        MachineRecipe recipe = holder.value();
        // 并行数：等级上限，再受当前输入数量限制，最后降到输出放得下的最大值。
        int parallel = Math.min(this.machineLevel.parallel(), recipe.maxCrafts(input));
        if (parallel < 1) {
            this.lastStartResult = StartResult.NO_RECIPE;
            return false;
        }
        while (parallel > 1 && !recipe.canFitOutputs(this, parallel)) {
            parallel--;
        }
        if (!recipe.canFitOutputs(this, parallel)) {
            this.lastStartResult = StartResult.NO_OUTPUT_SPACE;
            return false;
        }

        int baseTicks = this.definition.resolveTime(recipe);
        // 并行不再附加能耗惩罚：每 tick 能耗仍按单份配方的 energy 计算。
        double amperage = this.definition.resolveEnergy(recipe);
        int countedOverclocks = Overclock.count(this.machineLevel, recipe.level());
        int countedTicks = Overclock.ticks(baseTicks, countedOverclocks, this.machineLevel);
        // 配方电压不跟随机器：配方写什么等级，就按该等级的电压计算能耗。
        long power = Overclock.energyPerTick(amperage, recipe.level(), countedOverclocks);
        long countedEnergyPerTick = this.machineLevel.isSteam() ? 0L : power;
        long countedSteamPerTick = this.machineLevel.steamPerTick(power);
        if (!this.canAffordTick(countedEnergyPerTick, countedSteamPerTick)) {
            this.lastStartResult = StartResult.NO_POWER;
            return false;
        }

        this.activeRecipe = recipe;
        this.activeRecipeId = holder.id();
        this.overclocks = countedOverclocks;
        this.activeParallel = parallel;
        this.totalTicks = countedTicks;
        this.energyPerTick = countedEnergyPerTick;
        this.steamPerTick = countedSteamPerTick;
        this.progress = 0;

        recipe.consumeInputs(input, parallel);
        this.noRecipeLogged = false;
        this.lastStartResult = StartResult.STARTED;
        this.setChanged();
        return true;
    }

    /**
     * 缓存里是否足够跑一 tick（避免没电时先把输入扣掉）。
     */
    private boolean canAffordTick(long energy, long steam) {
        if (this.machineLevel.isSteam()) {
            return steam <= 0L || (this.steamTank != null && this.steamTank.getStored() >= steam);
        }
        return energy <= 0L || (this.energyStorage != null && this.energyStorage.getEnergyStored() >= energy);
    }

    /**
     * 有输入却匹配不到配方时，每个方块实体只告警一次，方便排查数据包问题。
     */
    private void logNoRecipeOnce() {
        if (this.noRecipeLogged || !this.hasInputs()) {
            return;
        }
        this.noRecipeLogged = true;
        int itemCount = 0;
        for (int slot = 0; slot < this.inventory.inputSlots(); slot++) {
            if (!this.inventory.getStackInSlot(slot).isEmpty()) {
                itemCount++;
            }
        }
        int registered = this.level == null ? -1 : MachineRecipeLookup
                .allFor(this.level.getRecipeManager(), this.definition.id()).size();
        if (this.lastStartResult == StartResult.NO_POWER) {
            if (this.machineLevel.isSteam()) {
                LOGGER.warn("Machine {} ({}) has input but needs {} mB/t steam and only has {} mB; "
                                + "the batch will not start until the steam buffer is filled",
                        this.getBlockState().getBlock().getName().getString(), this.worldPosition,
                        this.steamPerTick, this.steamTank == null ? 0L : this.steamTank.getStored());
            } else {
                LOGGER.warn("Machine {} ({}) has input but needs {} FE/t and only has {} FE; "
                                + "the batch will not start until the energy buffer is filled",
                        this.getBlockState().getBlock().getName().getString(), this.worldPosition,
                        this.energyPerTick, this.energyStorage == null ? 0L : this.energyStorage.getEnergyStored());
            }
            return;
        }
        if (this.lastStartResult == StartResult.NO_OUTPUT_SPACE) {
            LOGGER.warn("Machine {} ({}) has input and a matching recipe but the output slots are full",
                    this.getBlockState().getBlock().getName().getString(), this.worldPosition);
            return;
        }
        if (registered == 0) {
            LOGGER.warn("Machine {} ({}) has input but 0 recipe(s) are registered for {}; "
                            + "check that recipe JSON lives in data/<namespace>/recipe/{}/*.json with "
                            + "\"type\": \"meknh_core:machine\" and \"id\": \"{}\"",
                    this.getBlockState().getBlock().getName().getString(), this.worldPosition,
                    this.definition.id(), this.definition.id().getPath(), this.definition.id());
        } else {
            LOGGER.warn("Machine {} ({}) has input ({} item slot(s) filled) but none of the {} registered "
                            + "recipe(s) matched; machine level is {} (recipes above it are skipped), "
                            + "check the recipe inputs",
                    this.getBlockState().getBlock().getName().getString(), this.worldPosition,
                    itemCount, registered, this.machineLevel.recipeTier().getSerializedName());
        }
    }

    /**
     * 输入槽或输入罐里是否有东西。
     */
    private boolean hasInputs() {
        for (int slot = 0; slot < this.inventory.inputSlots(); slot++) {
            if (!this.inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        if (this.inputFluidTank != null && !this.inputFluidTank.isEmpty()) {
            return true;
        }
        return this.inputGasTank != null && !this.inputGasTank.isEmpty();
    }

    /**
     * 重载后恢复未完成的批次：按存档里的配方 ID 找回配方对象。
     */
    private boolean resolvePendingRecipe() {
        Level world = this.level;
        if (world == null || this.activeRecipeId == null) {
            return false;
        }
        RecipeHolder<?> holder = world.getRecipeManager().byKey(this.activeRecipeId).orElse(null);
        if (holder != null && holder.value() instanceof MachineRecipe recipe) {
            this.activeRecipe = recipe;
            this.activeParallel = Math.max(1, this.activeParallel);
            return true;
        }
        this.activeRecipeId = null;
        this.progress = 0;
        return false;
    }

    /**
     * 消耗本 tick 的能量/蒸汽。
     */
    private boolean consumePower() {
        if (this.machineLevel.isSteam()) {
            if (this.steamPerTick <= 0L || this.steamTank == null) {
                return true;
            }
            return this.steamTank.shrinkStack(this.steamPerTick, Action.EXECUTE) >= this.steamPerTick;
        }
        if (this.energyPerTick <= 0L) {
            return true;
        }
        return this.energyStorage != null && this.energyStorage.consume(this.energyPerTick);
    }

    /**
     * 完成当前批次：产出产物并复位。
     */
    private void finishRecipe() {
        MachineRecipe recipe = this.activeRecipe;
        if (recipe != null && this.level != null) {
            recipe.produce(this, this.level.random, this.activeParallel);
        }
        this.activeRecipe = null;
        this.activeRecipeId = null;
        this.activeParallel = 1;
        this.progress = 0;
        this.totalTicks = 0;
        this.running = false;
        this.setActive(false);
        this.setChanged();
    }

    private void stopRunning() {
        if (this.progress != 0 || this.running) {
            this.progress = 0;
            this.activeParallel = 1;
            this.running = false;
            this.setChanged();
        }
        this.setActive(false);
    }

    private void setActive(boolean active) {
        if (this.running != active) {
            this.running = active;
        }
        Level world = this.level;
        if (world == null || world.isClientSide) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            world.setBlock(this.worldPosition, state.setValue(MachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 构造输入视图：只包含输入物品槽与输入罐，避免把输出槽里的东西当成原料。
     */
    public MachineRecipeInput recipeInput() {
        return new MachineRecipeInput(new InputView(this.inventory), this.inputFluidTank,
                this.inputGasTank == null ? null : new CombinedChemicalHandler(this.inputGasTank));
    }

    /**
     * 只暴露输入物品槽的视图；允许扣除（供配方消耗输入）。
     */
    private static final class InputView implements net.neoforged.neoforge.items.IItemHandler {

        private final MachineItemHandler handler;

        private InputView(MachineItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public int getSlots() {
            return this.handler.inputSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < this.getSlots() ? this.handler.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= 0 && slot < this.getSlots() ? this.handler.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.getSlots() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = this.handler.getStackInSlot(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, current.getCount());
            ItemStack result = current.copyWithCount(taken);
            if (!simulate) {
                this.handler.setStackInSlot(slot, taken >= current.getCount()
                        ? ItemStack.EMPTY
                        : current.copyWithCount(current.getCount() - taken));
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.handler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < this.getSlots() && this.handler.isItemValid(slot, stack);
        }
    }

    @Override
    public int insertItem(ItemStack stack, boolean simulate) {
        // 输出槽不能走 ItemStackHandler#insertItem（它按 isItemValid 拒绝输出槽），
        // 否则 canFitOutputs 永远为 false，机器一开工就判定「产出放不下」而永远不启动。
        int inserted = this.inventory.insertOutput(stack, simulate);
        if (inserted > 0 && !simulate) {
            this.setChanged();
        }
        return inserted;
    }

    @Override
    public long insertFluid(FluidStack stack, boolean simulate) {
        if (this.outputFluidTank == null || stack.isEmpty()) {
            return 0L;
        }
        return this.outputFluidTank.fill(stack, simulate
                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public long insertChemical(ChemicalStack stack, boolean simulate) {
        if (this.outputGasTank == null || stack.isEmpty()) {
            return 0L;
        }
        ChemicalStack leftover = this.outputGasTank.insert(stack, simulate ? Action.SIMULATE : Action.EXECUTE,
                mekanism.api.AutomationType.INTERNAL);
        long inserted = stack.getAmount() - leftover.getAmount();
        if (inserted > 0 && !simulate) {
            this.setChanged();
        }
        return inserted;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        if (this.energyStorage != null) {
            tag.put("Energy", this.energyStorage.serializeNBT(registries));
        }
        if (this.inputFluidTank != null) {
            tag.put("InputFluid", this.inputFluidTank.writeToNBT(registries, new CompoundTag()));
        }
        if (this.outputFluidTank != null) {
            tag.put("OutputFluid", this.outputFluidTank.writeToNBT(registries, new CompoundTag()));
        }
        if (this.inputGasTank != null) {
            tag.put("InputGas", this.inputGasTank.serializeNBT(registries));
        }
        if (this.outputGasTank != null) {
            tag.put("OutputGas", this.outputGasTank.serializeNBT(registries));
        }
        if (this.steamTank != null) {
            tag.put("Steam", this.steamTank.serializeNBT(registries));
        }
        tag.putInt("Progress", this.progress);
        tag.putInt("TotalTicks", this.totalTicks);
        tag.putInt("Overclocks", this.overclocks);
        tag.putInt("ActiveParallel", this.activeParallel);
        tag.putLong("EnergyPerTick", this.energyPerTick);
        tag.putLong("SteamPerTick", this.steamPerTick);
        if (this.activeRecipeId != null) {
            tag.putString("Recipe", this.activeRecipeId.toString());
        }

        byte[] sides = new byte[this.sideModes.length * Direction.values().length];
        for (int type = 0; type < this.sideModes.length; type++) {
            for (int index = 0; index < this.sideModes[type].length; index++) {
                sides[type * this.sideModes[type].length + index] = (byte) this.sideModes[type][index].ordinal();
            }
        }
        tag.putByteArray("SideModes", sides);
        byte[] eject = new byte[this.autoEject.length];
        for (int index = 0; index < this.autoEject.length; index++) {
            eject[index] = (byte) (this.autoEject[index] ? 1 : 0);
        }
        tag.putByteArray("AutoEject", eject);
        tag.putByte("RedstoneMode", (byte) this.redstoneMode.ordinal());
        tag.putBoolean("PrivateMode", this.privateMode);
        tag.putBoolean("StrictInput", this.strictInput);
        tag.putInt("OutputColor", this.outputColor);
        tag.putIntArray("InputColors", this.inputColors);
        tag.put("EnergySlot", this.energySlot.serializeNBT(registries));
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        if (this.energyStorage != null && tag.contains("Energy")) {
            // 注意：NeoForge 的 EnergyStorage 序列化出来是「数值 Tag」（IntTag），
            // 不能用 getCompound 读，否则 deserializeNBT 会抛
            // IllegalArgumentException: Can not deserialize to an instance that isn't the default implementation
            this.energyStorage.setEnergyStored(tag.getInt("Energy"));
        }
        if (this.inputFluidTank != null && tag.contains("InputFluid")) {
            this.inputFluidTank.readFromNBT(registries, tag.getCompound("InputFluid"));
        }
        if (this.outputFluidTank != null && tag.contains("OutputFluid")) {
            this.outputFluidTank.readFromNBT(registries, tag.getCompound("OutputFluid"));
        }
        if (this.inputGasTank != null && tag.contains("InputGas")) {
            this.inputGasTank.deserializeNBT(registries, tag.getCompound("InputGas"));
        }
        if (this.outputGasTank != null && tag.contains("OutputGas")) {
            this.outputGasTank.deserializeNBT(registries, tag.getCompound("OutputGas"));
        }
        if (this.steamTank != null && tag.contains("Steam")) {
            this.steamTank.deserializeNBT(registries, tag.getCompound("Steam"));
        }
        this.progress = tag.getInt("Progress");
        this.totalTicks = tag.getInt("TotalTicks");
        this.overclocks = tag.getInt("Overclocks");
        this.activeParallel = Math.max(1, tag.getInt("ActiveParallel"));
        this.energyPerTick = tag.getLong("EnergyPerTick");
        this.steamPerTick = tag.getLong("SteamPerTick");
        this.activeRecipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;

        if (tag.contains("SideModes")) {
            byte[] sides = tag.getByteArray("SideModes");
            if (sides.length == Direction.values().length) {
                // 旧存档：一份全局配置，套用到所有传输类型。
                for (c_y.meknh_core.common.machine.config.SideMode[] modes : this.sideModes) {
                    for (int index = 0; index < modes.length; index++) {
                        modes[index] = c_y.meknh_core.common.machine.config.SideMode.byOrdinal(sides[index]);
                    }
                }
            } else {
                for (int type = 0; type < this.sideModes.length; type++) {
                    for (int index = 0; index < this.sideModes[type].length; index++) {
                        int offset = type * this.sideModes[type].length + index;
                        this.sideModes[type][index] = c_y.meknh_core.common.machine.config.SideMode.byOrdinal(
                                offset < sides.length ? sides[offset] : c_y.meknh_core.common.machine.config.SideMode.NONE.ordinal());
                    }
                }
            }
        }
        this.redstoneMode = c_y.meknh_core.common.machine.config.RedstoneMode.byOrdinal(tag.getByte("RedstoneMode"));
        if (tag.contains("AutoEject")) {
            byte[] eject = tag.getByteArray("AutoEject");
            for (int index = 0; index < this.autoEject.length; index++) {
                this.autoEject[index] = index < eject.length && eject[index] != 0;
            }
        }
        this.privateMode = tag.getBoolean("PrivateMode");
        this.strictInput = tag.getBoolean("StrictInput");
        this.outputColor = tag.getInt("OutputColor");
        int[] colors = tag.getIntArray("InputColors");
        for (int index = 0; index < this.inputColors.length; index++) {
            this.inputColors[index] = index < colors.length ? colors[index] : 0;
        }
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.contains("EnergySlot")) {
            this.energySlot.deserializeNBT(registries, tag.getCompound("EnergySlot"));
        }
        this.normalizeSideModes();
    }

    /**
     * 旧存档可能保存了耗电机器不允许的能量 OUTPUT/BOTH；读档时统一降级为 NONE。
     */
    private void normalizeSideModes() {
        for (c_y.meknh_core.common.machine.config.MachineConfigType type
                : c_y.meknh_core.common.machine.config.MachineConfigType.VALUES) {
            for (Direction side : Direction.values()) {
                int index = side.ordinal();
                if (!this.allowsSideMode(type, this.sideModes[type.ordinal()][index])) {
                    this.sideModes[type.ordinal()][index] = c_y.meknh_core.common.machine.config.SideMode.NONE;
                }
            }
        }
    }
}
