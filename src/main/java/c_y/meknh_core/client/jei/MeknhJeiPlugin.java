package c_y.meknh_core.client.jei;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.common.generator.boiler.BoilerScreen;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.common.recipe.MachineRecipes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/**
 * 让 JEI 识别 GUI 中非物品槽位里的流体/化学品，并显示本模组机器配方。
 */
@JeiPlugin
public final class MeknhJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = MekNHCore.rl("jei");
    private final List<StructureInfoCategory> structureCategories = new ArrayList<>();
    private final List<MachineRecipeCategory> machineCategories = new ArrayList<>();
    private final List<MultiblockRecipeCategory> multiblockCategories = new ArrayList<>();

    @Nullable
    private static IJeiRuntime jeiRuntime;
    private static final Map<ResourceLocation, RecipeType<MachineRecipe>> MACHINE_TYPES = new HashMap<>();
    private static final Map<ResourceLocation, RecipeType<MachineRecipe>> MULTIBLOCK_TYPES = new HashMap<>();

    /**
     * GUI 进度条点击入口：打开单方块机器对应的 JEI 配方页。
     *
     * @return JEI 不可用/没有分类时返回 {@code false}
     */
    public static boolean openMachineRecipes(ResourceLocation machineId) {
        return openCategory(MACHINE_TYPES.get(machineId));
    }

    /**
     * GUI 进度条点击入口：打开多方块对应的 JEI 配方页。
     */
    public static boolean openMultiblockRecipes(ResourceLocation multiblockId) {
        return openCategory(MULTIBLOCK_TYPES.get(multiblockId));
    }

    /** Open recipes producing the displayed material, without binding an inventory slot. */
    public static boolean openItemRecipes(net.minecraft.world.item.ItemStack stack) {
        if (jeiRuntime == null || stack.isEmpty()) return false;
        var focus = jeiRuntime.getJeiHelpers().getFocusFactory().createFocus(
                mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack.copyWithCount(1));
        jeiRuntime.getRecipesGui().show(java.util.List.of(focus));
        return true;
    }

    private static boolean openCategory(@Nullable RecipeType<MachineRecipe> recipeType) {
        if (jeiRuntime == null || recipeType == null) {
            return false;
        }
        jeiRuntime.getRecipesGui().showTypes(List.of(recipeType));
        return true;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    private void registerStructureCategories(IRecipeCategoryRegistration registration) {
        structureCategories.clear();
        var previews = c_y.meknh_core.api.multiblock.MultiblockRegistration.variants().stream()
                .filter(v -> c_y.meknh_core.api.multiblock.MultiblockRegistration.definitions().stream()
                        .anyMatch(d -> d.id().equals(v.definition().id())))
                .map(c_y.meknh_core.client.structure.StructurePreview::new).toList();
        var controllers = previews.stream().map(p -> p.variant().item().get()).collect(java.util.stream.Collectors.toSet());
        var helper = registration.getJeiHelpers().getGuiHelper();
        structureCategories.add(new StructureInfoCategory(helper, null, controllers));
        for (var preview : previews) structureCategories.add(new StructureInfoCategory(helper, preview, controllers));
        for (var category : structureCategories) registration.addRecipeCategories(category);
    }

    private void registerStructureRecipes(IRecipeRegistration registration) {
        // Structure information is registry-backed, even without a level or processing recipes.
        var previews = structureCategories.stream().map(StructureInfoCategory::controllerPreview)
                .filter(java.util.Objects::nonNull).toList();
        for (var category : structureCategories) registration.addRecipes(category.getRecipeType(),
                category.controllerPreview() == null ? previews : List.of(category.controllerPreview()));
    }

    private void registerStructureCatalysts(IRecipeCatalystRegistration registration) {
        // Controller categories contain exactly one preview. Shared parts use native recipe pagination.
        for (var category : structureCategories) if (category.controllerPreview() != null)
            registration.addRecipeCatalysts(category.getRecipeType(), category.controllerPreview().variant().item().get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registerStructureCategories(registration);
        this.machineCategories.clear();
        this.multiblockCategories.clear();
        MACHINE_TYPES.clear();
        MULTIBLOCK_TYPES.clear();
        MultiblockRecipeLayouts.register(MekNHCore.rl("primitive_blast_furnace"),
                new PrimitiveBlastFurnaceRecipeLayout());
        for (var definition : MachineRegistration.definitions()) {
            MachineRecipeCategory category = new MachineRecipeCategory(definition, registration.getJeiHelpers().getGuiHelper());
            this.machineCategories.add(category);
            MACHINE_TYPES.put(definition.id(), category.getRecipeType());
            registration.addRecipeCategories(category);
        }
        for (var definition : c_y.meknh_core.api.multiblock.MultiblockRegistration.definitions()) {
            MultiblockRecipeCategory category = new MultiblockRecipeCategory(definition,
                    registration.getJeiHelpers().getGuiHelper());
            this.multiblockCategories.add(category);
            MULTIBLOCK_TYPES.put(definition.id(), category.getRecipeType());
            registration.addRecipeCategories(category);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registerStructureRecipes(registration);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<RecipeHolder<MachineRecipe>> all = minecraft.level.getRecipeManager()
                .getAllRecipesFor(MachineRecipes.TYPE.get());
        for (MachineRecipeCategory category : this.machineCategories) {
            List<MachineRecipe> recipes = all.stream()
                    .map(RecipeHolder::value)
                    .filter(recipe -> recipe.belongsTo(category.definition().id()))
                    .toList();
            registration.addRecipes(category.getRecipeType(), recipes);
        }
        for (MultiblockRecipeCategory category : this.multiblockCategories) {
            List<MachineRecipe> recipes = all.stream()
                    .map(RecipeHolder::value)
                    .filter(recipe -> recipe.belongsTo(category.definition().id()))
                    .toList();
            registration.addRecipes(category.getRecipeType(), recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registerStructureCatalysts(registration);
        for (MachineRecipeCategory category : this.machineCategories) {
            for (var variant : MachineRegistration.variants()) {
                if (variant.definition().id().equals(category.definition().id())) {
                    registration.addRecipeCatalysts(category.getRecipeType(), variant.item().get());
                }
            }
        }
        for (MultiblockRecipeCategory category : this.multiblockCategories) {
            for (var variant : c_y.meknh_core.api.multiblock.MultiblockRegistration.variants()) {
                if (variant.definition().id().equals(category.definition().id())) {
                    registration.addRecipeCatalysts(category.getRecipeType(), variant.item().get());
                }
            }
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MachineScreen.class, new TankIngredientHandler<>());
        registration.addGuiContainerHandler(BoilerScreen.class, new TankIngredientHandler<>());
        registration.addGuiContainerHandler(c_y.meknh_core.common.coke.CokeOvenScreen.class,
                new TankIngredientHandler<>());
    }

    private static final class TankIngredientHandler<T extends AbstractContainerScreen<?>>
            implements IGuiContainerHandler<T> {
        @Override
        public List<Rect2i> getGuiExtraAreas(T screen) {
            if (screen instanceof MachineScreen machine) {
                return machine.jeiExtraAreas();
            }
            if (screen instanceof BoilerScreen boiler) {
                return boiler.jeiExtraAreas();
            }
            return List.of();
        }

        @Override
        public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                IClickableIngredientFactory factory, T screen, double mouseX, double mouseY) {
            if (screen instanceof MachineScreen machine) {
                return machine.jeiTankIngredient(factory, mouseX, mouseY);
            }
            if (screen instanceof BoilerScreen boiler) {
                return boiler.jeiTankIngredient(factory, mouseX, mouseY);
            }
            if (screen instanceof c_y.meknh_core.common.coke.CokeOvenScreen oven) {
                return oven.jeiTankIngredient(factory, mouseX, mouseY);
            }
            return Optional.empty();
        }
    }
}
