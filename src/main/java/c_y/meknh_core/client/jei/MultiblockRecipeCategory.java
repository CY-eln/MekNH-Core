package c_y.meknh_core.client.jei;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockDefinition;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.common.coke.CokeOvenLayout;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import c_y.meknh_core.common.recipe.MachineIngredient;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
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
 * 多方块的 JEI 配方页：直接复刻该多方块的真实 GUI（底板、槽位、储罐、进度条），
 * 再把配方的物品放到对应槽位、流体放进储罐里。
 *
 * <p>目前只有焦炉有 GUI，坐标全部取 {@link CokeOvenLayout}，和游戏内像素级一致：
 * 输入槽 {@code 46,34}、输出槽 {@code 112,34}、储罐 {@code 134,13}（18×60）、
 * 进度条 {@code 72,39}（32×8）。</p>
 */
public final class MultiblockRecipeCategory implements IRecipeCategory<MachineRecipe> {

    private static final int WIDTH = CokeOvenLayout.PANEL_WIDTH;
    private static final int HEIGHT = 76;

    private static final int ROW_STEP = 20;

    private static final int INPUT_X = CokeOvenLayout.INPUT_BG_X;
    private static final int OUTPUT_X = CokeOvenLayout.OUTPUT_BG_X;
    private static final int SLOT_Y = CokeOvenLayout.SLOT_BG_Y;

    private static final int TANK_X = 134;
    private static final int TANK_Y = 13;
    private static final int TANK_WIDTH = 18;
    private static final int TANK_HEIGHT = 58;
    private static final int TANK_INNER_WIDTH = 16;
    private static final int TANK_INNER_HEIGHT = 56;

    private static final int PROGRESS_X = CokeOvenLayout.PROGRESS_X;
    private static final int PROGRESS_Y = CokeOvenLayout.PROGRESS_Y;

    private final MultiblockDefinition definition;
    private final RecipeType<MachineRecipe> recipeType;
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable inputSlot;
    private final IDrawable outputSlot;

    public MultiblockRecipeCategory(MultiblockDefinition definition, IGuiHelper guiHelper) {
        this.definition = definition;
        this.recipeType = RecipeType.create(MekNHCore.MODID, "multiblock_" + definition.id().getPath(),
                MachineRecipe.class);
        this.background = new GuiBackground();
        this.inputSlot = guiHelper.createDrawable(MachineGuiTextures.SLOT_INPUT, 0, 0, 18, 18);
        this.outputSlot = guiHelper.createDrawable(MachineGuiTextures.SLOT_OUTPUT, 0, 0, 18, 18);
        ItemStack iconStack = MultiblockRegistration.variants().stream()
                .filter(variant -> variant.definition().id().equals(definition.id()))
                .findFirst()
                .map(variant -> new ItemStack(variant.item().get()))
                .orElse(ItemStack.EMPTY);
        this.icon = guiHelper.createDrawableItemStack(iconStack);
    }

    public MultiblockDefinition definition() {
        return this.definition;
    }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return this.recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("multiblock." + this.definition.id().getNamespace()
                + "." + this.definition.id().getPath());
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
        int itemInput = 0;
        int fluidInput = 0;
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.Item) {
                var slot = builder.addInputSlot(INPUT_X + 1, SLOT_Y + 1 + itemInput * ROW_STEP);
                MachineRecipeCategory.addIngredient(slot, ingredient);
                itemInput++;
            } else {
                // 焦炉暂时没有流体/化学品输入；有的话也塞进储罐。
                var slot = builder.addInputSlot(TANK_X + 1, TANK_Y + 1 + fluidInput * TANK_INNER_HEIGHT);
                MachineRecipeCategory.addIngredient(slot, ingredient);
                if (ingredient instanceof MachineIngredient.Fluid fluid) {
                    slot.setFluidRenderer(Math.max(1L, fluid.amount()), false, TANK_INNER_WIDTH, TANK_INNER_HEIGHT,
                            mezz.jei.api.gui.drawable.TilingDirection.UP_RIGHT);
                }
                fluidInput++;
            }
        }
        int itemOutput = 0;
        int fluidOutput = 0;
        for (MachineOutput.Product product : recipe.outputs()) {
            if (product instanceof MachineOutput.ItemProduct) {
                var slot = builder.addOutputSlot(OUTPUT_X + 1, SLOT_Y + 1 + itemOutput * ROW_STEP);
                MachineRecipeCategory.addProduct(slot, product);
                itemOutput++;
            } else {
                // 流体/化学品进储罐内部，而不是另开一个槽位。
                var slot = builder.addOutputSlot(TANK_X + 1, TANK_Y + 1 + fluidOutput * TANK_INNER_HEIGHT);
                MachineRecipeCategory.addProduct(slot, product);
                if (product instanceof MachineOutput.FluidProduct fluid) {
                    slot.setFluidRenderer(Math.max(1L, fluid.amount()), false, TANK_INNER_WIDTH, TANK_INNER_HEIGHT,
                            mezz.jei.api.gui.drawable.TilingDirection.UP_RIGHT);
                }
                fluidOutput++;
            }
        }
    }

    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        // 进度条（动态）叠在真实 GUI 的进度条位置。
        AnimatedProgressBar.INSTANCE.draw(graphics, PROGRESS_X, PROGRESS_Y);
        // 储罐刻度：画在流体内容之上，和游戏内 GuiGauge 的顺序一致。
        graphics.blit(MachineGuiTextures.GAUGE, TANK_X + 1, TANK_Y, 0, 0,
                TANK_INNER_WIDTH, TANK_HEIGHT, TANK_INNER_WIDTH, TANK_HEIGHT);
        // 耗时放标题区域。
        // 实际耗时：配方写了 time 就用配方，否则用多方块 JSON 的 time（已解析成 tick）。
        int time = this.definition.resolveTime(recipe);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.meknh_core.time",
                MachineRecipeCategory.seconds(time)), 6, 62, 0x404040, false);
    }

    /**
     * 复刻游戏内 GUI 底图：Mek 底板九宫格 + 储罐底板。
     */
    private static final class GuiBackground implements IDrawable {

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
            // 槽位底图直接画在背景上，只保留 1px 彩色边框（和 GUI 一致）。
            GuiDraw.nineSlice(graphics, MachineGuiTextures.SLOT_INPUT, x + INPUT_X, y + SLOT_Y,
                    18, 18, 1, 18, 18);
            GuiDraw.nineSlice(graphics, MachineGuiTextures.SLOT_OUTPUT, x + OUTPUT_X, y + SLOT_Y,
                    18, 18, 1, 18, 18);
            // 储罐底图：和游戏内一样把 output.png 纵向九宫格拉伸成 18×58。
            GuiDraw.nineSliceXY(graphics, MachineGuiTextures.SLOT_OUTPUT, x + TANK_X, y + TANK_Y,
                    TANK_WIDTH, TANK_HEIGHT, 1, 1, 18, 18, 0, 0, 18, 18);
        }
    }

    /**
     * 动态进度条：底图整条画出来，填充层按时间从左往右长。
     *
     * <p>纹理就是 Mek 冶金灌注机用的 {@code gui/progress/right.png}：32×24 三行，
     * 第 0 行空槽、第 1 行填充、第 2 行警告。</p>
     */
    private static final class AnimatedProgressBar implements IDrawable {

        private static final AnimatedProgressBar INSTANCE = new AnimatedProgressBar();
        private static final long CYCLE_MS = 1200L;
        private static final int WIDTH = 32;
        private static final int HEIGHT = 8;

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
            ResourceLocation texture = MachineGuiTextures.PROGRESS;
            int textureHeight = HEIGHT * 3;
            graphics.blit(texture, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, textureHeight);
            int filled = (int) ((System.currentTimeMillis() % CYCLE_MS) * WIDTH / CYCLE_MS);
            if (filled > 0) {
                graphics.blit(texture, x, y, 0, HEIGHT, filled, HEIGHT, WIDTH, textureHeight);
            }
        }
    }
}
