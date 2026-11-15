package c_y.meknh_core.common.blast;

import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.api.multiblock.*;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.recipe.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Solid-fuel GT primitive furnace: no energy, steam, fluid or overclock processing. */
public class PrimitiveBlastFurnaceControllerBlockEntity extends MultiblockControllerBlockEntity implements MultiblockControllerGui {
    private final MachineItemHandler items = new MachineItemHandler(List.of(64,64,64), List.of(64,64,64), this::setChanged);
    private int blastProgress, blastTotalTicks;
    private ResourceLocation recipeId;
    private MachineRecipe previousRecipe;
    private boolean workedThisTick;
    private boolean inventoryPacked;

    public PrimitiveBlastFurnaceControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            MultiblockDefinition definition, MachineLevel level) {
        super(type, pos, state, definition, level);
    }
    public MachineItemHandler items() { return items; }
    public int blastProgress() { return blastProgress; }
    public int blastTotalTicks() { return Math.max(1, blastTotalTicks); }
    public BlockPos interiorPosition() { return worldPosition.relative(getBlockState().getValue(MultiblockControllerBlock.FACING).getOpposite()); }
    public boolean formedState() { return getBlockState().getValue(MultiblockControllerBlock.FORMED); }
    public boolean activeState() { return getBlockState().getValue(MultiblockControllerBlock.ACTIVE); }

    /** GT clientTick: formed lava every 20 offset ticks, active large smoke every tick. */
    public void clientTick() {
        if (level == null || !level.isClientSide || !formedState()) return;
        var back = getBlockState().getValue(MultiblockControllerBlock.FACING).getOpposite();
        double x = worldPosition.getX() + .5 + back.getStepX() * .76;
        double y = worldPosition.getY() + .25 + back.getStepY() * .76;
        double z = worldPosition.getZ() + .5 + back.getStepZ() * .76;
        double speed = .2 + .1 * level.random.nextFloat();
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 20L) == 0)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x, y, z, 0, speed * 2, 0);
        if (activeState()) level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x, y, z, 0, speed, 0);
    }

    @Override public void serverTick() {
        workedThisTick = false;
        super.serverTick();
        if (level == null || level.isClientSide) return;
        // The base class returns before tickRecipe on deform; its deform method is private.
        if (!isFormed()) resetProgress();
        if (!workedThisTick) return;
        BlockPos inside = interiorPosition();
        for (var entity : level.getEntities(null, new AABB(inside))) entity.hurt(level.damageSources().lava(), 3F);
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 10L) == 0) {
            BlockState snow = level.getBlockState(inside);
            if (snow.is(Blocks.SNOW_BLOCK)) level.setBlockAndUpdate(inside, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7));
            else if (snow.is(Blocks.SNOW)) {
                int layers = snow.getValue(SnowLayerBlock.LAYERS);
                level.setBlockAndUpdate(inside, layers == 1 ? Blocks.AIR.defaultBlockState() : snow.setValue(SnowLayerBlock.LAYERS, layers - 1));
            }
        }
    }
    private void resetProgress() {
        if (blastProgress != 0 || recipeId != null) setChanged();
        blastProgress = 0;
        recipeId = null;
        previousRecipe = null;
        setActiveState(false);
    }
    @Override protected void tickRecipe(Level world) {
        // Fresh lookup every tick deliberately avoids stale input/reload caches.
        ItemStackHandler remaining = new ItemStackHandler(3);
        for (int i = 0; i < 3; i++) remaining.setStackInSlot(i, items.getStackInSlot(i).copy());
        var holder = MachineRecipeLookup.find(world, definition().id(), VoltageTier.ULV, new MachineRecipeInput(remaining, null, null));
        if (holder == null) { resetProgress(); return; }
        MachineRecipe recipe = holder.value();
        // Reserve inputs cumulatively too: overlapping ingredient predicates cannot double-spend a stack.
        MachineRecipeInput input = new MachineRecipeInput(remaining, null, null);
        for (var ingredient : recipe.inputs()) {
            if (!(ingredient instanceof MachineIngredient.Item item) || !input.removeItems(item.ingredient(), item.count())) {
                resetProgress(); return;
            }
        }
        MachineItemHandler reserved = new MachineItemHandler(List.of(), List.of(64,64,64), () -> {});
        for (int i = 0; i < 3; i++) reserved.setStackInSlot(i, items.getStackInSlot(i + 3).copy());
        // Execute on a shadow inventory, not independent simulate calls competing for the same space.
        for (var product : recipe.outputs()) {
            if (!(product instanceof MachineOutput.ItemProduct item)) { resetProgress(); return; }
            var type = MachineOutput.findItem(item.id());
            if (type == null || reserved.insertOutput(new ItemStack(type, item.count()), false) != item.count()) {
                resetProgress(); return;
            }
        }
        if (!holder.id().equals(recipeId) || previousRecipe != null && previousRecipe != recipe) blastProgress = 0;
        recipeId = holder.id();
        previousRecipe = recipe;
        blastTotalTicks = Math.max(1, recipe.time() > 0 ? recipe.time() : definition().time());
        workedThisTick = true;
        setActiveState(true);
        if (++blastProgress >= blastTotalTicks) {
            // No callbacks can mutate inventory between reservation and commit on this server thread.
            for (int i = 0; i < 3; i++) items.setStackInSlot(i, remaining.getStackInSlot(i));
            for (var product : recipe.outputs()) product.produce(this, world.random);
            blastProgress = 0;
        }
        setChanged();
    }
    /** Called only after WrenchDismantle has captured this BE's complete NBT. */
    public void packInventoryForDismantle() { inventoryPacked = true; }

    @Override public void onControllerRemoved() {
        if (!inventoryPacked && level != null && !level.isClientSide) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
                    items.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        super.onControllerRemoved();
    }

    @Override public int insertItem(ItemStack stack, boolean simulate) { return items.insertOutput(stack, simulate); }
    @Override public long insertFluid(net.neoforged.neoforge.fluids.FluidStack stack, boolean simulate) { return 0; }
    @Override public long insertChemical(mekanism.api.chemical.ChemicalStack stack, boolean simulate) { return 0; }
    @Override public boolean openControllerGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PrimitiveBlastFurnaceMenu(id, inventory, this),
                Component.translatable("block.meknh_core.primitive_blast_furnace")), buf -> buf.writeBlockPos(worldPosition));
        return true;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("BlastItems", items.serializeNBT(registries));
        tag.putInt("BlastProgress", blastProgress);
        tag.putInt("BlastTotalTicks", blastTotalTicks);
        if (recipeId != null) tag.putString("BlastRecipe", recipeId.toString());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("BlastItems")) items.deserializeNBT(registries, tag.getCompound("BlastItems"));
        blastTotalTicks = Math.max(1, tag.getInt("BlastTotalTicks"));
        blastProgress = Math.max(0, Math.min(tag.getInt("BlastProgress"), blastTotalTicks - 1));
        recipeId = ResourceLocation.tryParse(tag.getString("BlastRecipe"));
        previousRecipe = null;
    }
}
