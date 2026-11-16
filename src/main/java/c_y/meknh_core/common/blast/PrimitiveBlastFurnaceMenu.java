package c_y.meknh_core.common.blast;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineItemHandler;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class PrimitiveBlastFurnaceMenu extends AbstractContainerMenu {
    public static final int DATA_PROGRESS_LO = 0, DATA_PROGRESS_HI = 1, DATA_TOTAL_LO = 2, DATA_TOTAL_HI = 3, DATA_SIZE = 4;
    private final BlockPos pos;
    @Nullable private final PrimitiveBlastFurnaceControllerBlockEntity furnace;
    private final ContainerData data;
    public PrimitiveBlastFurnaceMenu(int id, Inventory inventory, PrimitiveBlastFurnaceControllerBlockEntity furnace) {
        this(id, inventory, furnace.getBlockPos(), furnace, new FurnaceData(furnace));
    }
    public PrimitiveBlastFurnaceMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }
    private PrimitiveBlastFurnaceMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, resolve(inventory.player, pos), new SimpleContainerData(DATA_SIZE));
    }
    private PrimitiveBlastFurnaceMenu(int id, Inventory inventory, BlockPos pos,
            @Nullable PrimitiveBlastFurnaceControllerBlockEntity furnace, ContainerData data) {
        super(PrimitiveBlastFurnaceRegistration.PRIMITIVE_BLAST_FURNACE_MENU.get(), id);
        this.pos = pos.immutable();
        this.furnace = furnace;
        this.data = data;
        MachineItemHandler items = furnace == null ? new MachineItemHandler(List.of(64,64,64), List.of(64,64,64), () -> {}) : furnace.items();
        for (int i = 0; i < 3; i++) addSlot(new SlotItemHandler(items, i,
                PrimitiveBlastFurnaceLayout.INPUT_X + i * PrimitiveBlastFurnaceLayout.SLOT_STEP,
                PrimitiveBlastFurnaceLayout.INPUT_Y));
        for (int i = 0; i < 3; i++) addSlot(new SlotItemHandler(items, i + 3,
                PrimitiveBlastFurnaceLayout.OUTPUT_X + i * PrimitiveBlastFurnaceLayout.SLOT_STEP,
                PrimitiveBlastFurnaceLayout.OUTPUT_Y));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, PrimitiveBlastFurnaceLayout.PLAYER_INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }
    @Nullable private static PrimitiveBlastFurnaceControllerBlockEntity resolve(Player player, BlockPos pos) {
        var be = player.level().getBlockEntity(pos);
        if (be instanceof PrimitiveBlastFurnaceControllerBlockEntity furnace) return furnace;
        MekNHCore.LOGGER.warn("Primitive blast furnace menu at {} has missing/wrong BE: {}; using synchronized placeholder", pos, be);
        return null;
    }
    public BlockPos blockPos() { return pos; }
    @Nullable public PrimitiveBlastFurnaceControllerBlockEntity furnace() { return furnace; }
    public int progress() { return join(data.get(DATA_PROGRESS_LO), data.get(DATA_PROGRESS_HI)); }
    public int totalTicks() { return Math.max(1, join(data.get(DATA_TOTAL_LO), data.get(DATA_TOTAL_HI))); }
    public double progressRatio() { return Math.clamp((double) progress() / totalTicks(), 0D, 1D); }
    public static int join(int low, int high) { return BlastProgressSync.join(low, high); }
    @Override public boolean stillValid(Player player) {
        if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return false;
        return player.level().isClientSide || furnace != null && !furnace.isRemoved() && player.level().getBlockEntity(pos) == furnace;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 6) {
            if (!moveItemStackTo(stack, 6, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 3, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
    /** Server getter-only view: menu packets cannot modify BE progress. */
    private record FurnaceData(PrimitiveBlastFurnaceControllerBlockEntity furnace) implements ContainerData {
        @Override public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS_LO -> BlastProgressSync.low(furnace.blastProgress());
                case DATA_PROGRESS_HI -> BlastProgressSync.high(furnace.blastProgress());
                case DATA_TOTAL_LO -> BlastProgressSync.low(furnace.blastTotalTicks());
                case DATA_TOTAL_HI -> BlastProgressSync.high(furnace.blastTotalTicks());
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { }
        @Override public int getCount() { return DATA_SIZE; }
    }
}
