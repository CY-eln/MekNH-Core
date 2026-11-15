package c_y.meknh_core.common.gui.screen;

import c_y.meknh_core.common.gui.element.TabStrip;
import c_y.meknh_core.common.gui.menu.HatchMenu;
import c_y.meknh_core.common.util.EnergyText;
import java.util.List;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 仓室 GUI，直接复用 Mekanism 的 {@link GuiMekanism} 基类、{@link GuiSlot} 与 {@link GuiGauge}。
 *
 * <p>宽罐用 Mek 自己的 {@link GuiGauge}（{@link GaugeType#WIDE} = {@code GaugeInfo.STANDARD}
 * + {@code GaugeOverlay.WIDE}），填充、刻度、悬浮提示全部走源码链，不再手写 tiled 绘制。</p>
 */
public class HatchScreen extends GuiMekanism<HatchMenu> {

    private static final int GAUGE_Y = 18;
    /** Mek 源码：流体储罐 48,18；能量立方 55,18。 */
    private static final int GAUGE_X_FLUID = 48;
    private static final int GAUGE_X_ENERGY = 55;

    public HatchScreen(HatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = menu.imageHeight();
        this.inventoryLabelY = menu.inventoryY() - 11;
    }

    @Override
    protected void addGuiElements() {
        boolean energy = this.menu.partType() == c_y.meknh_core.api.multiblock.MultiblockPartType.ENERGY_INPUT
                || this.menu.partType() == c_y.meknh_core.api.multiblock.MultiblockPartType.ENERGY_OUTPUT;
        for (int index = 0; index < this.menu.slots.size(); index++) {
            Slot slot = this.menu.slots.get(index);
            GuiSlot guiSlot = new GuiSlot(SlotType.NORMAL, this, slot.x - 1, slot.y - 1);
            if (!this.menu.isBus()) {
                if (energy && index == 0) {
                    guiSlot.with(SlotOverlay.MINUS);
                } else if (energy && index == 1) {
                    guiSlot.with(SlotOverlay.PLUS);
                } else if (!energy && index == 0) {
                    guiSlot.with(SlotOverlay.INPUT);
                } else if (!energy && index == 1) {
                    guiSlot.with(SlotOverlay.OUTPUT);
                }
            }
            this.addRenderableWidget(guiSlot);
        }
        if (!this.menu.isBus()) {
            this.addRenderableWidget(new HatchGauge(this,
                    energy ? GAUGE_X_ENERGY : GAUGE_X_FLUID, GAUGE_Y, this.menu));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderAutoIoTab(graphics, mouseX, mouseY);
    }

    /**
     * 自动搬运 Tab：输出仓是「自动弹出」，输入仓是「自动吸入」。
     */
    private void renderAutoIoTab(GuiGraphics graphics, int mouseX, int mouseY) {
        TabStrip.renderAutoIo(graphics, this.leftPos, this.topPos, this.menu.autoIo());
        if (TabStrip.isOverAutoIo(mouseX, mouseY, this.leftPos, this.topPos)) {
            graphics.renderTooltip(this.font, Component.translatable(this.autoIoTooltipKey()), mouseX, mouseY);
        }
    }

    private String autoIoTooltipKey() {
        String action = this.menu.isOutputHatch() ? "auto_output" : "auto_input";
        return "gui.meknh_core.hatch." + action + (this.menu.autoIo() ? ".on" : ".off");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && TabStrip.isOverAutoIo(mouseX, mouseY, this.leftPos, this.topPos)) {
            // 只发目标状态，服务端写回后由 ContainerData 同步，客户端不本地翻转。
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new c_y.meknh_core.common.network.HatchAutoIoPayload(
                            this.menu.blockPos(), !this.menu.autoIo()));
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

    /**
     * 64×48 宽罐：直接继承 Mek 的 {@link GuiGauge}，只提供液面高度、图标、染色与提示。
     */
    private static final class HatchGauge extends GuiGauge<Void> {

        private final HatchMenu menu;

        private HatchGauge(IGuiWrapper gui, int x, int y, HatchMenu menu) {
            super(GaugeType.WIDE, gui, x, y);
            this.menu = menu;
        }

        @Override
        public int getScaledLevel() {
            long stored = this.menu.stored();
            long capacity = this.menu.capacity();
            if (stored <= 0L || capacity <= 0L) {
                return 0;
            }
            double ratio = Math.min(1.0D, (double) stored / (double) capacity);
            return Math.max(1, (int) ((this.getHeight() - 2) * ratio));
        }

        @Nullable
        @Override
        public TextureAtlasSprite getIcon() {
            return switch (this.menu.data(HatchMenu.DATA_KIND)) {
                case HatchMenu.KIND_ENERGY -> MekanismRenderer.energyIcon;
                case HatchMenu.KIND_FLUID -> {
                    FluidStack fluid = this.menu.fluid();
                    yield fluid == null ? null
                            : MekanismRenderer.getFluidTexture(fluid, MekanismRenderer.FluidTextureType.STILL);
                }
                case HatchMenu.KIND_CHEMICAL -> {
                    ChemicalStack chemical = this.menu.chemical();
                    yield chemical == null ? null : MekanismRenderer.getChemicalTexture(chemical);
                }
                default -> null;
            };
        }

        @Override
        protected void applyRenderColor(GuiGraphics graphics) {
            int kind = this.menu.data(HatchMenu.DATA_KIND);
            if (kind == HatchMenu.KIND_FLUID) {
                FluidStack fluid = this.menu.fluid();
                if (fluid != null) {
                    MekanismRenderer.color(graphics, fluid);
                }
            } else if (kind == HatchMenu.KIND_CHEMICAL) {
                ChemicalStack chemical = this.menu.chemical();
                if (chemical != null) {
                    int tint = chemical.getChemicalTint();
                    graphics.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                            (tint & 0xFF) / 255.0F, 1.0F);
                }
            }
        }

        @Override
        public Component getLabel() {
            return null;
        }

        @Override
        public List<Component> getTooltipText() {
            return switch (this.menu.data(HatchMenu.DATA_KIND)) {
                case HatchMenu.KIND_ENERGY -> List.of(Component.translatable("gui.meknh_core.hatch.energy",
                        EnergyText.shortFe(this.menu.stored()), EnergyText.shortFe(this.menu.capacity())));
                case HatchMenu.KIND_FLUID -> {
                    FluidStack fluid = this.menu.fluid();
                    if (fluid == null || fluid.isEmpty()) {
                        yield List.of(Component.translatable("gui.meknh_core.hatch.empty"));
                    }
                    yield List.of(Component.literal(fluid.getHoverName().getString() + "："
                            + this.menu.stored() + " / " + this.menu.capacity() + " mB"));
                }
                case HatchMenu.KIND_CHEMICAL -> {
                    ChemicalStack chemical = this.menu.chemical();
                    if (chemical == null || chemical.isEmpty()) {
                        yield List.of(Component.translatable("gui.meknh_core.hatch.empty"));
                    }
                    yield List.of(Component.literal(chemical.getChemical().getTextComponent().getString() + "："
                            + this.menu.stored() + " / " + this.menu.capacity() + " mB"));
                }
                default -> List.of(Component.translatable("gui.meknh_core.hatch.empty"));
            };
        }

        @Nullable
        @Override
        public TransmissionType getTransmission() {
            return null;
        }
    }
}
