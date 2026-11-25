package c_y.meknh_core.common.fuel;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** 燃烧时间来自 data/addon/fuel/*.json 的普通物品。 */
public class FueledItem extends Item {

    public FueledItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> type) {
        int burn = FuelRegistry.burnTime(stack);
        return burn > 0 ? burn : super.getBurnTime(stack, type);
    }
}
