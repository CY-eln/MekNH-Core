package c_y.meknh_core.common.machine.config;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 只允许插入的物品能力视图。
 *
 * <p>用于「槽位是燃料/原料输入、但机器没有物品输出」的方块（如锅炉）。
 * 直接暴露原 {@link net.neoforged.neoforge.items.ItemStackHandler} 会让漏斗/管道
 * 把正在使用的燃料抽走，违反「不能自动弹出的槽位就不该被抽取」这条规则。</p>
 */
public final class InsertOnlyItemHandler implements IItemHandler {

    private final IItemHandler delegate;

    public InsertOnlyItemHandler(IItemHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getSlots() {
        return this.delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return this.delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.delegate.isItemValid(slot, stack);
    }
}
