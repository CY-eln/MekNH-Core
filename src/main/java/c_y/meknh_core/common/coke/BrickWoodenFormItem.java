package c_y.meknh_core.common.coke;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 木制砖模具：参与压缩焦黏土合成时不消耗，保留在合成格中。
 */
public final class BrickWoodenFormItem extends Item {

    public BrickWoodenFormItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return stack.copy();
    }
}
