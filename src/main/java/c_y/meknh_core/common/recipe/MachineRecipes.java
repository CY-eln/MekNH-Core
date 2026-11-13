package c_y.meknh_core.common.recipe;

import c_y.meknh_core.MekNHCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 机器配方的注册入口：配方类型 {@code meknh_core:machine} 与对应序列化器。
 *
 * <p>配方 JSON 的 {@code "type"} 写 {@code "meknh_core:machine"}。</p>
 */
public final class MachineRecipes {

    /**
     * 配方类型注册名。
     */
    public static final String TYPE_NAME = "machine";

    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MekNHCore.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MekNHCore.MODID);

    /**
     * {@code meknh_core:machine} 配方类型。
     */
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> TYPE = RECIPE_TYPES.register(
            TYPE_NAME,
            () -> new RecipeType<MachineRecipe>() {
                @Override
                public String toString() {
                    return MekNHCore.MODID + ":" + TYPE_NAME;
                }
            }
    );

    /**
     * {@code meknh_core:machine} 配方序列化器。
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> SERIALIZER =
            RECIPE_SERIALIZERS.register(TYPE_NAME, MachineRecipeSerializer::new);

    private MachineRecipes() {
    }

    /**
     * 由主模组类在构造阶段调用。
     */
    public static void initialize(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
