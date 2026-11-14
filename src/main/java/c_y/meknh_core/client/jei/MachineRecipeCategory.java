package c_y.meknh_core.client.jei;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.layout.SteamGuiLayout;
import c_y.meknh_core.common.gui.render.GuiDraw;
import c_y.meknh_core.common.recipe.MachineIngredient;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.decor.description.MachineNames;
import java.math.BigDecimal;
import java.util.Locale;
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
import net.minecraft.world.item.ItemStack;

/**
 * 单方块机器的 JEI 配方页：复刻机器 GUI 的机器区域（176 宽、高 92，不含玩家背包），
 * 槽位坐标、进度条位置与游戏内完全一致，左下保留耗时/总 FE/功耗三行提示。
 *
 * <p>布局数字全部来自 {@link SteamGuiLayout}（本模组机器统一用蒸汽布局）：
 * 输入/输出槽在 {@code SLOT_Y=37}，进度条在 {@code PROGRESS_Y=41}；
 * 提取机的流体/化学品输出画在输出槽右侧的 18×58 竖罐里。</p>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 66;

    private static final int SLOT_SIZE = SteamGuiLayout.SLOT_SIZE;
    private static final int SLOT_BG_Y = 6;
    private static final int PROGRESS_Y = 10;

    private static final int TANK_Y = 4;
    private static final int TANK_WIDTH = 18;
    private static final int TANK_HEIGHT = 58;
    private static final int TANK_INNER_WIDTH = 16;
    private static final int TANK_INNER_HEIGHT = 56;

    private final MachineDefinition definition;
    private final RecipeType<MachineRecipe> recipeType;
    private final IDrawable background;
    private final IDrawable icon;
    private final boolean hasTank;

    public MachineRecipeCategory(MachineDefinition definition, IGuiHelper guiHelper) {
        this.definition = definition;
        this.recipeType = RecipeType.create(MekNHCore.MODID, "machine_" + definition.id().getPath(), MachineRecipe.class);
        this.hasTank = definition.fluidOutputCapacity() > 0L || definition.chemicalOutputCapacity() > 0L
                || definition.id().equals(MekNHCore.rl("extractor"));
        this.background = new GuiBackground(definition, this.hasTank);
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
        int inputs = this.definition.itemInputSlots();
        int outputs = this.definition.itemOutputSlots();
        int index = 0;
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.Item) {
                int x = SteamGuiLayout.inputX(inputs, outputs, Math.min(index, Math.max(0, inputs - 1)));
                var slot = builder.addInputSlot(x + 1, SLOT_BG_Y + 1);
                MachineRecipeCategory.addIngredient(slot, ingredient);
                index++;
            } else if (this.hasTank) {
                var slot = builder.addInputSlot(this.tankX(inputs, outputs) + 1, TANK_Y + 1);
                MachineRecipeCategory.addIngredient(slot, ingredient);
                if (ingredient instanceof MachineIngredient.Fluid fluid) {
                    slot.setFluidRenderer(Math.max(1L, fluid.amount()), false, TANK_INNER_WIDTH,
                            TANK_INNER_HEIGHT, mezz.jei.api.gui.drawable.TilingDirection.UP_RIGHT);
                }
            }
        }
        index = 0;
        for (MachineOutput.Product product : recipe.outputs()) {
            if (product instanceof MachineOutput.ItemProduct) {
                int x = SteamGuiLayout.outputX(inputs, outputs, Math.min(index, Math.max(0, outputs - 1)));
                var slot = builder.addOutputSlot(x + 1, SLOT_BG_Y + 1);
                MachineRecipeCategory.addProduct(slot, product);
                index++;
            } else if (this.hasTank) {
                var slot = builder.addOutputSlot(this.tankX(inputs, outputs) + 1, TANK_Y + 1);
                MachineRecipeCategory.addProduct(slot, product);
                if (product instanceof MachineOutput.FluidProduct fluid) {
                    slot.setFluidRenderer(Math.max(1L, fluid.amount()), false, TANK_INNER_WIDTH,
                            TANK_INNER_HEIGHT, mezz.jei.api.gui.drawable.TilingDirection.UP_RIGHT);
                }
            }
        }
    }

    private int tankX(int inputs, int outputs) {
        return SteamGuiLayout.outputX(inputs, outputs, Math.max(0, outputs - 1)) + SLOT_SIZE + SteamGuiLayout.GAP;
    }

    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        int inputs = this.definition.itemInputSlots();
        int outputs = this.definition.itemOutputSlots();
        AnimatedProgressBar.INSTANCE.draw(graphics, SteamGuiLayout.progressX(inputs, outputs), PROGRESS_Y);
        if (this.hasTank) {
            graphics.blit(MachineGuiTextures.GAUGE, this.tankX(inputs, outputs) + 1, TANK_Y, 0, 0,
                    TANK_INNER_WIDTH, TANK_HEIGHT, TANK_INNER_WIDTH, TANK_HEIGHT);
        }
        int time = recipe.time() > 0 ? recipe.time() : this.definition.resolveTime(recipe);
        double energy = recipe.energy() >= 0.0D ? recipe.energy() : this.definition.resolveEnergy(recipe);
        VoltageTier tier = recipe.level();
        long totalEu = Math.round(energy * tier.voltage() * time);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        // 左下三行提示：耗时 / 总 FE / 功耗，和旧版一致，蒸汽机器也显示（按 ULV 计）。
        graphics.drawString(font, Component.translatable("jei.meknh_core.time", seconds(time)),
                6, 32, 0x404040, false);
        graphics.drawString(font, Component.translatable("jei.meknh_core.total",
                String.format(Locale.ROOT, "%,d", totalEu)), 6, 41, 0x404040, false);
        graphics.drawString(font, Component.translatable("jei.meknh_core.power",
                formatAmps(energy), tier.getSerializedName().toUpperCase(Locale.ROOT)), 6, 50, 0x404040, false);
    }

    static void addIngredient(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, MachineIngredient ingredient) {
        if (ingredient instanceof MachineIngredient.Item item) {
            slot.addIngredients(item.ingredient());
            if (item.count() > 1) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal("x" + item.count())));
            }
        } else if (ingredient instanceof MachineIngredient.Fluid fluid) {
            net.minecraft.world.level.material.Fluid value = net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .get(ResourceLocation.tryParse(fluid.fluid()));
            if (value != null) {
                slot.addFluidStack(value, fluid.amount());
            }
        } else if (ingredient instanceof MachineIngredient.Gas gas) {
            mekanism.api.chemical.Chemical chemical = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY
                    .get(ResourceLocation.tryParse(gas.gas()));
            if (chemical != null) {
                slot.addIngredient(mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL,
                        new mekanism.api.chemical.ChemicalStack(chemical, gas.amount()));
            }
        }
    }

    static void addProduct(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, MachineOutput.Product product) {
        if (product instanceof MachineOutput.ItemProduct item) {
            net.minecraft.world.item.Item value = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item.id());
            if (value != null) {
                slot.addItemStack(new ItemStack(value, item.count()));
            }
            if (item.chance() < 1.0D) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal(
                        Math.round(item.chance() * 100.0D) + "%")));
            }
        } else if (product instanceof MachineOutput.FluidProduct fluid) {
            net.minecraft.world.level.material.Fluid value = net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .get(ResourceLocation.tryParse(fluid.fluid()));
            if (value != null) {
                slot.addFluidStack(value, fluid.amount());
            }
        } else if (product instanceof MachineOutput.GasProduct gas) {
            mekanism.api.chemical.Chemical chemical = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY
                    .get(ResourceLocation.tryParse(gas.gas()));
            if (chemical != null) {
                slot.addIngredient(mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL,
                        new mekanism.api.chemical.ChemicalStack(chemical, gas.amount()));
            }
        }
    }

    static String seconds(int ticks) {
        double value = ticks / 20.0D;
        if (value == Math.rint(value)) {
            return (long) value + "秒";
        }
        return String.format(Locale.ROOT, "%.1f秒", value);
    }

    private static String formatAmps(double amps) {
        return BigDecimal.valueOf(amps).stripTrailingZeros().toPlainString();
    }

    /**
     * 复刻机器 GUI 的机器区域：底板九宫格 + 提取机的输出竖罐（output.png 纵向九宫格）。
     */
    private static final class GuiBackground implements IDrawable {

        private final MachineDefinition definition;
        private final boolean hasTank;

        private GuiBackground(MachineDefinition definition, boolean hasTank) {
            this.definition = definition;
            this.hasTank = hasTank;
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
        public void draw(GuiGraphics graphics, int x, int y) {
            GuiDraw.nineSlice(graphics, MachineGuiTextures.BASE, x, y, WIDTH, HEIGHT, 4, 256, 256);
            // 槽位底图直接画在背景上，和游戏内一样只保留 1px 彩色边框。
            int inputs = this.definition.itemInputSlots();
            int outputs = this.definition.itemOutputSlots();
            for (int i = 0; i < inputs; i++) {
                GuiDraw.nineSlice(graphics, MachineGuiTextures.SLOT_INPUT,
                        x + SteamGuiLayout.inputX(inputs, outputs, i), y + SLOT_BG_Y,
                        SLOT_SIZE, SLOT_SIZE, 1, SLOT_SIZE, SLOT_SIZE);
            }
            for (int i = 0; i < outputs; i++) {
                GuiDraw.nineSlice(graphics, MachineGuiTextures.SLOT_OUTPUT,
                        x + SteamGuiLayout.outputX(inputs, outputs, i), y + SLOT_BG_Y,
                        SLOT_SIZE, SLOT_SIZE, 1, SLOT_SIZE, SLOT_SIZE);
            }
            if (!this.hasTank) {
                return;
            }
            int tankX = SteamGuiLayout.outputX(inputs, outputs, Math.max(0, outputs - 1))
                    + SLOT_SIZE + SteamGuiLayout.GAP;
            GuiDraw.nineSliceXY(graphics, MachineGuiTextures.SLOT_OUTPUT, x + tankX, y + TANK_Y,
                    TANK_WIDTH, TANK_HEIGHT, 1, 1, 18, 18, 0, 0, 18, 18);
        }
    }

    /**
     * 动态进度条：Mek {@code gui/progress/bar.png}（25×27，三行 25×9）。
     */
    private static final class AnimatedProgressBar implements IDrawable {

        private static final AnimatedProgressBar INSTANCE = new AnimatedProgressBar();
        private static final long CYCLE_MS = 1200L;
        private static final int WIDTH = SteamGuiLayout.PROGRESS_WIDTH;
        private static final int HEIGHT = SteamGuiLayout.PROGRESS_HEIGHT;

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            ResourceLocation texture = MachineGuiTextures.PROGRESS_BAR;
            int textureHeight = HEIGHT * 3;
            graphics.blit(texture, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, textureHeight);
            int inner = WIDTH - 2;
            int filled = (int) ((System.currentTimeMillis() % CYCLE_MS) * inner / CYCLE_MS);
            if (filled > 0) {
                graphics.blit(texture, x + 1, y, 1, HEIGHT, filled, HEIGHT, WIDTH, textureHeight);
            }
        }
    }
}
