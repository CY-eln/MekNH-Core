package c_y.meknh_core.common.blast;

import c_y.meknh_core.common.gui.element.ProgressElement;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Mek base panel, NORMAL GuiSlot widgets, then native-size Mek RIGHT progress. */
public class PrimitiveBlastFurnaceScreen extends GuiMekanism<PrimitiveBlastFurnaceMenu> {
    public PrimitiveBlastFurnaceScreen(PrimitiveBlastFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PrimitiveBlastFurnaceLayout.PANEL_WIDTH;
        imageHeight = PrimitiveBlastFurnaceLayout.PANEL_HEIGHT;
        inventoryLabelY = 73;
    }
    @Override protected void addGuiElements() {
        for (var slot : menu.slots) addRenderableWidget(new GuiSlot(SlotType.NORMAL, this, slot.x - 1, slot.y - 1));
    }
    @Override protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        ProgressElement.render(graphics, MachineGuiTextures.PROGRESS, (float) menu.progressRatio(),
                leftPos + PrimitiveBlastFurnaceLayout.PROGRESS_X, topPos + PrimitiveBlastFurnaceLayout.PROGRESS_Y,
                PrimitiveBlastFurnaceLayout.PROGRESS_WIDTH, PrimitiveBlastFurnaceLayout.PROGRESS_HEIGHT);
    }
}
