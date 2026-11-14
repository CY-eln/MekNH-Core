package c_y.meknh_core.common.recipe;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 按机器查找可执行的配方。
 *
 * <p>配方从标准 {@link RecipeManager} 读取（目录 {@code data/<命名空间>/recipe/machine/<机器ID>/}，
 * 多方块在 {@code recipe/multiblock/<多方块ID>/}；目录只是组织方式），
 * 匹配条件：</p>
 * <ol>
 *     <li>{@code id} 等于机器 ID，或机器 ID 出现在配方的 {@code extra_type} 中；</li>
 *     <li>配方 {@code level} 不高于机器等级；</li>
 *     <li>配方输入全部满足。</li>
 * </ol>
 *
 * <p>按 {@link RecipeManager} 实例缓存一份「机器 → 配方」索引，{@code /reload} 后会自动失效。</p>
 */
public final class MachineRecipeLookup {

    @Nullable
    private static RecipeManager cachedManager;
    private static Map<ResourceLocation, List<RecipeHolder<MachineRecipe>>> cachedIndex = Map.of();
    private static final Map<Item, RecipeHolder<MachineRecipe>> SMELTING_CACHE = new HashMap<>();

    private MachineRecipeLookup() {
    }

    /**
     * 查找第一份可执行配方。
     *
     * @param level    机器所在世界
     * @param machine  机器 ID
     * @param tier     机器等级（电力电压或 ULV）
     * @param input    当前输入
     * @return 找到的配方（含注册 ID）；没有则为 {@code null}
     */
    @Nullable
    public static RecipeHolder<MachineRecipe> find(Level level, ResourceLocation machine, VoltageTier tier, MachineRecipeInput input) {
        if (level == null || level.isClientSide) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<MachineRecipe>> candidates = index(manager).get(machine);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (RecipeHolder<MachineRecipe> holder : candidates) {
            MachineRecipe recipe = holder.value();
            if (!recipe.matchesLevel(tier)) {
                continue;
            }
            if (recipe.vanilla() != null) {
                RecipeHolder<MachineRecipe> resolved = resolveVanilla(level, recipe, input);
                if (resolved != null) {
                    return resolved;
                }
                continue;
            }
            if (recipe.matches(input, level)) {
                return holder;
            }
        }
        return null;
    }

    /**
     * 由一条 JSON 模板读取原版配方类型（目前支持 minecraft:smelting）。
     */
    @Nullable
    private static RecipeHolder<MachineRecipe> resolveVanilla(Level level, MachineRecipe template, MachineRecipeInput input) {
        if (!"minecraft:smelting".equals(template.vanilla().toString())) {
            return null;
        }
        ItemStack stack = ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack candidate = input.getItem(slot);
            if (!candidate.isEmpty()) {
                stack = candidate;
                break;
            }
        }
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        RecipeHolder<MachineRecipe> cached = SMELTING_CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        RecipeHolder<SmeltingRecipe> smelting = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .orElse(null);
        if (smelting == null) {
            return null;
        }
        ItemStack result = smelting.value().getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }
        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        MachineRecipe resolved = new MachineRecipe(
                template.machine(),
                template.level(),
                template.time() > 0 ? template.time() : 200,
                template.energy() >= 0.0D ? template.energy() : 0.25D,
                List.of(new MachineIngredient.Item(Ingredient.of(item), 1)),
                List.of(new MachineOutput.ItemProduct(resultId, result.getCount(), 1.0D)),
                template.extraTypes()
        );
        ResourceLocation recipeId = MekNHCore.rl("electric_stove/vanilla_smelting/" + BuiltInRegistries.ITEM.getKey(item).getPath());
        RecipeHolder<MachineRecipe> created = new RecipeHolder<>(recipeId, resolved);
        SMELTING_CACHE.put(item, created);
        return created;
    }

    /**
     * 所有属于指定机器的配方（含 {@code extra_type} 指过来的）。
     */
    public static List<RecipeHolder<MachineRecipe>> allFor(RecipeManager manager, ResourceLocation machine) {
        return index(manager).getOrDefault(machine, List.of());
    }

    /**
     * 清空缓存，下一次查找会重建索引。
     */
    public static void invalidate() {
        cachedManager = null;
        cachedIndex = Map.of();
        SMELTING_CACHE.clear();
    }

    private static Map<ResourceLocation, List<RecipeHolder<MachineRecipe>>> index(RecipeManager manager) {
        if (manager == cachedManager) {
            return cachedIndex;
        }
        Map<ResourceLocation, List<RecipeHolder<MachineRecipe>>> index = new HashMap<>();
        for (RecipeHolder<MachineRecipe> holder : manager.<MachineRecipeInput, MachineRecipe>getAllRecipesFor(MachineRecipes.TYPE.get())) {
            MachineRecipe recipe = holder.value();
            index.computeIfAbsent(recipe.machine(), key -> new ArrayList<>()).add(holder);
            for (ResourceLocation extra : recipe.extraTypes()) {
                if (!extra.equals(recipe.machine())) {
                    index.computeIfAbsent(extra, key -> new ArrayList<>()).add(holder);
                }
            }
        }
        cachedManager = manager;
        cachedIndex = index;
        return index;
    }
}
