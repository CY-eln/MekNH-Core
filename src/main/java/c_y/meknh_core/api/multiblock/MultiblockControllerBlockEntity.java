package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.api.machine.CombinedChemicalHandler;
import c_y.meknh_core.api.machine.CombinedFluidHandler;
import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.common.data.overclock.Overclock;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.common.recipe.MachineRecipeInput;
import c_y.meknh_core.common.recipe.MachineRecipeLookup;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 多方块控制器：结构匹配 + 仓室聚合 + 配方执行。
 *
 * <p>生命周期：</p>
 * <ol>
 *     <li>未成型时每 {@value #CHECK_INTERVAL} tick 尝试一次结构匹配；</li>
 *     <li>成型后每 {@value #VALIDATE_INTERVAL} tick 复检一次，仓室被拆/放错会立即失效；</li>
 *     <li>相邻方块变化与仓室移除会提前触发复检；</li>
 *     <li>成型的控制器从输入仓取原料、从能量仓扣 FE，按配方推进进度，
 *     完成后把产物写进输出仓。</li>
 * </ol>
 *
 * <p>控制器不提供 GUI：输入输出全部通过仓室能力（漏斗/管道/右键）完成。
 * 右键控制器会在聊天栏打印结构状态，方便搭结构时排查。</p>
 */
public class MultiblockControllerBlockEntity extends BlockEntity implements MachineOutput {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 未成型时的结构复检间隔（tick）。
     */
    public static final int CHECK_INTERVAL = 20;

    /**
     * 成型后的结构复检间隔（tick）。
     */
    public static final int VALIDATE_INTERVAL = 40;

    private final MultiblockDefinition definition;
    private final MachineLevel machineLevel;

    private boolean formed;
    private boolean needsValidation = true;
    private boolean checkRequested;
    private int checkCooldown = 5;
    private int validateCooldown = VALIDATE_INTERVAL;

    private List<MultiblockPart> parts = List.of();

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

    private String lastError = "";

    public MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                           MultiblockDefinition definition, MachineLevel level) {
        super(type, pos, state);
        this.definition = definition;
        this.machineLevel = level;
    }

    public MultiblockDefinition definition() {
        return this.definition;
    }

    public MachineLevel level() {
        return this.machineLevel;
    }

    public boolean isFormed() {
        return this.formed;
    }

    public boolean isRunning() {
        return this.running;
    }

    public int progress() {
        return this.progress;
    }

    public int totalTicks() {
        return this.totalTicks;
    }

    public long energyPerTick() {
        return this.energyPerTick;
    }

    public long steamPerTick() {
        return this.steamPerTick;
    }

    public String lastError() {
        return this.lastError;
    }

    private Direction facing() {
        BlockState state = this.getBlockState();
        return state.hasProperty(MultiblockControllerBlock.FACING)
                ? state.getValue(MultiblockControllerBlock.FACING)
                : Direction.NORTH;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /**
     * 服务端每 tick 调用。
     */
    public void serverTick() {
        Level world = this.getLevel();
        if (world == null || world.isClientSide) {
            return;
        }

        if (this.checkRequested) {
            this.checkRequested = false;
            if (!this.revalidate()) {
                return;
            }
        }

        if (this.needsValidation) {
            this.needsValidation = false;
            if (!this.revalidate()) {
                return;
            }
        }

        if (!this.formed) {
            if (--this.checkCooldown <= 0) {
                this.checkCooldown = CHECK_INTERVAL;
                this.tryForm();
            }
            return;
        }

        if (--this.validateCooldown <= 0) {
            this.validateCooldown = VALIDATE_INTERVAL;
            if (!this.revalidate()) {
                return;
            }
        }

        this.tickRecipe(world);
    }

    /**
     * 外部通知：结构可能变了，下一 tick 立即复检。
     */
    public void invalidateStructure() {
        this.checkRequested = true;
    }

    /**
     * 方块被移除时调用，断开所有仓室。
     *
     * <p>注意：此时方块正在被替换，不能再调用 {@code setBlock} 改状态，
     * 否则会和移除流程互相覆盖。这里只做内存清理。</p>
     */
    public void onControllerRemoved() {
        for (MultiblockPart part : this.parts) {
            part.unlinkController(this.worldPosition);
        }
        this.parts = List.of();
        this.formed = false;
        this.activeRecipe = null;
        this.activeRecipeId = null;
        this.progress = 0;
        this.totalTicks = 0;
        this.activeParallel = 1;
        this.energyPerTick = 0L;
        this.steamPerTick = 0L;
        this.running = false;
        this.setChanged();
    }

    /**
     * 立即做一次“成型则复检、未成型则尝试成型”。
     *
     * @return 复检后是否仍处于成型状态
     */
    private boolean revalidate() {
        Level world = this.getLevel();
        if (world == null || world.isClientSide) {
            return this.formed;
        }
        MultiblockStructure.MatchResult result = this.definition.structure()
                .match(world, this.worldPosition, this.facing());
        if (!result.formed()) {
            this.lastError = result.message();
            if (this.formed) {
                LOGGER.debug("Multiblock {} at {} broke: {}", this.definition.id(), this.worldPosition, this.lastError);
                this.deform();
            }
            return false;
        }
        this.applyParts(result.parts());
        if (!this.formed) {
            this.formed = true;
            this.setFormedState(true);
            this.lastError = "";
            LOGGER.debug("Multiblock {} at {} formed with {} part(s)",
                    this.definition.id(), this.worldPosition, this.parts.size());
        }
        return true;
    }

    private boolean tryForm() {
        return this.revalidate();
    }

    private void applyParts(List<MultiblockPart> matched) {
        // 每次复检都重新绑定，避免区块重载后持有失效引用。
        for (MultiblockPart part : this.parts) {
            part.unlinkController(this.worldPosition);
        }
        for (MultiblockPart part : matched) {
            part.linkController(this.worldPosition);
        }
        this.parts = List.copyOf(matched);
    }

    private void deform() {
        for (MultiblockPart part : this.parts) {
            part.unlinkController(this.worldPosition);
        }
        this.parts = List.of();
        this.formed = false;
        this.activeRecipe = null;
        this.activeRecipeId = null;
        this.progress = 0;
        this.totalTicks = 0;
        this.overclocks = 0;
        this.activeParallel = 1;
        this.energyPerTick = 0L;
        this.steamPerTick = 0L;
        this.running = false;
        this.setFormedState(false);
        this.setActiveState(false);
        this.setChanged();
    }

    private void setFormedState(boolean formed) {
        Level world = this.getLevel();
        if (world == null || world.isClientSide) {
            return;
        }
        BlockState state = this.getBlockState();
        if (!state.hasProperty(MultiblockControllerBlock.FORMED)) {
            return;
        }
        if (state.getValue(MultiblockControllerBlock.FORMED) == formed) {
            return;
        }
        world.setBlock(this.worldPosition, state.setValue(MultiblockControllerBlock.FORMED, formed),
                Block.UPDATE_CLIENTS);
    }

    private void setActiveState(boolean active) {
        Level world = this.getLevel();
        if (world == null || world.isClientSide) {
            return;
        }
        BlockState state = this.getBlockState();
        if (!state.hasProperty(MultiblockControllerBlock.ACTIVE)) {
            return;
        }
        if (state.getValue(MultiblockControllerBlock.ACTIVE) == active) {
            return;
        }
        world.setBlock(this.worldPosition, state.setValue(MultiblockControllerBlock.ACTIVE, active),
                Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------
    // 配方执行
    // ------------------------------------------------------------------

    private void tickRecipe(Level world) {
        if (this.activeRecipe == null && this.activeRecipeId != null) {
            this.resolvePendingRecipe(world);
        }
        if (this.activeRecipe == null) {
            if (!this.startRecipe(world)) {
                this.setActiveState(false);
                this.running = false;
                return;
            }
        }
        if (!this.consumePower()) {
            this.setActiveState(false);
            this.running = false;
            return;
        }
        this.progress++;
        this.running = true;
        this.setActiveState(true);
        if (this.progress >= this.totalTicks) {
            this.finishRecipe(world);
        }
    }

    private boolean startRecipe(Level world) {
        MachineLevel energyLevel = this.energyLevel();
        if (energyLevel == null) {
            // 没有能源仓（电力）或蒸汽仓：结构成型也不能开工。
            return false;
        }
        // 配方等级门槛：电力由最高等级的能源仓决定，蒸汽固定 ULV/LV。
        VoltageTier allowedTier = this.machineLevel.isSteam()
                ? this.machineLevel.recipeTier()
                : energyLevel.voltage();

        MachineRecipeInput input = this.recipeInput();
        RecipeHolder<MachineRecipe> holder = MachineRecipeLookup.find(world, this.definition.id(),
                allowedTier, input);
        if (holder == null) {
            return false;
        }
        MachineRecipe recipe = holder.value();

        int parallel = Math.min(this.machineLevel.parallel(), recipe.maxCrafts(input));
        if (parallel < 1) {
            return false;
        }
        while (parallel > 1 && !recipe.canFitOutputs(this, parallel)) {
            parallel--;
        }
        if (!recipe.canFitOutputs(this, parallel)) {
            return false;
        }

        int baseTicks = this.definition.resolveTime(recipe);
        double amperage = this.definition.resolveEnergy(recipe);
        // 超频次数由能源仓等级决定，而不是控制器方块等级。
        int countedOverclocks = Overclock.count(energyLevel, recipe.level());
        int countedTicks = Overclock.ticks(baseTicks, countedOverclocks, this.machineLevel);
        long power = Overclock.energyPerTick(amperage, recipe.level(), countedOverclocks);
        long countedEnergyPerTick = this.machineLevel.isSteam() ? 0L : power;
        long countedSteamPerTick = this.machineLevel.isSteam() ? this.machineLevel.steamPerTick(power) : 0L;

        if (!this.canAfford(countedEnergyPerTick, countedSteamPerTick)) {
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
        this.setChanged();
        return true;
    }

    /**
     * 当前可用的“能源等级”。
     *
     * <ul>
     *     <li>电力多方块：取所有 {@code energy_input} 仓室中的最高电压；没有能源仓返回 {@code null}；</li>
     *     <li>蒸汽多方块：必须有至少一个 {@code steam_input} 仓，等级固定 ULV
     *     （低压/高压由控制器决定）。</li>
     * </ul>
     */
    @Nullable
    private MachineLevel energyLevel() {
        if (this.machineLevel.isSteam()) {
            for (MultiblockPart part : this.parts) {
                if (part instanceof MultiblockPartBlockEntity partEntity
                        && partEntity.partType() == MultiblockPartType.STEAM_INPUT
                        && partEntity.chemicalTank() != null) {
                    return this.machineLevel;
                }
            }
            return null;
        }
        VoltageTier best = null;
        for (MultiblockPart part : this.parts) {
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.ENERGY_INPUT
                    && partEntity.energyStorage() != null) {
                VoltageTier tier = partEntity.level().voltage();
                if (best == null || tier.level() > best.level()) {
                    best = tier;
                }
            }
        }
        return best == null ? null : MachineLevel.electric(best);
    }

    private void resolvePendingRecipe(Level world) {
        ResourceLocation recipeId = this.activeRecipeId;
        if (recipeId == null) {
            return;
        }
        var holder = world.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder != null && holder.value() instanceof MachineRecipe recipe) {
            this.activeRecipe = recipe;
            this.activeParallel = Math.max(1, this.activeParallel);
            return;
        }
        // 配方被数据包删掉了：放弃这一批，避免卡进度。
        this.activeRecipeId = null;
        this.progress = 0;
        this.totalTicks = 0;
        this.activeParallel = 1;
    }

    private void finishRecipe(Level world) {
        MachineRecipe recipe = this.activeRecipe;
        if (recipe != null) {
            recipe.produce(this, world.random, this.activeParallel);
        }
        this.activeRecipe = null;
        this.activeRecipeId = null;
        this.progress = 0;
        this.totalTicks = 0;
        this.overclocks = 0;
        this.activeParallel = 1;
        this.energyPerTick = 0L;
        this.steamPerTick = 0L;
        this.running = false;
        this.setActiveState(false);
        this.setChanged();
    }

    private boolean canAfford(long energy, long steam) {
        if (steam > 0L) {
            return this.storedSteam() >= steam;
        }
        return energy <= 0L || this.storedEnergy() >= energy;
    }

    private boolean consumePower() {
        if (this.steamPerTick > 0L) {
            return this.consumeSteam(this.steamPerTick);
        }
        if (this.energyPerTick <= 0L) {
            return true;
        }
        return this.consumeEnergy(this.energyPerTick);
    }

    private long storedEnergy() {
        long total = 0L;
        for (MultiblockPart part : this.parts) {
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.ENERGY_INPUT
                    && partEntity.energyStorage() != null) {
                total += partEntity.energyStorage().getEnergyStored();
            }
        }
        return total;
    }

    private boolean consumeEnergy(long amount) {
        long remaining = amount;
        for (MultiblockPart part : this.parts) {
            if (remaining <= 0L) {
                break;
            }
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.ENERGY_INPUT
                    && partEntity.energyStorage() != null) {
                long stored = partEntity.energyStorage().getEnergyStored();
                long taken = Math.min(stored, remaining);
                if (taken > 0L && partEntity.energyStorage().consume(taken)) {
                    remaining -= taken;
                }
            }
        }
        return remaining <= 0L;
    }

    private long storedSteam() {
        long total = 0L;
        for (MultiblockPart part : this.parts) {
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.STEAM_INPUT
                    && partEntity.chemicalTank() != null) {
                total += partEntity.chemicalTank().getStored();
            }
        }
        return total;
    }

    private boolean consumeSteam(long amount) {
        long remaining = amount;
        for (MultiblockPart part : this.parts) {
            if (remaining <= 0L) {
                break;
            }
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.STEAM_INPUT
                    && partEntity.chemicalTank() != null) {
                long drained = partEntity.chemicalTank().shrinkStack(remaining, Action.EXECUTE);
                remaining -= drained;
            }
        }
        return remaining <= 0L;
    }

    // ------------------------------------------------------------------
    // 输入 / 输出
    // ------------------------------------------------------------------

    private MachineRecipeInput recipeInput() {
        List<MachineItemHandler> itemInputs = new ArrayList<>();
        List<FluidTank> fluidInputs = new ArrayList<>();
        List<IChemicalTank> chemicalInputs = new ArrayList<>();
        for (MultiblockPart part : this.parts) {
            if (!(part instanceof MultiblockPartBlockEntity partEntity)) {
                continue;
            }
            switch (partEntity.partType()) {
                case ITEM_INPUT -> {
                    if (partEntity.items() != null) {
                        itemInputs.add(partEntity.items());
                    }
                }
                case FLUID_INPUT -> {
                    // MEK 逻辑槽：同一仓室既能提供流体也能提供化学品。
                    if (partEntity.fluidTank() != null) {
                        fluidInputs.add(partEntity.fluidTank());
                    }
                    if (partEntity.chemicalTank() != null) {
                        chemicalInputs.add(partEntity.chemicalTank());
                    }
                }
                default -> {
                }
            }
        }
        IItemHandler items = itemInputs.isEmpty() ? EmptyItemHandler.INSTANCE : new CombinedItemHandler(itemInputs);
        IFluidHandler fluids = fluidInputs.isEmpty() ? null : new CombinedFluidHandler(fluidInputs);
        var chemicals = chemicalInputs.isEmpty() ? null : new CombinedChemicalHandler(chemicalInputs);
        return new MachineRecipeInput(items, fluids, chemicals);
    }

    @Override
    public int insertItem(ItemStack stack, boolean simulate) {
        int remaining = stack.getCount();
        for (MultiblockPart part : this.parts) {
            if (remaining <= 0) {
                break;
            }
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.ITEM_OUTPUT
                    && partEntity.items() != null) {
                int inserted = partEntity.items().insertOutput(stack.copyWithCount(remaining), simulate);
                remaining -= inserted;
            }
        }
        return stack.getCount() - remaining;
    }

    @Override
    public long insertFluid(FluidStack stack, boolean simulate) {
        long remaining = stack.getAmount();
        IFluidHandler.FluidAction action = simulate
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE;
        for (MultiblockPart part : this.parts) {
            if (remaining <= 0L) {
                break;
            }
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.FLUID_OUTPUT
                    && partEntity.fluidTank() != null) {
                int filled = partEntity.fluidTank().fill(
                        stack.copyWithAmount((int) Math.min(Integer.MAX_VALUE, remaining)), action);
                remaining -= filled;
            }
        }
        return stack.getAmount() - remaining;
    }

    @Override
    public long insertChemical(ChemicalStack stack, boolean simulate) {
        long remaining = stack.getAmount();
        Action action = simulate ? Action.SIMULATE : Action.EXECUTE;
        for (MultiblockPart part : this.parts) {
            if (remaining <= 0L) {
                break;
            }
            if (part instanceof MultiblockPartBlockEntity partEntity
                    && partEntity.partType() == MultiblockPartType.FLUID_OUTPUT
                    && partEntity.chemicalTank() != null) {
                ChemicalStack leftover = partEntity.chemicalTank().insert(
                        stack.copyWithAmount(remaining), action, AutomationType.INTERNAL);
                remaining -= stack.copyWithAmount(remaining).getAmount() - leftover.getAmount();
            }
        }
        return stack.getAmount() - remaining;
    }

    // ------------------------------------------------------------------
    // 诊断
    // ------------------------------------------------------------------

    /**
     * 右键控制器时把结构状态打印到聊天栏。
     */
    public void sendStatus(Player player) {
        if (this.formed) {
            player.displayClientMessage(Component.translatable("multiblock.meknh_core.status.formed",
                            this.definition.id().toString(),
                            this.progress + "/" + this.totalTicks,
                            this.activeParallel)
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            player.displayClientMessage(Component.translatable("multiblock.meknh_core.status.unformed",
                            this.lastError.isEmpty() ? "-" : this.lastError)
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", this.formed);
        tag.putInt("Progress", this.progress);
        tag.putInt("TotalTicks", this.totalTicks);
        tag.putInt("Overclocks", this.overclocks);
        tag.putInt("ActiveParallel", this.activeParallel);
        tag.putLong("EnergyPerTick", this.energyPerTick);
        tag.putLong("SteamPerTick", this.steamPerTick);
        if (this.activeRecipeId != null) {
            tag.putString("Recipe", this.activeRecipeId.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.progress = tag.getInt("Progress");
        this.totalTicks = tag.getInt("TotalTicks");
        this.overclocks = tag.getInt("Overclocks");
        this.activeParallel = Math.max(1, tag.getInt("ActiveParallel"));
        this.energyPerTick = tag.getLong("EnergyPerTick");
        this.steamPerTick = tag.getLong("SteamPerTick");
        this.activeRecipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        this.formed = false;
        this.needsValidation = true;
        this.checkCooldown = 1;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 多个物品仓的只读/可抽取视图，供 {@link MachineRecipeInput} 统计与扣除原料。
     */
    private static final class CombinedItemHandler implements IItemHandler {

        private final List<MachineItemHandler> handlers;
        private final int[] offsets;

        private CombinedItemHandler(List<MachineItemHandler> handlers) {
            this.handlers = List.copyOf(handlers);
            this.offsets = new int[this.handlers.size()];
            int total = 0;
            for (int index = 0; index < this.handlers.size(); index++) {
                this.offsets[index] = total;
                total += this.handlers.get(index).getSlots();
            }
        }

        private int handlerIndex(int slot) {
            for (int index = this.handlers.size() - 1; index >= 0; index--) {
                if (slot >= this.offsets[index]) {
                    return index;
                }
            }
            return -1;
        }

        @Override
        public int getSlots() {
            int total = 0;
            for (MachineItemHandler handler : this.handlers) {
                total += handler.getSlots();
            }
            return total;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int index = this.handlerIndex(slot);
            return index < 0 ? ItemStack.EMPTY
                    : this.handlers.get(index).getStackInSlot(slot - this.offsets[index]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int index = this.handlerIndex(slot);
            return index < 0 ? ItemStack.EMPTY
                    : this.handlers.get(index).extractItem(slot - this.offsets[index], amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int index = this.handlerIndex(slot);
            return index < 0 ? 0 : this.handlers.get(index).getSlotLimit(slot - this.offsets[index]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    /**
     * 没有任何输入物品仓时给配方匹配用的空视图。
     */
    private enum EmptyItemHandler implements IItemHandler {
        INSTANCE;

        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
