package c_y.meknh_core.client.jei;

import c_y.meknh_core.common.recipe.MachineRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 多方块 JEI 页面的可选布局扩展；未注册时使用默认的焦炉式布局。
 */
public interface MultiblockRecipeLayout {

    IDrawable background();

    void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe);

    void draw(GuiGraphics graphics, MachineRecipe recipe, int time);
}
