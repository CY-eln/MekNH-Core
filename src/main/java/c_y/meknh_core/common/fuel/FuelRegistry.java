package c_y.meknh_core.common.fuel;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** data/addon/fuel/*.json 解析出的物品燃烧时间。 */
public final class FuelRegistry {

    private static final Map<ResourceLocation, Integer> ITEM_FUELS = new LinkedHashMap<>();

    private FuelRegistry() {
    }

    public static synchronized void registerItem(ResourceLocation item, int burnTime) {
        ITEM_FUELS.put(item, Math.max(1, burnTime));
    }

    public static int burnTime(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        Integer value = ITEM_FUELS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return value == null ? -1 : value;
    }
}
