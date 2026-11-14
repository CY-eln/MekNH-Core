package c_y.meknh_core.common.coke;

import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.api.multiblock.BucketFluidController;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockControllerGui;
import c_y.meknh_core.api.multiblock.MultiblockDefinition;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * 焦炉控制器：在通用多方块控制器上增加一个输入槽、一个输出槽和一个杂酚油输出罐。
 *
 * <p>输入/输出槽和输出罐同时被焦炉仓代理（见 {@code MultiblockPartBlockEntity} 的
 * {@code COKE_OVEN_HATCH}），所以管道/漏斗可以从仓室那一侧交互。</p>
 */
public class CokeOvenControllerBlockEntity extends MultiblockControllerBlockEntity
        implements MultiblockControllerGui, BucketFluidController {

    /**
     * 杂酚油输出罐容量（mB），对齐 GT 的 32 B。
     */
    public static final int TANK_CAPACITY = 32_000;

    private final MachineItemHandler items = new MachineItemHandler(List.of(64), List.of(64), this::setChanged);
    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            CokeOvenControllerBlockEntity.this.setChanged();
        }
    };
    private final IFluidHandler bucketTank = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return CokeOvenControllerBlockEntity.this.tank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return CokeOvenControllerBlockEntity.this.tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return CokeOvenControllerBlockEntity.this.tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return CokeOvenControllerBlockEntity.this.tank.drain(maxDrain, action);
        }
    };

    public CokeOvenControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                         MultiblockDefinition definition, MachineLevel level) {
        super(type, pos, state, definition, level);
    }

    /**
     * 当前炼焦进度（tick）。
     */
    private int cokeProgress;

    /**
     * 当前这一炉的总时长（tick），配方没写 time 时取多方块定义。
     */
    private int cokeTotalTicks;

    /**
     * 当前匹配到的炼焦配方；结构/输入变化后重新匹配。
     */
    @Nullable
    private c_y.meknh_core.common.recipe.MachineRecipe cokeRecipe;

    /**
     * 供 GUI 读取的进度（tick）。
     */
    public int cokeProgress() {
        return this.cokeProgress;
    }

    /**
     * 供 GUI 读取的总时长（tick），至少 1，避免除零。
     */
    public int cokeTotalTicks() {
        if (this.cokeTotalTicks <= 0) {
            this.cokeTotalTicks = Math.max(1, this.definition().time());
        }
        return this.cokeTotalTicks;
    }

    /**
     * 焦炉自己的配方执行：输入槽 0 的煤 → 输出槽 1 的焦炭 + 输出罐的杂酚油。
     *
     * <p>配方时间取多方块 JSON 的 {@code time}（配方 JSON 不写 time）。</p>
     */
    @Override
    protected void tickRecipe(net.minecraft.world.level.Level world) {
        if (!this.isFormed()) {
            this.cokeProgress = 0;
            this.cokeRecipe = null;
            this.setActiveState(false);
            return;
        }
        if (this.cokeRecipe == null) {
            this.cokeRecipe = this.findRecipe(world);
        }
        if (this.cokeRecipe == null
                || !this.cokeRecipe.matches(this.recipeInput(), world)
                || !this.cokeRecipe.canFitOutputs(this)) {
            this.cokeProgress = 0;
            this.setActiveState(false);
            return;
        }
        int total = this.cokeRecipe.time() > 0 ? this.cokeRecipe.time() : this.definition().time();
        this.cokeTotalTicks = Math.max(1, total);
        this.cokeProgress++;
        this.setActiveState(true);
        if (this.cokeProgress < total) {
            return;
        }
        this.cokeProgress = 0;
        this.cokeRecipe.consumeInputs(this.recipeInput());
        for (c_y.meknh_core.common.recipe.MachineOutput.Product product : this.cokeRecipe.outputs()) {
            product.produce(this, world.random);
        }
        this.cokeRecipe = null;
        this.setChanged();
    }

    @Nullable
    private c_y.meknh_core.common.recipe.MachineRecipe findRecipe(net.minecraft.world.level.Level world) {
        var holder = c_y.meknh_core.common.recipe.MachineRecipeLookup.find(world, this.definition().id(),
                c_y.meknh_core.common.data.voltage.VoltageTier.ULV, this.recipeInput());
        return holder == null ? null : holder.value();
    }

    private c_y.meknh_core.common.recipe.MachineRecipeInput recipeInput() {
        return new c_y.meknh_core.common.recipe.MachineRecipeInput(this.items, null, null);
    }

    /**
     * 产物进控制器自己的输出槽（不写仓室）。
     */
    @Override
    public int insertItem(ItemStack stack, boolean simulate) {
        return this.items.insertOutput(stack, simulate);
    }

    /**
     * 产物进控制器自己的杂酚油罐（不写仓室）。
     */
    @Override
    public long insertFluid(FluidStack stack, boolean simulate) {
        return this.tank.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * 输入槽 0 / 输出槽 1。
     */
    public MachineItemHandler coalItems() {
        return this.items;
    }

    /**
     * 杂酚油输出罐。
     */
    public FluidTank creosoteTank() {
        return this.tank;
    }

    @Override
    public IFluidHandler bucketFluidHandler() {
        return this.bucketTank;
    }

    @Override
    public boolean openControllerGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new CokeOvenMenu(id, inventory, this),
                Component.translatable("block.meknh_core.coke_oven")),
                buf -> buf.writeBlockPos(this.worldPosition));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("CokeItems", this.items.serializeNBT(registries));
        tag.put("CokeTank", this.tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("CokeProgress", this.cokeProgress);
        tag.putInt("CokeTotalTicks", this.cokeTotalTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CokeItems")) {
            this.items.deserializeNBT(registries, tag.getCompound("CokeItems"));
        }
        if (tag.contains("CokeTank")) {
            this.tank.readFromNBT(registries, tag.getCompound("CokeTank"));
        }
        this.cokeProgress = tag.getInt("CokeProgress");
        this.cokeTotalTicks = tag.getInt("CokeTotalTicks");
        this.cokeRecipe = null;
    }

    /**
     * 供菜单/屏幕读取的占位物品名。
     */
    public ItemStack icon() {
        return new ItemStack(this.getBlockState().getBlock());
    }
}
