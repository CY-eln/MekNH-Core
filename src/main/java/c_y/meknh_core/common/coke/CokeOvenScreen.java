package c_y.meknh_core.common.coke;

import java.util.List;
import c_y.meknh_core.common.gui.element.ProgressElement;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 焦炉 GUI：输入槽 - 进度条 - 输出槽（水平居中，间距 8），右侧杂酚油罐。
 *
 * <p>槽位与储罐底图都用 Mek 的 {@code gui/slot/input.png} / {@code output.png}：
 * 输入槽 18×18，储罐把 {@code output.png} 纵向拉成 18×60（和提取机的 MEK 输出槽一致），
 * 再叠 {@code gauge/standard.png} 刻度。进度条用冶金灌注机的
 * {@code gui/progress/right.png}（32×8，三行：底图 / 填充 / 警告）。</p>
 */
public class CokeOvenScreen extends GuiMekanism<CokeOvenMenu> {

    private static final int TANK_X = 134;
    private static final int TANK_Y = 13;
    private static final int TANK_WIDTH = CokeOvenLayout.SLOT_SIZE;
    private static final int TANK_HEIGHT = 58;
    private static final int TANK_INNER_WIDTH = 16;
    private static final int TANK_INNER_HEIGHT = 56;

    /**
     * 槽位底图九宫格边框（Mek 的槽位贴图四周各 1px 是边框）。
     */
    private static final int SLOT_BORDER = 1;

    public CokeOvenScreen(CokeOvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 73;
    }

    @Override
    protected void addGuiElements() {
        // 槽位与储罐全部手绘，保持和 JEI 页同一套坐标与贴图。
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        // 槽位底图：0 = 输入（input.png），1 = 输出（output.png），其余是玩家物品栏（normal.png）。
        // 全部走九宫格绘制，漏画物品栏就会显得“背包没了”。
        for (int index = 0; index < this.menu.slots.size(); index++) {
            Slot slot = this.menu.slots.get(index);
            ResourceLocation texture = index == 0 ? MekGuiTextures.SLOT_INPUT
                    : index == 1 ? MekGuiTextures.SLOT_OUTPUT
                    : MekGuiTextures.SLOT_NORMAL;
            drawSlot(graphics, texture,
                    this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
        }
        renderTank(graphics);
        // 进度条：冶金灌注机 right.png，时长/进度来自 ContainerData。
        ProgressElement.render(graphics, MachineGuiTextures.PROGRESS, this.menu.progressRatio(),
                this.leftPos + CokeOvenLayout.PROGRESS_X, this.topPos + CokeOvenLayout.PROGRESS_Y,
                CokeOvenLayout.PROGRESS_WIDTH, CokeOvenLayout.PROGRESS_HEIGHT);
    }

    /**
     * 储罐：output.png 纵向九宫格拉伸成 18×60，内部填杂酚油，最后叠刻度。
     */
    private void renderTank(GuiGraphics graphics) {
        int x = this.leftPos + TANK_X;
        int y = this.topPos + TANK_Y;
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.SLOT_OUTPUT, x, y,
                TANK_WIDTH, TANK_HEIGHT, 1, 1, 18, 18, 0, 0, 18, 18);
        FluidStack fluid = this.menu.fluid();
        long amount = this.menu.amount();
        long capacity = this.menu.capacity();
        if (fluid != null && amount > 0L && capacity > 0L) {
            int fill = Math.max(1, (int) Math.round(TANK_INNER_HEIGHT
                    * Math.min(1.0D, (double) amount / (double) capacity)));
            TextureAtlasSprite sprite = MekanismRenderer.getFluidTexture(fluid,
                    MekanismRenderer.FluidTextureType.STILL);
            if (sprite != null) {
                MekanismRenderer.color(graphics, fluid);
                // drawTiledSprite 的 y 是“底部”，从罐底往上铺。
                GuiUtils.drawTiledSprite(graphics, x + 1, y + 1 + TANK_INNER_HEIGHT,
                        0, TANK_INNER_WIDTH, fill, sprite, 16, 16, 0, TilingDirection.DOWN_RIGHT);
                MekanismRenderer.resetColor(graphics);
            }
        }
        graphics.blit(MachineGuiTextures.GAUGE, x + 1, y, 0, 0,
                TANK_INNER_WIDTH, TANK_HEIGHT, TANK_INNER_WIDTH, TANK_HEIGHT);
    }

    private static void drawSlot(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
        GuiDraw.nineSlice(graphics, texture, x, y, CokeOvenLayout.SLOT_SIZE, CokeOvenLayout.SLOT_SIZE,
                SLOT_BORDER, CokeOvenLayout.SLOT_SIZE, CokeOvenLayout.SLOT_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.isOverTank(mouseX, mouseY)) {
            FluidStack fluid = this.menu.fluid();
            if (fluid == null || fluid.isEmpty()) {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.meknh_core.hatch.empty"), mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font,
                        Component.literal(fluid.getHoverName().getString() + "："
                                + this.menu.amount() + " / " + this.menu.capacity() + " mB"), mouseX, mouseY);
            }
        } else if (this.isOverProgress(mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    Component.translatableWithFallback("gui.meknh_core.show_recipe", "显示配方"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isOverTank(mouseX, mouseY)
                && c_y.meknh_core.common.gui.element.DropperInteraction.click(
                        this.menu.blockPos(), c_y.meknh_core.common.network.DropperUsePayload.TANK_FLUID, 0,
                        button, net.minecraft.client.gui.screens.Screen.hasShiftDown())) {
            return true;
        }
        if (button == 0 && this.isOverProgress(mouseX, mouseY)
                && c_y.meknh_core.client.jei.MeknhJeiPlugin.openMultiblockRecipes(
                        c_y.meknh_core.MekNHCore.rl("coke_oven"))) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForegroundText(GuiGraphics graphics, int mouseX, int mouseY) {
        this.renderTitleText(graphics);
        this.renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }

    private boolean isOverProgress(double mouseX, double mouseY) {
        int x = this.leftPos + CokeOvenLayout.PROGRESS_X;
        int y = this.topPos + CokeOvenLayout.PROGRESS_Y;
        return mouseX >= x && mouseX < x + CokeOvenLayout.PROGRESS_WIDTH
                && mouseY >= y && mouseY < y + CokeOvenLayout.PROGRESS_HEIGHT;
    }

    private boolean isOverTank(double mouseX, double mouseY) {
        int x = this.leftPos + TANK_X;
        int y = this.topPos + TANK_Y;
        return mouseX >= x && mouseX < x + TANK_WIDTH && mouseY >= y && mouseY < y + TANK_HEIGHT;
    }

    /**
     * JEI：把罐体区域里的杂酚油变成可点击成分（R/U 查用途/配方）。
     */
    public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> jeiTankIngredient(
            mezz.jei.api.gui.builder.IClickableIngredientFactory factory, double mouseX, double mouseY) {
        int x = this.leftPos + TANK_X;
        int y = this.topPos + TANK_Y;
        if (!this.isOverTank(mouseX, mouseY)) {
            return java.util.Optional.empty();
        }
        FluidStack fluid = this.menu.fluid();
        if (fluid == null) {
            return java.util.Optional.empty();
        }
        return factory.createBuilder(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, fluid)
                .buildWithArea(x, y, TANK_WIDTH, TANK_HEIGHT);
    }
}
