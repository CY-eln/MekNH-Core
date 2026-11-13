package c_y.meknh_core.api.machine;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 机器的物品槽：前 {@code inputSlots} 个是输入（可插不可抽），其余是输出（可抽不可插）。
 *
 * <p>每个槽位的堆叠上限来自机器 JSON 的 {@code stack_limit}，
 * 不同槽位组可以有不同的上限。</p>
 */
public final class MachineItemHandler extends ItemStackHandler {

    private final int inputSlots;
    private final int[] limits;
    private final Runnable onChanged;

    public MachineItemHandler(List<Integer> inputLimits, List<Integer> outputLimits, Runnable onChanged) {
        super(inputLimits.size() + outputLimits.size());
        this.inputSlots = inputLimits.size();
        this.limits = new int[inputLimits.size() + outputLimits.size()];
        for (int slot = 0; slot < inputLimits.size(); slot++) {
            this.limits[slot] = Math.max(1, inputLimits.get(slot));
        }
        for (int slot = 0; slot < outputLimits.size(); slot++) {
            this.limits[this.inputSlots + slot] = Math.max(1, outputLimits.get(slot));
        }
        this.onChanged = onChanged;
    }

    /**
     * 输入槽数量。
     */
    public int inputSlots() {
        return this.inputSlots;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < this.inputSlots;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < this.limits.length ? this.limits[slot] : 64;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return this.getSlotLimit(slot);
    }

    /**
     * 对外（管道/漏斗）的能力视图：只允许往输入槽插入、只允许从输出槽抽取。
     *
     * <p>内部处理器本身允许抽取输入槽，这样玩家在 GUI 里能把自己放进去的物品拿回来；
     * 自动化靠这个包装视图限制。</p>
     */
    public net.neoforged.neoforge.items.IItemHandler externalView() {
        if (this.externalView == null) {
            this.externalView = new ExternalView(this);
        }
        return this.externalView;
    }

    private ExternalView externalView;

    /**
     * 某个面的视图：只开插入（INPUT）、只开抽取（OUTPUT）或两者（BOTH）。
     */
    public net.neoforged.neoforge.items.IItemHandler directionalView(boolean allowInsert, boolean allowExtract) {
        return new DirectionalView(this.externalView(), allowInsert, allowExtract);
    }

    private record DirectionalView(net.neoforged.neoforge.items.IItemHandler delegate,
                                   boolean allowInsert, boolean allowExtract)
            implements net.neoforged.neoforge.items.IItemHandler {

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
            return this.allowInsert ? this.delegate.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.allowExtract ? this.delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.allowInsert && this.delegate.isItemValid(slot, stack);
        }
    }

    /**
     * 把所有槽位清空并重置堆叠上限（用于反序列化后保持配置一致）。
     */
    public void resetContents() {
        for (int slot = 0; slot < this.getSlots(); slot++) {
            this.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    /**
     * 往输出槽插入产物。
     *
     * <p>不能直接用 {@link #insertItem}：NeoForge 的 {@code ItemStackHandler#insertItem}
     * 会先检查 {@link #isItemValid}，而本类只允许输入槽通过校验，输出槽会被直接拒绝，
     * 导致机器永远「输出放不下」而不开工。这里改为直接操作输出槽。</p>
     *
     * @return 实际插入的数量
     */
    public int insertOutput(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        int remaining = stack.getCount();
        for (int slot = this.inputSlots; slot < this.getSlots() && remaining > 0; slot++) {
            ItemStack existing = this.getStackInSlot(slot);
            int limit = this.getSlotLimit(slot);
            if (!existing.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(stack, existing)) {
                    continue;
                }
                limit -= existing.getCount();
            }
            if (limit <= 0) {
                continue;
            }
            int moved = Math.min(limit, remaining);
            if (!simulate) {
                ItemStack updated = existing.isEmpty() ? stack.copyWithCount(moved) : existing.copy();
                if (!existing.isEmpty()) {
                    updated.grow(moved);
                }
                this.setStackInSlot(slot, updated);
            }
            remaining -= moved;
        }
        return stack.getCount() - remaining;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (this.onChanged != null) {
            this.onChanged.run();
        }
    }

    /**
     * 自动化视图：输入槽可插不可抽，输出槽可抽不可插。
     */
    private static final class ExternalView implements net.neoforged.neoforge.items.IItemHandler {

        private final MachineItemHandler handler;

        private ExternalView(MachineItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public int getSlots() {
            return this.handler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= 0 && slot < this.handler.inputSlots ? this.handler.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= this.handler.inputSlots ? this.handler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.handler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.handler.isItemValid(slot, stack);
        }
    }
}
