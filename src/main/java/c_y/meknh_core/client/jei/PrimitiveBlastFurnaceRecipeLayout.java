package c_y.meknh_core.client.jei;

import c_y.meknh_core.common.blast.PrimitiveBlastFurnaceLayout;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import c_y.meknh_core.common.recipe.MachineIngredient;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 土高炉的 JEI 布局：上方 3 个 input 槽，下方 3 个 output 槽，没有储罐。
 */
final class PrimitiveBlastFurnaceRecipeLayout implements MultiblockRecipeLayout {

    private static final int WIDTH = MultiblockRecipeCategory.WIDTH;
    private static final int HEIGHT = MultiblockRecipeCategory.HEIGHT;

    private final IDrawable background = new Background();

    @Override
    public IDrawable background() {
        return this.background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe) {
        int input = 0;
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.Item && input < 3) {
                MachineRecipeCategory.addIngredient(builder.addInputSlot(
                        PrimitiveBlastFurnaceLayout.INPUT_X + input++ * PrimitiveBlastFurnaceLayout.SLOT_STEP,
                        PrimitiveBlastFurnaceLayout.INPUT_Y), ingredient);
            }
        }
        int output = 0;
        for (MachineOutput.Product product : recipe.outputs()) {
            if (product instanceof MachineOutput.ItemProduct && output < 3) {
                MachineRecipeCategory.addProduct(builder.addOutputSlot(
                        PrimitiveBlastFurnaceLayout.OUTPUT_X + output++ * PrimitiveBlastFurnaceLayout.SLOT_STEP,
                        PrimitiveBlastFurnaceLayout.OUTPUT_Y), product);
            }
        }
    }

    @Override
    public void draw(GuiGraphics graphics, MachineRecipe recipe, int time) {
        MultiblockRecipeCategory.drawDownProgress(graphics,
                PrimitiveBlastFurnaceLayout.PROGRESS_X, PrimitiveBlastFurnaceLayout.PROGRESS_Y);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.meknh_core.time", MachineRecipeCategory.seconds(time)),
                6, 6, 0x404040, false);
    }

    private static final class Background implements IDrawable {
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
            for (int i = 0; i < 3; i++) {
                GuiDraw.nineSlice(graphics, MachineGuiTextures.SLOT_INPUT,
                        x + PrimitiveBlastFurnaceLayout.INPUT_X + i * PrimitiveBlastFurnaceLayout.SLOT_STEP - 1,
                        y + PrimitiveBlastFurnaceLayout.INPUT_Y - 1,
                        18, 18, 1, 18, 18);
                GuiDraw.nineSlice(graphics, MachineGuiTextures.SLOT_OUTPUT,
                        x + PrimitiveBlastFurnaceLayout.OUTPUT_X + i * PrimitiveBlastFurnaceLayout.SLOT_STEP - 1,
                        y + PrimitiveBlastFurnaceLayout.OUTPUT_Y - 1,
                        18, 18, 1, 18, 18);
            }
        }
    }
}
