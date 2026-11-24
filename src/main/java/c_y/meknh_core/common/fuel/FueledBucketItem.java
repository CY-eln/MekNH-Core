package c_y.meknh_core.common.fuel;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** 燃烧时间来自 data/addon/fuel/*.json 的流体桶。 */
public class FueledBucketItem extends BucketItem {

    public FueledBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> type) {
        int burn = FuelRegistry.burnTime(stack);
        return burn > 0 ? burn : super.getBurnTime(stack, type);
    }
}
