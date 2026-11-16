package c_y.meknh_core.client.jei;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块 JEI 布局注册表：核心只提供默认布局，具体机器按 ID 注册扩展。
 */
public final class MultiblockRecipeLayouts {

    private static final Map<ResourceLocation, MultiblockRecipeLayout> LAYOUTS = new LinkedHashMap<>();

    private MultiblockRecipeLayouts() {
    }

    public static void register(ResourceLocation id, MultiblockRecipeLayout layout) {
        LAYOUTS.put(id, layout);
    }

    @Nullable
    public static MultiblockRecipeLayout get(ResourceLocation id) {
        return LAYOUTS.get(id);
    }
}
