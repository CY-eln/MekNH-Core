package c_y.meknh_core.common.coke;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineItemHandler;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 焦炉 GUI：输入槽（52,30）、输出槽（103,30）、杂酚油罐（18×60，位于 134,13）。
 */
public class CokeOvenMenu extends AbstractContainerMenu {

    public static final int DATA_AMOUNT_LO = 0;
    public static final int DATA_AMOUNT_HI = 1;
    public static final int DATA_CAPACITY_LO = 2;
    public static final int DATA_CAPACITY_HI = 3;
    public static final int DATA_FLUID = 4;
    public static final int DATA_PROGRESS = 5;
    public static final int DATA_TOTAL = 6;
    public static final int DATA_SIZE = 7;

    private static final int PLAYER_INVENTORY_Y = 84;

    @Nullable
    private final CokeOvenControllerBlockEntity oven;
    private final ContainerData data;

    /**
     * 服务端构造。
     */
    public CokeOvenMenu(int windowId, Inventory playerInventory, CokeOvenControllerBlockEntity oven) {
        this(windowId, playerInventory, oven, new OvenData(oven));
    }

    private CokeOvenMenu(int windowId, Inventory playerInventory, @Nullable CokeOvenControllerBlockEntity oven,
                         ContainerData data) {
        super(CokeOvenRegistration.COKE_OVEN_MENU.get(), windowId);
        this.oven = oven;
        this.data = data;
        // 客户端如果暂时找不到焦炉实体（区块未同步等），用空槽占位，槽内容由服务端同步包填充。
        MachineItemHandler items = oven != null
                ? oven.coalItems()
                : new MachineItemHandler(List.of(64), List.of(64), () -> {
                });
        this.addSlot(new SlotItemHandler(items, 0, CokeOvenLayout.INPUT_X, CokeOvenLayout.SLOT_Y));
        this.addSlot(new SlotItemHandler(items, 1, CokeOvenLayout.OUTPUT_X, CokeOvenLayout.SLOT_Y));
        this.addPlayerInventory(playerInventory);
        this.addDataSlots(this.data);
    }

    /**
     * 客户端构造：数据由服务端同步，不能读本地 BE。
     */
    public CokeOvenMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, resolve(playerInventory.player, buf.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    /**
     * 找不到焦炉实体时返回 {@code null}：不能在这里抛异常，否则客户端会直接断开连接。
     */
    @Nullable
    private static CokeOvenControllerBlockEntity resolve(Player player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof CokeOvenControllerBlockEntity oven) {
            return oven;
        }
        MekNHCore.LOGGER.warn("Coke oven menu opened but block entity at {} is {}",
                pos, be == null ? "null" : be.getClass().getName());
        return null;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18,
                        PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, PLAYER_INVENTORY_Y + 3 * 18 + 4));
        }
    }

    public long amount() {
        return (long) this.data.get(DATA_AMOUNT_LO) | ((long) this.data.get(DATA_AMOUNT_HI) << 32);
    }

    public long capacity() {
        return (long) this.data.get(DATA_CAPACITY_LO) | ((long) this.data.get(DATA_CAPACITY_HI) << 32);
    }

    @Nullable
    public FluidStack fluid() {
        Fluid fluid = BuiltInRegistries.FLUID.byId(this.data.get(DATA_FLUID));
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return null;
        }
        return new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, this.amount()));
    }

    public int progress() {
        return this.data.get(DATA_PROGRESS);
    }

    public int totalTicks() {
        return Math.max(1, this.data.get(DATA_TOTAL));
    }

    public float progressRatio() {
        return Math.max(0.0F, Math.min(1.0F, (float) this.progress() / (float) this.totalTicks()));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < 2) {
            if (!this.moveItemStackTo(stack, 2, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.oven == null) {
            return false;
        }
        return player.distanceToSqr(this.oven.getBlockPos().getX() + 0.5,
                this.oven.getBlockPos().getY() + 0.5, this.oven.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    /**
     * 服务端数据：罐内流体与容量。
     */
    private static final class OvenData implements ContainerData {

        private final CokeOvenControllerBlockEntity oven;

        private OvenData(CokeOvenControllerBlockEntity oven) {
            this.oven = oven;
        }

        @Override
        public int get(int index) {
            long amount = this.oven.creosoteTank().getFluidAmount();
            long capacity = this.oven.creosoteTank().getCapacity();
            return switch (index) {
                case DATA_AMOUNT_LO -> (int) amount;
                case DATA_AMOUNT_HI -> (int) (amount >>> 32);
                case DATA_CAPACITY_LO -> (int) capacity;
                case DATA_CAPACITY_HI -> (int) (capacity >>> 32);
                case DATA_FLUID -> this.oven.creosoteTank().isEmpty() ? 0
                        : BuiltInRegistries.FLUID.getId(this.oven.creosoteTank().getFluid().getFluid());
                case DATA_PROGRESS -> this.oven.cokeProgress();
                case DATA_TOTAL -> this.oven.cokeTotalTicks();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }
}
