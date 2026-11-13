package c_y.meknh_core.client.jei;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.common.generator.boiler.BoilerScreen;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.common.recipe.MachineRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 让 JEI 识别 GUI 中非物品槽位里的流体/化学品，并显示本模组机器配方。
 */
@JeiPlugin
public final class MeknhJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = MekNHCore.rl("jei");
    private final List<MachineRecipeCategory> machineCategories = new ArrayList<>();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        this.machineCategories.clear();
        for (var definition : MachineRegistration.definitions()) {
            MachineRecipeCategory category = new MachineRecipeCategory(definition, registration.getJeiHelpers().getGuiHelper());
            this.machineCategories.add(category);
            registration.addRecipeCategories(category);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
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
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineRecipeCategory category : this.machineCategories) {
            for (var variant : MachineRegistration.variants()) {
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
    }

    private static final class TankIngredientHandler<T extends AbstractContainerScreen<?>>
            implements IGuiContainerHandler<T> {
        @Override
        public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                IClickableIngredientFactory factory, T screen, double mouseX, double mouseY) {
            if (screen instanceof MachineScreen machine) {
                return machine.jeiTankIngredient(factory, mouseX, mouseY);
            }
            if (screen instanceof BoilerScreen boiler) {
                return boiler.jeiTankIngredient(factory, mouseX, mouseY);
            }
            return Optional.empty();
        }
    }
}
