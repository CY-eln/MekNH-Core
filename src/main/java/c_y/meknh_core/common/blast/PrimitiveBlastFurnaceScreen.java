package c_y.meknh_core.common.blast;

import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import c_y.meknh_core.common.gui.render.MetalMachineBackground;
import c_y.meknh_core.common.gui.render.MachineBackgroundMaterial;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 土高炉 GUI：上方 3 个 input 槽，向下短箭头进度，下方 3 个 output 槽。
 */
public class PrimitiveBlastFurnaceScreen extends GuiMekanism<PrimitiveBlastFurnaceMenu> {

    private static final int SLOT_BORDER = 1;
    private static final int SLOT_SIZE = 18;

    public PrimitiveBlastFurnaceScreen(PrimitiveBlastFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PrimitiveBlastFurnaceLayout.PANEL_WIDTH;
        this.imageHeight = PrimitiveBlastFurnaceLayout.PANEL_HEIGHT;
        this.inventoryLabelY = 73;
    }

    @Override
    protected void addGuiElements() {
        IProgressInfoHandler.IBooleanProgressInfoHandler handler =
                new IProgressInfoHandler.IBooleanProgressInfoHandler() {
                    @Override
                    public boolean fillProgressBar() {
                        return PrimitiveBlastFurnaceScreen.this.menu.progress() > 0;
                    }

                    @Override
                    public double getProgress() {
                        return PrimitiveBlastFurnaceScreen.this.menu.progressRatio();
                    }
                };
        this.addRenderableWidget(new GuiProgress(handler, ProgressType.RIGHT, this,
                PrimitiveBlastFurnaceLayout.PROGRESS_X, PrimitiveBlastFurnaceLayout.PROGRESS_Y));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MetalMachineBackground.renderBase(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                MachineBackgroundMaterial.PRIMITIVE_BRICK);
        for (int index = 0; index < this.menu.slots.size(); index++) {
            Slot slot = this.menu.slots.get(index);
            ResourceLocation texture = index < 3 ? MekGuiTextures.SLOT_INPUT
                    : index < 6 ? MekGuiTextures.SLOT_OUTPUT
                    : MekGuiTextures.SLOT_NORMAL;
            GuiDraw.nineSlice(graphics, texture, this.leftPos + slot.x - 1, this.topPos + slot.y - 1,
                    SLOT_SIZE, SLOT_SIZE, SLOT_BORDER, SLOT_SIZE, SLOT_SIZE);
        }
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        this.renderTitleText(graphics);
        this.renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
