package c_y.meknh_core.client.jei;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.recipe.MachineIngredient;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.decor.description.MachineNames;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 一台机器的 JEI 配方分类：左侧输入、右侧输出，左下角显示耗时/总 FE/功耗。
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 92;

    private final MachineDefinition definition;
    private final RecipeType<MachineRecipe> recipeType;
    private final IDrawable background;
    private final IDrawable icon;

    public MachineRecipeCategory(MachineDefinition definition, IGuiHelper guiHelper) {
        this.definition = definition;
        this.recipeType = RecipeType.create(MekNHCore.MODID, "machine_" + definition.id().getPath(), MachineRecipe.class);
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        ItemStack iconStack = MachineRegistration.variants().stream()
                .filter(variant -> variant.definition().id().equals(definition.id()))
                .findFirst()
                .map(variant -> new ItemStack(variant.item().get()))
                .orElse(ItemStack.EMPTY);
        this.icon = guiHelper.createDrawableItemStack(iconStack);
    }

    public MachineDefinition definition() {
        return this.definition;
    }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return this.recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(MachineNames.machineKey(this.definition.id()));
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        int index = 0;
        for (MachineIngredient ingredient : recipe.inputs()) {
            int x = 8 + (index % 3) * 20;
            int y = 8 + (index / 3) * 20;
            addIngredient(builder.addInputSlot(x, y), ingredient);
            index++;
        }
        index = 0;
        for (MachineOutput.Product product : recipe.outputs()) {
            int x = 98 + (index % 3) * 20;
            int y = 8 + (index / 3) * 20;
            addProduct(builder.addOutputSlot(x, y), product);
            index++;
        }
    }

    private static void addIngredient(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, MachineIngredient ingredient) {
        if (ingredient instanceof MachineIngredient.Item item) {
            slot.addIngredients(item.ingredient());
            if (item.count() > 1) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal("x" + item.count())));
            }
        } else if (ingredient instanceof MachineIngredient.Fluid fluid) {
            Fluid value = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(fluid.fluid()));
            if (value != null) {
                slot.addFluidStack(value, fluid.amount()).setFluidRenderer(fluid.amount(), true, 16, 16);
            }
        } else if (ingredient instanceof MachineIngredient.Gas gas) {
            Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.tryParse(gas.gas()));
            if (chemical != null) {
                slot.addIngredient(MekanismJEI.TYPE_CHEMICAL, new ChemicalStack(chemical, gas.amount()));
            }
        }
    }

    private static void addProduct(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, MachineOutput.Product product) {
        if (product instanceof MachineOutput.ItemProduct item) {
            Item value = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item.id());
            if (value != null) {
                slot.addItemStack(new ItemStack(value, item.count()));
            }
            if (item.chance() < 1.0D) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal(
                        Math.round(item.chance() * 100.0D) + "%")));
            }
        } else if (product instanceof MachineOutput.FluidProduct fluid) {
            Fluid value = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(fluid.fluid()));
            if (value != null) {
                slot.addFluidStack(value, fluid.amount()).setFluidRenderer(fluid.amount(), true, 16, 16);
            }
        } else if (product instanceof MachineOutput.GasProduct gas) {
            Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.tryParse(gas.gas()));
            if (chemical != null) {
                slot.addIngredient(MekanismJEI.TYPE_CHEMICAL, new ChemicalStack(chemical, gas.amount()));
            }
        }
    }

    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        int time = recipe.time() > 0 ? recipe.time() : this.definition.time();
        double energy = recipe.energy() >= 0.0D ? recipe.energy() : this.definition.energy();
        VoltageTier tier = recipe.level();
        long totalEu = Math.round(energy * tier.voltage() * time);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int x = 6;
        int y = 58;
        graphics.drawString(font, Component.translatable("jei.meknh_core.time", seconds(time)), x, y, 0x404040, false);
        graphics.drawString(font, Component.translatable("jei.meknh_core.total", String.format(Locale.ROOT, "%,d", totalEu)),
                x, y + 11, 0x404040, false);
        graphics.drawString(font, Component.translatable("jei.meknh_core.power",
                        formatAmps(energy), tier.getSerializedName().toUpperCase(Locale.ROOT)),
                x, y + 22, 0x404040, false);
    }

    private static String seconds(int ticks) {
        double value = ticks / 20.0D;
        if (value == Math.rint(value)) {
            return (long) value + "秒";
        }
        return String.format(Locale.ROOT, "%.1f秒", value);
    }

    private static String formatAmps(double amps) {
        return BigDecimal.valueOf(amps).stripTrailingZeros().toPlainString();
    }
}
